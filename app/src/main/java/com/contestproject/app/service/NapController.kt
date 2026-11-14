package com.contestproject.app.service

import android.util.Log
import com.contestproject.app.data.local.NapStore
import com.contestproject.app.data.vehicle.ClimateCapabilities
import com.contestproject.app.data.vehicle.ClimateSnapshot
import com.contestproject.app.data.vehicle.GearState
import com.contestproject.app.data.vehicle.Powertrain
import com.contestproject.app.data.vehicle.VehicleConnection
import com.contestproject.app.data.vehicle.VehicleEvent
import com.contestproject.app.data.vehicle.VehicleGateway
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.data.voice.VoiceLines
import com.contestproject.app.data.voice.WakeVoice
import com.contestproject.app.domain.AlertReason
import com.contestproject.app.domain.ClimateCheck
import com.contestproject.app.domain.ClimateItem
import com.contestproject.app.domain.ClimatePlan
import com.contestproject.app.domain.ClimatePlanner
import com.contestproject.app.domain.FreshAirMode
import com.contestproject.app.domain.NapEvent
import com.contestproject.app.domain.NapMonitor
import com.contestproject.app.domain.NapNotice
import com.contestproject.app.domain.NapPhase
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapRecord
import com.contestproject.app.domain.NapSession
import com.contestproject.app.domain.NapState
import com.contestproject.app.domain.RestClimate
import com.contestproject.app.domain.StepStatus
import com.contestproject.app.domain.WakeStep
import com.contestproject.app.domain.WakeStepKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 쪽잠 한 번의 전체 흐름(시작 → 외기 유지 → 기상 연출 → 깨우기 → 출발 확인 → 원래대로)을 진행한다.
 * 앱 프로세스 단위로 하나만 존재하고, 화면이 없어도(다른 앱 사용 중) 계속 동작한다.
 *
 * 쪽잠 중에는 주기적으로 차의 실제 공조 값을 다시 읽어(점검) 앱이 정한 값과 다르면 다시 맞추고 기록한다.
 * 화면의 숫자는 이 점검 결과(차에서 읽은 값)를 그대로 보여 준다.
 */
