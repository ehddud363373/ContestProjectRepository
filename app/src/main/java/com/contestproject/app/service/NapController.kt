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
                goToDepartCheck(active.session, canExtend = active.session.canExtend)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 깨우기 화면 [일어났어요] */
    fun acknowledgeWake() {
        val active = _state.value as? NapState.Active ?: return
        when (active) {
            is NapState.Waking -> goToDepartCheck(active.session, active.session.canExtend)
            is NapState.Alert -> goToDepartCheck(
                active.session,
                canExtend = active.reason == AlertReason.NO_RESPONSE && active.session.canExtend,
            )
            else -> Unit
        }
    }

    fun snoozeAlert() {
        val alert = _state.value as? NapState.Alert ?: return
        voice.stop()
        _state.value = alert.copy(snoozedUntilMs = clock() + NapPolicy.ALERT_SNOOZE_SEC * 1000L)
    }

    fun extend() {
        val check = _state.value as? NapState.DepartCheck ?: return
        if (!check.canExtend) return
        scope.launch {
            _busy.value = true
            try {
                val now = clock()
                val session = check.session.extend(now)
                store.updateSession(session)
                updateRecord { it.with(NapEvent(now, NapEvent.Kind.EXTENDED)).copy(defrostEndedAtMs = it.defrostEndedAtMs ?: now) }
                val caps = _capabilities.value ?: ClimateCapabilities()
                if (caps.defroster) vehicle.setDefroster(false)
                if (plan.wakeSteeringHeat && caps.steeringHeat != null) vehicle.setSteeringHeat(0)
                val climate = applyRestClimate(caps)
                if (caps.defroster) target = target.copy(defroster = false)
                _state.value = NapState.Resting(session, climate)
                voice.announce(lines.extend(NapPolicy.EXTENSION_MIN))
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
        voice.prepare()
        snapshot = saved
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
        for (item in ClimateCheck.mismatches(wanted, reading)) {
            Log.i(TAG, "check: $item differs (want=$wanted got=$reading)")
            when (item) {
                ClimateItem.AIR -> revertToOutsideAir(_state.value as? NapState.Active ?: return)
                else -> if (reapply(item, wanted)) {
                    updateRecord { it.with(NapEvent(now, NapEvent.Kind.CORRECTED, item)) }
                }
            }
        }
    }

    private suspend fun reapply(item: ClimateItem, wanted: ClimateSnapshot): Boolean = when (item) {
        ClimateItem.POWER -> wanted.hvacPower?.let { vehicle.setHvacPower(it) } ?: false
        ClimateItem.AC -> wanted.ac?.let { vehicle.setAc(it) } ?: false
        ClimateItem.TEMPERATURE -> wanted.temperatureC?.let { vehicle.setTemperature(it) } ?: false
        ClimateItem.FAN -> wanted.fanSpeed?.let { vehicle.setFanSpeed(it) } ?: false
        ClimateItem.DEFROST -> wanted.defroster?.let { vehicle.setDefroster(it) } ?: false
        ClimateItem.SEAT_HEAT -> wanted.seatHeat?.let { vehicle.setSeatHeat(it) } ?: false
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
            lines.wake(session.plannedMinutes)
        } else {
            lines.wakeRepeat()
        }
        voice.announce(line, level = repeats)
    }

    private fun goToDepartCheck(session: NapSession, canExtend: Boolean) {
        voice.stop()
        val now = clock()
        updateRecord { it.with(NapEvent(now, NapEvent.Kind.AWAKE)) }
        _state.value = NapState.DepartCheck(
            session = session,
            restedMinutes = session.restedMinutes(now),
            canExtend = canExtend,
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
        }
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
        val fan = caps.fanSpeed?.let { plan.restFanLevel.coerceIn(it.first, it.last) }
        val fanOk = fan != null && vehicle.setFanSpeed(plan.restFanLevel)
        val seatOk = plan.restSeatHeatLevel > 0 && caps.seatHeat != null && vehicle.setSeatHeat(plan.restSeatHeatLevel)
        // 적용에 성공한 항목만 지킨다. 차가 거부한 항목을 점검 때마다 다시 보내 봐야 소용이 없다.
        target = ClimateSnapshot(
            hvacPower = true.takeIf { powerOk },
            recirculation = false.takeIf { freshAir == FreshAirMode.OUTSIDE_AIR },
            ac = plan.acOn.takeIf { acOk },
            temperatureC = temp.takeIf { tempOk },
            fanSpeed = fan.takeIf { fanOk },
            seatHeat = plan.restSeatHeatLevel.takeIf { seatOk },
            driverWindow = windowGapPosition.takeIf { freshAir == FreshAirMode.WINDOW_GAP },
        )
        _monitor.update {
            it.copy(
                wakePlan = ClimateSnapshot(
                    fanSpeed = caps.fanSpeed?.let { r -> plan.wakeFanLevel.coerceIn(r.first, r.last) },
                    temperatureC = caps.temperature?.let { r -> plan.wakeTempC.coerceIn(r.start, r.endInclusive) },
                    defroster = true.takeIf { caps.defroster },
                    steeringHeat = 1.takeIf { plan.wakeSteeringHeat && caps.steeringHeat != null },
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
        if (caps.defroster) add(WakeStep(WakeStepKind.DEFROST, StepStatus.PENDING))
        if (plan.wakeSteeringHeat && caps.steeringHeat != null) add(WakeStep(WakeStepKind.STEERING_HEAT, StepStatus.PENDING))
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
            if (step(WakeStepKind.TEMPERATURE) { vehicle.setTemperature(plan.wakeTempC) }) target = target.copy(temperatureC = temp)
        }
        if (plan.wakeSteeringHeat && caps.steeringHeat != null) {
            step(WakeStepKind.STEERING_HEAT) { vehicle.setSteeringHeat(caps.steeringHeat.last.coerceAtMost(2)) }
        }
        if (caps.defroster) {
            markStep(WakeStepKind.DEFROST, StepStatus.RUNNING)
            if (vehicle.setDefroster(true)) {
                target = target.copy(defroster = true)
                val now = clock()
                updateRecord { it.copy(defrostStartedAtMs = now, defrostEndedAtMs = null) }
            } else {
                // 김서림 제거는 기상 시각까지 '진행 중'으로 둔다. 실패했을 때만 바로 표시를 바꾼다.
                markStep(WakeStepKind.DEFROST, StepStatus.SKIPPED)
            }
        }
    }

    private fun markStep(kind: WakeStepKind, status: StepStatus) {
        val pre = _state.value as? NapState.PreWake ?: return
        val doneAt = if (status == StepStatus.DONE) clock() else null
        _state.value = pre.copy(steps = pre.steps.map { if (it.kind == kind) it.copy(status = status, doneAtMs = doneAt) else it })
    }

    private suspend fun openWindowGap(caps: ClimateCapabilities): Boolean {
        val range = caps.driverWindow ?: return false
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
        caps.driverWindow?.let { range ->
            when {
                saved.driverWindow != null -> vehicle.setDriverWindow(saved.driverWindow)
                // 시작 전 위치를 못 읽었어도 앱이 연 창문은 닫는다(창문 열린 채 출발 방지).
                windowOpenedByApp -> vehicle.setDriverWindow(range.first)
                else -> Unit
            }
        }
        windowOpenedByApp = false
        windowGapPosition = null
        saved.defroster?.let { vehicle.setDefroster(it) }
        saved.steeringHeat?.let { vehicle.setSteeringHeat(it) }
        saved.seatHeat?.let { vehicle.setSeatHeat(it) }
        saved.fanSpeed?.let { vehicle.setFanSpeed(it) }
        saved.temperatureC?.let { vehicle.setTemperature(it) }
        saved.ac?.let { vehicle.setAc(it) }
        saved.recirculation?.let { vehicle.setRecirculation(it) }
        saved.hvacPower?.let { if (!it) vehicle.setHvacPower(false) }
        snapshot = ClimateSnapshot()
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
    }
}