class NapController(
    private val vehicle: VehicleGateway,
    private val voice: WakeVoice,
    private val store: NapStore,
    private val lines: VoiceLines,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<NapState>(NapState.Idle)
    val state: StateFlow<NapState> = _state.asStateFlow()

    private val _capabilities = MutableStateFlow<ClimateCapabilities?>(null)
    val capabilities: StateFlow<ClimateCapabilities?> = _capabilities.asStateFlow()

    private val _monitor = MutableStateFlow(NapMonitor())
    /** 지금 차 상태(마지막 점검 결과)와 이번 쪽잠 기록 */
    val monitor: StateFlow<NapMonitor> = _monitor.asStateFlow()

    private val _notices = MutableSharedFlow<NapNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<NapNotice> = _notices.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    /** 차량 제어 중(버튼 반응 2초 기준 때문에 화면에 진행 표시를 한다) */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val powertrainOverride = MutableStateFlow(
        store.powertrainOverride?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }
    )

    /**
     * 화면에 보여 줄 차량 상태. 시연 모드에서 동력원을 가정하면 그 값으로 바꿔 보여 준다
     * (Pleos 에뮬레이터는 전기차로 고정돼 있어 내연기관·하이브리드 안내를 확인할 방법이 이것뿐이다).
     * 실제 차에서는 차가 알려 주는 연료 종류를 그대로 쓴다.
     */
    val vehicleStatus: StateFlow<VehicleStatus> = combine(vehicle.status, powertrainOverride) { status, override ->
        if (override == null) status else status.copy(powertrain = override, modelName = DEMO_MODEL_NAMES[override])
    }.stateIn(scope, SharingStarted.Eagerly, vehicle.status.value)

    var demoMode: Boolean
        get() = store.demoMode
        set(value) {
            store.demoMode = value
            if (!value) setPowertrainOverride(null)
        }

    /** 시연 모드: 실제 → 내연기관 → 하이브리드 → 실제 순서로 바꾼다. */
    fun cyclePowertrainOverride() {
        if (!store.demoMode) return
        setPowertrainOverride(
            when (powertrainOverride.value) {
                null -> Powertrain.COMBUSTION
                Powertrain.COMBUSTION -> Powertrain.HYBRID
                else -> null
            }
        )
    }

    private fun setPowertrainOverride(value: Powertrain?) {
        powertrainOverride.value = value
        store.powertrainOverride = value?.name
    }

    val lastDurationMin: Int get() = store.lastDurationMin

    private var snapshot = ClimateSnapshot()
    private var plan: ClimatePlan = ClimatePlanner.planFor(ClimatePlanner.seasonOf(null))
    private var tickJob: Job? = null
    private var checkJob: Job? = null
    private var freshAirMode = FreshAirMode.NONE
    // 앱이 지키려는 공조 값. null 항목은 신경 쓰지 않는다. 점검 때 차에서 읽은 값과 비교한다.
    private var target = ClimateSnapshot()
    // 앱이 창문을 열었는지. 시작 전 창문 위치를 못 읽었더라도 끝날 때 반드시 닫기 위해 기억한다.
    private var windowOpenedByApp = false
    private var windowGapPosition: Int? = null
    private var lastVoiceAtMs = 0L
    private var started = false
    // 김서림 제거 중: 에어컨은 차에 맡기고 풍량은 '이 단계 이상'만 지킨다(차가 김을 빼려고 스스로 올리는 걸 막지 않게).
    private var defrosting = false
    // 점검 때 공조가 꺼져 있던 횟수(연속). 다시 켜도 계속 꺼지면 시동이 꺼진 것으로 보고 깨운다.
    private var powerOffChecks = 0

    fun start() {
        if (started) return
        started = true
        // 음성 엔진은 무거워서(네이티브 라이브러리) 앱 시작 때 올리지 않고, 쪽잠을 시작할 때 준비한다.
        scope.launch {
            vehicle.connect()
            if (vehicle.status.value.connection is VehicleConnection.Connected) {
                _capabilities.value = vehicle.capabilities()
            }
            resumeIfNeeded()
        }
        scope.launch { vehicle.events.collect(::onVehicleEvent) }
        scope.launch {
            vehicle.status.collect { status ->
                val active = _state.value as? NapState.Active ?: return@collect
                if (status.gear == GearState.NOT_PARK && active !is NapState.DepartCheck) {
                    endByGear()
                }
            }
        }
    }

    // region 사용자 동작

    fun startNap(minutes: Int) {
        if (_state.value != NapState.Idle || _busy.value) return
        if (!NapPolicy.isValidDuration(minutes)) return
        val status = vehicle.status.value
        if (status.connection !is VehicleConnection.Connected || status.gear == GearState.NOT_PARK) return
        store.lastDurationMin = minutes
        voice.prepare()

        scope.launch {
            _busy.value = true
            try {
                val caps = _capabilities.value ?: vehicle.capabilities().also { _capabilities.value = it }
                snapshot = vehicle.readClimate()
                plan = ClimatePlanner.planFor(ClimatePlanner.seasonOf(status.outsideTempC))
                val minuteMs = if (store.demoMode) NapPolicy.DEMO_MINUTE_MS else NapPolicy.MINUTE_MS
                val session = NapSession.start(clock(), minutes, minuteMs)
                store.save(session, snapshot)
                _monitor.value = NapMonitor(original = snapshot)
                updateRecord { NapRecord().with(NapEvent(session.startedAtMs, NapEvent.Kind.STARTED)) }

                val climate = applyRestClimate(caps)
                if (climate.freshAir == FreshAirMode.NONE) {
                    // 외기를 지킬 방법이 없으면 시작하지 않는다(기획서 안전 설계).
                    restoreClimate()
                    store.clearSession()
                    _monitor.value = NapMonitor()
                    _notices.tryEmit(NapNotice.START_FAILED)
                    return@launch
                }
                _state.value = NapState.Resting(session, climate)
                voice.announce(lines.start(minutes))
                startTicking()
            } finally {
                _busy.value = false
            }
        }
    }

    /** 쉬는 중·기상 연출 중 [지금 깨기] */
    fun wakeNow() {
        val active = _state.value as? NapState.Active ?: return
        if (active !is NapState.Resting && active !is NapState.PreWake) return
        scope.launch {
            _busy.value = true
            try {
                if (active is NapState.Resting) applyWakeClimate(_capabilities.value ?: ClimateCapabilities())
                goToDepartCheck(active.session, allowExtend = true)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 깨우기 화면 [일어났어요] */
    fun acknowledgeWake() {
        val active = _state.value as? NapState.Active ?: return
        when (active) {
            is NapState.Waking -> goToDepartCheck(active.session, allowExtend = true)
            is NapState.Alert -> goToDepartCheck(
                active.session,
                // 차량 경고 뒤에는 더 자지 않게 연장을 막는다.
                allowExtend = active.reason == AlertReason.NO_RESPONSE,
            )
            else -> Unit
        }
    }

    fun snoozeAlert() {
        val alert = _state.value as? NapState.Alert ?: return
        voice.stop()
        _state.value = alert.copy(snoozedUntilMs = clock() + NapPolicy.ALERT_SNOOZE_SEC * 1000L)
    }

    /** 출발 전 확인 [n분 더]. 앞서 쉰 시간과 합쳐 최대 시간을 넘는 길이는 받지 않는다. */
    fun extend(minutes: Int) {
        val check = _state.value as? NapState.DepartCheck ?: return
        if (minutes < NapPolicy.MIN_EXTENSION_MIN || minutes > check.maxExtensionMin || _busy.value) return
        scope.launch {
            _busy.value = true
            try {
                val now = clock()
                val session = check.session.extend(now, minutes, awakeAtMs = check.awakeAtMs)
                store.updateSession(session)
                updateRecord { it.with(NapEvent(now, NapEvent.Kind.EXTENDED)).copy(defrostEndedAtMs = it.defrostEndedAtMs ?: now) }
                val caps = _capabilities.value ?: ClimateCapabilities()
                if (caps.defroster) vehicle.setDefroster(false)
                if (caps.rearDefroster) vehicle.setRearDefroster(false)
                if (wakeSteeringLevel(caps) != null) vehicle.setSteeringHeat(0)
                val climate = applyRestClimate(caps)
                if (caps.defroster) target = target.copy(defroster = false)
                if (caps.rearDefroster) target = target.copy(rearDefroster = false)
                _state.value = NapState.Resting(session, climate)
                voice.announce(lines.extend(minutes))
                startTicking()
            } finally {
                _busy.value = false
            }
        }
    }

    fun depart() {
        if (_state.value !is NapState.Active) return
        scope.launch {
            _busy.value = true
            try {
                finish()
                _notices.tryEmit(NapNotice.DEPARTED)
            } finally {
                _busy.value = false
            }
        }
    }

    // endregion

    private suspend fun resumeIfNeeded() {
        val (session, saved) = store.load() ?: return
        snapshot = saved
        if (session.isStaleAt(clock())) {
            // 깨울 시각이 한참 지나서 차가 켜졌다 = 사람이 이미 깨서 차를 켰다(꺼진 동안엔 깨울 수도 없었다).
            // 이어서 깨우지 않고, 앱이 바꾼 공조·창문만 되돌리고 정리한다.
            restoreClimate()
            store.clearSession()
            _notices.tryEmit(NapNotice.ENDED_WHILE_OFF)
            return
        }
        voice.prepare()
        _monitor.value = NapMonitor(original = saved, record = store.loadRecord() ?: NapRecord())
        plan = ClimatePlanner.planFor(ClimatePlanner.seasonOf(vehicle.status.value.outsideTempC))
        val caps = _capabilities.value ?: ClimateCapabilities()
        // 전원이 다시 켜지면 공조가 초기화됐을 수 있어 현재 단계에 맞게 다시 적용한다.
        when (session.phaseAt(clock())) {
            NapPhase.RESTING -> {
                val climate = applyRestClimate(caps)
                _state.value = NapState.Resting(session, climate)
            }
            NapPhase.PRE_WAKE -> {
                val climate = applyRestClimate(caps)
                _state.value = NapState.PreWake(session, climate, initialWakeSteps(caps))
                applyWakeClimate(caps)
            }
            NapPhase.WAKE -> {
                applyRestClimate(caps)
                applyWakeClimate(caps)
                enterWaking(session)
            }
        }
        _notices.tryEmit(NapNotice.RESUMED_AFTER_RESTART)
        startTicking()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                onTick()
                delay(TICK_MS)
            }
        }
        startChecking()
    }

    /** 점검: 바로 한 번 읽고, 그 뒤로 일정 간격마다 다시 읽는다. 화면이 꺼져 있어도 돈다. */
    private fun startChecking() {
        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                checkOnce()
                delay(checkIntervalMs())
            }
        }
    }

    private fun checkIntervalMs(): Long =
        if ((_state.value as? NapState.Active)?.session?.isDemo == true) DEMO_CHECK_MS else CHECK_MS

    /**
     * 차에서 공조 값을 다시 읽어 화면에 보여 주고, 앱이 정한 값과 다르면 다시 맞춘다.
     * 출발 확인 화면에서는 읽기만 한다(사람이 깨어 있고, 곧 원래대로 되돌린다).
     */
    internal suspend fun checkOnce() {
        val active = _state.value as? NapState.Active ?: return
        val wanted = target
        val reading = vehicle.readClimate()
        val cabin = vehicle.readCabinTemperature()
        val now = clock()
        if (_state.value !is NapState.Active) return

        val airOk = ClimateCheck.freshAirOk(freshAirMode, reading)
        _monitor.update { it.copy(reading = reading, cabinTempC = cabin, readAtMs = now) }
        if (airOk != null) {
            updateRecord { it.copy(checks = it.checks + 1, freshAirOkChecks = it.freshAirOkChecks + if (airOk) 1 else 0) }
        }

        // 읽는 사이에 단계가 바뀌어 목표가 달라졌으면, 옛 목표로 되돌리지 않는다.
        if (active is NapState.DepartCheck || wanted !== target) return
        val differing = ClimateCheck.mismatches(wanted, reading, fanAtLeast = defrosting)
        powerOffChecks = if (ClimateItem.POWER in differing) powerOffChecks + 1 else 0
        for (item in differing) {
            Log.i(TAG, "check: $item differs (want=$wanted got=$reading)")
            when (item) {
                ClimateItem.AIR -> revertToOutsideAir(_state.value as? NapState.Active ?: return)
                else -> if (reapply(item, wanted)) {
                    updateRecord { it.with(NapEvent(now, NapEvent.Kind.CORRECTED, item)) }
                }
            }
        }
        // 다시 켰는데도 다음 점검에서 또 꺼져 있으면 차가 공조를 돌릴 수 없는 상태다
        // (내연기관 시동 꺼짐 등. 엔진 상태는 공개 API로 읽을 수 없어 공조 전원으로 판단한다). 바람이 멈췄으니 깨운다.
        if (powerOffChecks >= POWER_OFF_ALERT_CHECKS) {
            (_state.value as? NapState.Active)?.let { raiseAlert(it.session, AlertReason.CLIMATE_OFF) }
        }
    }

    private suspend fun reapply(item: ClimateItem, wanted: ClimateSnapshot): Boolean = when (item) {
        ClimateItem.POWER -> wanted.hvacPower?.let { vehicle.setHvacPower(it) } ?: false
        ClimateItem.AC -> wanted.ac?.let { vehicle.setAc(it) } ?: false
        ClimateItem.TEMPERATURE -> wanted.temperatureC?.let { t ->
            vehicle.setTemperature(t).also { ok -> if (ok) wanted.passengerTemperatureC?.let { vehicle.setPassengerTemperature(it) } }
        } ?: false
        ClimateItem.FAN -> wanted.fanSpeed?.let { vehicle.setFanSpeed(it) } ?: false
        ClimateItem.DEFROST -> wanted.defroster?.let { vehicle.setDefroster(it) } ?: false
        ClimateItem.REAR_DEFROST -> wanted.rearDefroster?.let { vehicle.setRearDefroster(it) } ?: false
        ClimateItem.SEAT_HEAT -> wanted.seatHeat?.let { vehicle.setSeatHeat(it) } ?: false
        ClimateItem.STEERING_HEAT -> wanted.steeringHeat?.let { vehicle.setSteeringHeat(it) } ?: false
        ClimateItem.WINDOW -> wanted.driverWindow?.let { vehicle.setDriverWindow(it) } ?: false
        ClimateItem.AIR -> false
    }

    private suspend fun onTick() {
        val now = clock()
        when (val current = _state.value) {
            is NapState.Resting -> when (current.session.phaseAt(now)) {
                NapPhase.RESTING -> Unit
                NapPhase.PRE_WAKE -> {
                    val caps = _capabilities.value ?: ClimateCapabilities()
                    _state.value = NapState.PreWake(current.session, current.climate, initialWakeSteps(caps))
                    updateRecord { it.with(NapEvent(now, NapEvent.Kind.PRE_WAKE)) }
                    applyWakeClimate(caps)
                }
                NapPhase.WAKE -> enterWaking(current.session)
            }
            is NapState.PreWake -> if (current.session.phaseAt(now) == NapPhase.WAKE) enterWaking(current.session)
            is NapState.Waking -> if (now - lastVoiceAtMs >= voiceRepeatMs(current.session)) {
                val repeats = current.repeats + 1
                if (repeats >= NapPolicy.VOICE_REPEATS_BEFORE_ALERT) {
                    raiseAlert(current.session, AlertReason.NO_RESPONSE)
                } else {
                    speakWake(current.session, repeats)
                    _state.value = current.copy(repeats = repeats, lastCallAtMs = lastVoiceAtMs)
                }
            }
            is NapState.Alert -> {
                val snoozed = current.snoozedUntilMs?.let { now < it } == true
                if (!snoozed && now - lastVoiceAtMs >= NapPolicy.VOICE_REPEAT_SEC * 1000L) {
                    lastVoiceAtMs = now
                    voice.announce(alertLine(current.reason), level = NapPolicy.VOICE_REPEATS_BEFORE_ALERT)
                }
            }
            is NapState.DepartCheck, NapState.Idle -> Unit
        }
    }

    private fun voiceRepeatMs(session: NapSession): Long =
        if (session.isDemo) DEMO_VOICE_REPEAT_MS else NapPolicy.VOICE_REPEAT_SEC * 1000L

    private fun enterWaking(session: NapSession) {
        speakWake(session, 0)
        _state.value = NapState.Waking(
            session = session,
            repeats = 0,
            freshAir = freshAirMode,
            lastCallAtMs = lastVoiceAtMs,
            callIntervalMs = voiceRepeatMs(session),
        )
    }

    private fun speakWake(session: NapSession, repeats: Int) {
        lastVoiceAtMs = clock()
        updateRecord { it.with(NapEvent(lastVoiceAtMs, NapEvent.Kind.WAKE_CALL)) }
        val line = if (repeats == 0) {
            lines.wake(session.totalPlannedMinutes)
        } else {
            lines.wakeRepeat()
        }
        voice.announce(line, level = repeats)
    }

    private fun goToDepartCheck(session: NapSession, allowExtend: Boolean) {
        voice.stop()
        val now = clock()
        updateRecord { it.with(NapEvent(now, NapEvent.Kind.AWAKE)) }
        _state.value = NapState.DepartCheck(
            session = session,
            restedMinutes = session.restedMinutes(now),
            awakeAtMs = now,
            maxExtensionMin = if (allowExtend) session.maxExtensionMinutes(now) else 0,
        )
    }

    private fun raiseAlert(session: NapSession, reason: AlertReason) {
        if ((_state.value as? NapState.Alert)?.reason == reason) return
        _state.value = NapState.Alert(session, reason, snoozedUntilMs = null)
        lastVoiceAtMs = clock()
        voice.announce(alertLine(reason), level = NapPolicy.VOICE_REPEATS_BEFORE_ALERT)
        scope.launch {
            val caps = _capabilities.value ?: return@launch
            caps.fanSpeed?.let { range ->
                if (vehicle.setFanSpeed(range.last)) target = target.copy(fanSpeed = range.last)
            }
            if (caps.recirculation && vehicle.setRecirculation(false)) target = target.copy(recirculation = false)
        }
        if (tickJob?.isActive != true) startTicking()
    }

    private fun alertLine(reason: AlertReason): String = lines.alert(reason)

    private suspend fun onVehicleEvent(event: VehicleEvent) {
        val active = _state.value as? NapState.Active ?: return
        when (event) {
            is VehicleEvent.RecirculationChanged -> if (event.on && active.isProtectingAir()) revertToOutsideAir(active)
            is VehicleEvent.PowerDownWarning -> if (event.on && active !is NapState.DepartCheck) {
                raiseAlert(active.session, AlertReason.VEHICLE_WARNING)
            }
            VehicleEvent.DriverDoorOpened -> if (active is NapState.Resting || active is NapState.PreWake) wakeNow()
            is VehicleEvent.WiperChanged -> if (event.on && freshAirMode == FreshAirMode.WINDOW_GAP && active.isProtectingAir()) {
                closeWindowForRain(active)
            }
        }
    }

    /**
     * 창문 틈으로 바깥 공기를 넣던 중 와이퍼가 움직이면(비) 창문을 닫는다. 그러면 바깥 공기가 끊기므로 깨운다.
     * 차에 비 감지 공개 API가 없어 와이퍼 상태로 짐작한다(와이퍼가 자동이 아니면 비를 알 수 없다).
     */
    private suspend fun closeWindowForRain(active: NapState.Active) {
        val range = _capabilities.value?.driverWindow ?: return
        vehicle.setDriverWindow(range.first)
        freshAirMode = FreshAirMode.NONE
        target = target.copy(driverWindow = null)
        raiseAlert(active.session, AlertReason.RAIN)
    }

    private fun NapState.Active.isProtectingAir() =
        this is NapState.Resting || this is NapState.PreWake || this is NapState.Waking || this is NapState.Alert

    private suspend fun revertToOutsideAir(active: NapState.Active) {
        val now = clock()
        val reverted = vehicle.setRecirculation(false)
        val kind = if (reverted) {
            NapEvent.Kind.AIR_REVERTED
        } else if (openWindowGap(_capabilities.value ?: ClimateCapabilities())) {
            freshAirMode = FreshAirMode.WINDOW_GAP
            target = target.copy(recirculation = null, driverWindow = windowGapPosition)
            NapEvent.Kind.WINDOW_OPENED
        } else {
            raiseAlert(active.session, AlertReason.FRESH_AIR_LOST)
            return
        }
        updateRecord { it.with(NapEvent(now, kind, ClimateItem.AIR)) }
    }

    private suspend fun endByGear() {
        Log.i(TAG, "gear left P, ending nap")
        finish()
        _notices.tryEmit(NapNotice.ENDED_BY_GEAR)
    }

    private suspend fun finish() {
        tickJob?.cancel()
        tickJob = null
        checkJob?.cancel()
        checkJob = null
        voice.stop()
        _state.value = NapState.Idle
        restoreClimate()
        store.clearSession()
        _monitor.value = NapMonitor()
        target = ClimateSnapshot()
        defrosting = false
        powerOffChecks = 0
    }

    private fun updateRecord(change: (NapRecord) -> NapRecord) {
        val next = change(_monitor.value.record)
        _monitor.update { it.copy(record = next) }
        store.saveRecord(next)
    }

    // region 공조

    private suspend fun applyRestClimate(caps: ClimateCapabilities): RestClimate {
        // 공조가 꺼져 있으면 풍량·온도 변경이 거부된다(에뮬레이터에서 확인). 상태를 못 읽은 경우도 있어 항상 켠다.
        // 원래 꺼져 있었다면 끝날 때 restoreClimate가 다시 끈다.
        defrosting = false
        powerOffChecks = 0
        val powerOk = caps.hvacPower && vehicle.setHvacPower(true)
        val freshAir = when {
            caps.recirculation && vehicle.setRecirculation(false) -> FreshAirMode.OUTSIDE_AIR
            openWindowGap(caps) -> FreshAirMode.WINDOW_GAP
            else -> FreshAirMode.NONE
        }
        freshAirMode = freshAir
        val acOk = caps.ac && vehicle.setAc(plan.acOn)
        val temp = caps.temperature?.let { plan.restTempC.coerceIn(it.start, it.endInclusive) }
        val tempOk = temp != null && vehicle.setTemperature(plan.restTempC)
        val passengerOk = tempOk && passengerFollows(caps) && vehicle.setPassengerTemperature(plan.restTempC)
        val fan = caps.fanSpeed?.let { plan.restFanLevel.coerceIn(it.first, it.last) }
        val fanOk = fan != null && vehicle.setFanSpeed(plan.restFanLevel)
        val seatOk = plan.restSeatHeatLevel > 0 && caps.seatHeat != null && vehicle.setSeatHeat(plan.restSeatHeatLevel)
        // 적용에 성공한 항목만 지킨다. 차가 거부한 항목을 점검 때마다 다시 보내 봐야 소용이 없다.
        target = ClimateSnapshot(
            hvacPower = true.takeIf { powerOk },
            recirculation = false.takeIf { freshAir == FreshAirMode.OUTSIDE_AIR },
            ac = plan.acOn.takeIf { acOk },
            temperatureC = temp.takeIf { tempOk },
            passengerTemperatureC = temp.takeIf { passengerOk },
            fanSpeed = fan.takeIf { fanOk },
            seatHeat = plan.restSeatHeatLevel.takeIf { seatOk },
            driverWindow = windowGapPosition.takeIf { freshAir == FreshAirMode.WINDOW_GAP },
        )
        _monitor.update {
            it.copy(
                wakePlan = ClimateSnapshot(
                    fanSpeed = caps.fanSpeed?.let { r -> plan.wakeFanLevel.coerceIn(r.first, r.last) },
                    temperatureC = caps.temperature?.let { r -> plan.wakeTempC.coerceIn(r.start, r.endInclusive) },
                    defroster = true.takeIf { caps.anyDefroster },
                    steeringHeat = wakeSteeringLevel(caps),
                ),
            )
        }
        return RestClimate(
            freshAir = freshAir,
            temperatureC = temp.takeIf { tempOk },
            fanLevel = fan.takeIf { fanOk },
            seatHeatOn = seatOk,
        )
    }

    private fun initialWakeSteps(caps: ClimateCapabilities): List<WakeStep> = buildList {
        caps.fanSpeed?.let {
            add(WakeStep(WakeStepKind.FAN_UP, StepStatus.PENDING,
                plan.restFanLevel.coerceIn(it.first, it.last).toFloat(), plan.wakeFanLevel.coerceIn(it.first, it.last).toFloat()))
        }
        if (caps.temperature != null) add(WakeStep(WakeStepKind.TEMPERATURE, StepStatus.PENDING, plan.restTempC, plan.wakeTempC))
        if (caps.anyDefroster) add(WakeStep(WakeStepKind.DEFROST, StepStatus.PENDING))
        if (wakeSteeringLevel(caps) != null) add(WakeStep(WakeStepKind.STEERING_HEAT, StepStatus.PENDING))
        add(WakeStep(WakeStepKind.VOICE, StepStatus.PENDING))
    }

    /** 기상 연출: 소리보다 먼저 바람·온도로 몸을, 김서림 제거로 차를 깨운다. */
    private suspend fun applyWakeClimate(caps: ClimateCapabilities) {
        suspend fun step(kind: WakeStepKind, action: suspend () -> Boolean): Boolean {
            markStep(kind, StepStatus.RUNNING)
            val ok = action()
            markStep(kind, if (ok) StepStatus.DONE else StepStatus.SKIPPED)
            return ok
        }
        caps.fanSpeed?.let { range ->
            val level = plan.wakeFanLevel.coerceIn(range.first, range.last)
            if (step(WakeStepKind.FAN_UP) { vehicle.setFanSpeed(plan.wakeFanLevel) }) target = target.copy(fanSpeed = level)
        }
        caps.temperature?.let { range ->
            val temp = plan.wakeTempC.coerceIn(range.start, range.endInclusive)
            if (step(WakeStepKind.TEMPERATURE) { vehicle.setTemperature(plan.wakeTempC) }) {
                val passengerOk = passengerFollows(caps) && vehicle.setPassengerTemperature(plan.wakeTempC)
                target = target.copy(temperatureC = temp, passengerTemperatureC = temp.takeIf { passengerOk })
            }
        }
        wakeSteeringLevel(caps)?.let { level ->
            if (step(WakeStepKind.STEERING_HEAT) { vehicle.setSteeringHeat(level) }) target = target.copy(steeringHeat = level)
        }
        if (caps.anyDefroster) {
            markStep(WakeStepKind.DEFROST, StepStatus.RUNNING)
            // 앞 유리는 바람으로, 뒷유리는 열선으로 김을 뺀다. 둘 중 되는 것만 켠다.
            val frontOk = caps.defroster && vehicle.setDefroster(true)
            val rearOk = caps.rearDefroster && vehicle.setRearDefroster(true)
            if (frontOk || rearOk) {
                // 김서림 제거를 켜면 차가 습기를 빼려고 에어컨을 켜고 바람을 올리는 경우가 많다(현대차 공조 동작).
                // 에어컨은 바람을 식혀 습기를 뺀 뒤 히터로 다시 데우므로 겨울에도 춥지 않다.
                // 앱이 이걸 되돌리면 김이 덜 빠지므로: 에어컨은 지키지 않고(차에 맡김), 풍량은 '이 단계 이상'만 지킨다.
                defrosting = true
                target = target.copy(
                    defroster = true.takeIf { frontOk },
                    rearDefroster = true.takeIf { rearOk },
                    ac = null,
                )
                val now = clock()
                updateRecord { it.copy(defrostStartedAtMs = now, defrostEndedAtMs = null) }
            } else {
                // 김서림 제거는 기상 시각까지 '진행 중'으로 둔다. 실패했을 때만 바로 표시를 바꾼다.
                markStep(WakeStepKind.DEFROST, StepStatus.SKIPPED)
            }
        }
    }

    /** 깨울 때 핸들 열선 단계. 겨울이고 차가 지원할 때만. 화면 '예정'과 실제로 보내는 값이 같도록 한곳에서 정한다. */
    private fun wakeSteeringLevel(caps: ClimateCapabilities): Int? {
        val range = caps.steeringHeat ?: return null
        if (!plan.wakeSteeringHeat) return null
        return WAKE_STEERING_HEAT_LEVEL.coerceIn(range.first.coerceAtLeast(1), range.last)
    }

    /**
     * 동승석 온도도 같이 맞출지. 듀얼 공조가 켜진(운전석·동승석 따로) 차만 맞춘다.
     * 꺼져 있으면 운전석 온도가 차 전체에 적용되고, 동승석을 따로 바꾸면 차가 듀얼을 켜 버릴 수 있어 건드리지 않는다.
     */
    private fun passengerFollows(caps: ClimateCapabilities) = caps.passengerTemperature && snapshot.dualOn == true

    private fun markStep(kind: WakeStepKind, status: StepStatus) {
        val pre = _state.value as? NapState.PreWake ?: return
        val doneAt = if (status == StepStatus.DONE) clock() else null
        _state.value = pre.copy(steps = pre.steps.map { if (it.kind == kind) it.copy(status = status, doneAtMs = doneAt) else it })
    }

    private suspend fun openWindowGap(caps: ClimateCapabilities): Boolean {
        val range = caps.driverWindow ?: return false
        // 비가 오는 중(와이퍼 작동)에는 창문을 열지 않는다.
        if (vehicle.status.value.wiperOn == true) return false
        val travel = range.last - range.first
        if (travel <= 0) return false
        // 차마다 창문 단계가 다르다(에뮬레이터는 0~10, 한 단계 10%). 가능한 가장 작은 틈을 연다.
        val gap = range.first + ceil(travel * WINDOW_GAP_RATIO).toInt().coerceAtLeast(1)
        return vehicle.setDriverWindow(gap).also {
            if (it) {
                windowOpenedByApp = true
                windowGapPosition = gap
            }
        }
    }

    private suspend fun restoreClimate() {
        val saved = snapshot
        val caps = _capabilities.value ?: return
        // 시작 전 위치를 못 읽었어도 앱이 연 창문은 닫는다(창문 열린 채 출발 방지).
        val windowTo = caps.driverWindow?.let { range -> saved.driverWindow ?: range.first.takeIf { windowOpenedByApp } }
        sendRestore(saved, windowTo, only = null)

        // 차가 그 순간 거부했거나 늦게 반영해 바뀐 값이 남지 않도록, 한 번 다시 읽어 다른 항목만 한 번 더 보낸다.
        val after = vehicle.readClimate()
        val expected = saved.copy(
            hvacPower = saved.hvacPower?.takeIf { !it }, // 공조 전원은 원래 꺼져 있었을 때만 되돌린다
            passengerTemperatureC = saved.passengerTemperatureC?.takeIf { saved.dualOn == true },
            driverWindow = null,
            dualOn = null,
        )
        val again = ClimateCheck.mismatches(expected, after).toMutableSet()
        if (windowTo != null && after.driverWindow != null && after.driverWindow != windowTo) again += ClimateItem.WINDOW
        if (again.isNotEmpty()) {
            Log.i(TAG, "restore: retry $again")
            sendRestore(saved, windowTo, only = again)
        }
        windowOpenedByApp = false
        windowGapPosition = null
        snapshot = ClimateSnapshot()
    }

    /** 쪽잠 전 값으로 보낸다. 창문을 먼저 닫고, 공조 전원은 마지막에 끈다. [only]가 있으면 그 항목만. */
    private suspend fun sendRestore(saved: ClimateSnapshot, windowTo: Int?, only: Set<ClimateItem>?) {
        fun wants(item: ClimateItem) = only == null || item in only
        if (wants(ClimateItem.WINDOW)) windowTo?.let { vehicle.setDriverWindow(it) }
        if (wants(ClimateItem.DEFROST)) saved.defroster?.let { vehicle.setDefroster(it) }
        if (wants(ClimateItem.REAR_DEFROST)) saved.rearDefroster?.let { vehicle.setRearDefroster(it) }
        if (wants(ClimateItem.STEERING_HEAT)) saved.steeringHeat?.let { vehicle.setSteeringHeat(it) }
        if (wants(ClimateItem.SEAT_HEAT)) saved.seatHeat?.let { vehicle.setSeatHeat(it) }
        if (wants(ClimateItem.FAN)) saved.fanSpeed?.let { vehicle.setFanSpeed(it) }
        if (wants(ClimateItem.TEMPERATURE)) {
            saved.temperatureC?.let { vehicle.setTemperature(it) }
            if (saved.dualOn == true) saved.passengerTemperatureC?.let { vehicle.setPassengerTemperature(it) }
        }
        if (wants(ClimateItem.AC)) saved.ac?.let { vehicle.setAc(it) }
        if (wants(ClimateItem.AIR)) saved.recirculation?.let { vehicle.setRecirculation(it) }
        if (wants(ClimateItem.POWER)) saved.hvacPower?.let { if (!it) vehicle.setHvacPower(false) }
    }

    // endregion

    private companion object {
        const val TAG = "NapController"
        // Pleos Connect 탑재 차종(기획서 참고자료): 시연 모드에서 동력원을 가정할 때 함께 보여 준다.
        val DEMO_MODEL_NAMES = mapOf(
            Powertrain.COMBUSTION to "디 올 뉴 아반떼",
            Powertrain.HYBRID to "더 뉴 그랜저",
        )
        const val TICK_MS = 500L
        const val DEMO_VOICE_REPEAT_MS = 6_000L
        /** 점검 간격. 읽기 9번 정도라 이보다 짧으면 차량 서비스에 부담을 준다. */
        const val CHECK_MS = 10_000L
        const val DEMO_CHECK_MS = 4_000L
        /** 창문 전체 이동 범위의 3% ≒ 1~2cm */
        const val WINDOW_GAP_RATIO = 0.03
        /** 깨울 때 핸들 열선 단계(차 범위 안으로 자름) */
        const val WAKE_STEERING_HEAT_LEVEL = 2
        /** 공조가 이만큼 연속 점검에서 꺼져 있으면(다시 켜도) 경보 */
        const val POWER_OFF_ALERT_CHECKS = 2
    }
}
