package com.contestproject.app.service

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
import com.contestproject.app.domain.FreshAirMode
import com.contestproject.app.domain.NapNotice
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapSession
import com.contestproject.app.domain.NapState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NapControllerTest {

    private val minute = NapPolicy.MINUTE_MS

    private class FakeCar(caps: ClimateCapabilities) : VehicleGateway {
        override val status = MutableStateFlow(VehicleStatus())
        override val events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 8)
        var caps = caps
        // 현재 차 상태 (쪽잠 전: 내기순환, 20℃, 바람 1단)
        var recirculation = true
        var temperature = 20f
        var fan = 1
        var defroster = false
        var ac = false
        var window = 0
        var steeringHeat = 0
        var failRecirculation = false
        var failWindowRead = false

        override suspend fun connect() {
            status.update { it.copy(connection = VehicleConnection.Connected, gear = GearState.PARK, outsideTempC = 31f) }
        }
        override suspend fun capabilities() = caps
        override suspend fun readClimate() = ClimateSnapshot(
            recirculation = recirculation.takeIf { caps.recirculation },
            ac = ac, temperatureC = temperature, fanSpeed = fan, defroster = defroster,
            driverWindow = window.takeIf { caps.driverWindow != null && !failWindowRead },
        )
        override suspend fun setHvacPower(on: Boolean) = true
        override suspend fun setRecirculation(on: Boolean): Boolean {
            if (!caps.recirculation || failRecirculation) return false
            recirculation = on; return true
        }
        override suspend fun setAc(on: Boolean): Boolean { ac = on; return true }
        override suspend fun setTemperature(celsius: Float): Boolean { temperature = celsius; return true }
        override suspend fun setFanSpeed(level: Int): Boolean { fan = level; return true }
        override suspend fun setDefroster(on: Boolean): Boolean { defroster = on; return true }
        override suspend fun setSeatHeat(level: Int) = true
        override suspend fun setSteeringHeat(level: Int): Boolean { steeringHeat = level; return true }
        override suspend fun setDriverWindow(position: Int): Boolean {
            if (caps.driverWindow == null) return false
            window = position; return true
        }
        override fun release() = Unit
    }

    private class FakeVoice : WakeVoice {
        val spoken = mutableListOf<String>()
        override fun prepare() = Unit
        override fun announce(text: String, level: Int) { spoken += text }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class MemoryStore : NapStore {
        var saved: Pair<NapSession, ClimateSnapshot>? = null
        override fun save(session: NapSession, snapshot: ClimateSnapshot) { saved = session to snapshot }
        override fun updateSession(session: NapSession) { saved = saved?.copy(first = session) }
        override fun load() = saved
        override fun clearSession() { saved = null }
        override var demoMode = false
        override var lastDurationMin = 15
        override var powertrainOverride: String? = null
    }

    private object Lines : VoiceLines {
        override fun start(minutes: Int) = "start $minutes"
        override fun extend(minutes: Int) = "extend $minutes"
        override fun wake(minutes: Int) = "wake $minutes"
        override fun wakeRepeat() = "again"
        override fun alert(reason: AlertReason) = "alert $reason"
    }

    private val fullCaps = ClimateCapabilities(
        hvacPower = true, recirculation = true, ac = true,
        temperature = 16f..32f, fanSpeed = 1..8, defroster = true, driverWindow = 0..100,
    )

    private class Rig(val car: FakeCar, val voice: FakeVoice, val store: MemoryStore, val controller: NapController)

    private fun TestScope.rig(caps: ClimateCapabilities = fullCaps, store: MemoryStore = MemoryStore()): Rig {
        val car = FakeCar(caps)
        val voice = FakeVoice()
        val controller = NapController(car, voice, store, Lines, backgroundScope, clock = { testScheduler.currentTime })
        controller.start()
        runCurrent()
        return Rig(car, voice, store, controller)
    }

    @Test
    fun `쪽잠 한 번 전체 흐름 - 외기 유지, 기상 연출, 깨우기, 출발 시 원래대로`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()

        val resting = r.controller.state.value as NapState.Resting
        assertEquals(FreshAirMode.OUTSIDE_AIR, resting.climate.freshAir)
        assertFalse(r.car.recirculation)
        assertEquals(24f, r.car.temperature) // 바깥 31℃ → 여름 쪽잠 온도
        assertTrue(r.car.ac)
        assertEquals(2, r.car.fan)
        assertEquals("start 15", r.voice.spoken.last())
        assertTrue(r.store.saved != null)

        advanceTimeBy(12 * minute + 600)
        assertTrue(r.controller.state.value is NapState.PreWake)
        assertEquals(4, r.car.fan)
        assertEquals(22.5f, r.car.temperature)
        assertTrue(r.car.defroster)

        advanceTimeBy(3 * minute)
        assertTrue(r.controller.state.value is NapState.Waking)
        assertEquals("wake 15", r.voice.spoken.last())

        r.controller.acknowledgeWake()
        val check = r.controller.state.value as NapState.DepartCheck
        assertTrue(check.canExtend)
        assertEquals(15, check.restedMinutes)

        r.controller.depart()
        runCurrent()
        assertEquals(NapState.Idle, r.controller.state.value)
        assertTrue(r.car.recirculation)
        assertEquals(20f, r.car.temperature)
        assertEquals(1, r.car.fan)
        assertFalse(r.car.defroster)
        assertFalse(r.car.ac)
        assertNull(r.store.saved)
    }

    @Test
    fun `쉬는 중 내기순환이 켜지면 바깥 공기로 되돌린다`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()

        r.car.recirculation = true
        r.car.events.emit(VehicleEvent.RecirculationChanged(on = true))
        runCurrent()

        assertFalse(r.car.recirculation)
        val resting = r.controller.state.value as NapState.Resting
        assertEquals(1, resting.guardLog.size)
    }

    @Test
    fun `외기 전환이 안 되는 차는 창문 틈으로 대신한다`() = runTest {
        val r = rig(caps = fullCaps.copy(recirculation = false))
        r.controller.startNap(15)
        runCurrent()

        val resting = r.controller.state.value as NapState.Resting
        assertEquals(FreshAirMode.WINDOW_GAP, resting.climate.freshAir)
        assertEquals(3, r.car.window) // 0..100 범위의 3%

        r.controller.wakeNow()
        runCurrent()
        r.controller.depart()
        runCurrent()
        assertEquals(0, r.car.window)
    }

    @Test
    fun `시작 전 창문 위치를 못 읽었어도 앱이 연 창문은 출발할 때 닫는다`() = runTest {
        val r = rig(caps = fullCaps.copy(recirculation = false))
        r.car.failWindowRead = true
        r.controller.startNap(15)
        runCurrent()
        assertEquals(3, r.car.window)

        r.controller.wakeNow()
        runCurrent()
        r.controller.depart()
        runCurrent()
        assertEquals(0, r.car.window)
    }

    @Test
    fun `바깥 공기를 지킬 방법이 없으면 시작하지 않는다`() = runTest {
        val r = rig(caps = fullCaps.copy(recirculation = false, driverWindow = null))
        val notices = mutableListOf<NapNotice>()
        backgroundScope.launch { r.controller.notices.collect { notices += it } }
        runCurrent()

        r.controller.startNap(15)
        runCurrent()

        assertEquals(NapState.Idle, r.controller.state.value)
        assertEquals(listOf(NapNotice.START_FAILED), notices)
        assertNull(r.store.saved)
    }

    @Test
    fun `기어가 P에서 벗어나면 바로 끝내고 되돌린다`() = runTest {
        val r = rig()
        r.controller.startNap(20)
        runCurrent()

        r.car.status.update { it.copy(gear = GearState.NOT_PARK) }
        runCurrent()

        assertEquals(NapState.Idle, r.controller.state.value)
        assertTrue(r.car.recirculation)
        assertNull(r.store.saved)
    }

    @Test
    fun `P단이 아니면 시작 버튼이 동작하지 않는다`() = runTest {
        val r = rig()
        r.car.status.update { it.copy(gear = GearState.NOT_PARK) }
        r.controller.startNap(15)
        runCurrent()
        assertEquals(NapState.Idle, r.controller.state.value)
    }

    @Test
    fun `연장은 한 번만 되고 두 번째 깨우기에서는 막힌다`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()
        advanceTimeBy(15 * minute + 600)
        r.controller.acknowledgeWake()

        r.controller.extend()
        runCurrent()
        val resting = r.controller.state.value as NapState.Resting
        assertTrue(resting.session.extensionUsed)
        assertFalse(r.car.defroster) // 다시 쉬는 동안은 김서림 제거를 끈다
        assertEquals(2, r.car.fan)

        advanceTimeBy(10 * minute + 600)
        r.controller.acknowledgeWake()
        val check = r.controller.state.value as NapState.DepartCheck
        assertFalse(check.canExtend)

        r.controller.extend()
        runCurrent()
        assertTrue(r.controller.state.value is NapState.DepartCheck)
    }

    @Test
    fun `반응이 없으면 반복해 깨우다가 경보로 올린다`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()
        advanceTimeBy(15 * minute + 600)
        assertTrue(r.controller.state.value is NapState.Waking)

        advanceTimeBy(NapPolicy.VOICE_REPEATS_BEFORE_ALERT * NapPolicy.VOICE_REPEAT_SEC * 1000L + 1000)
        val alert = r.controller.state.value as NapState.Alert
        assertEquals(AlertReason.NO_RESPONSE, alert.reason)
        assertEquals(8, r.car.fan) // 바람 최대
    }

    @Test
    fun `차량 경고가 켜지면 타이머와 상관없이 즉시 깨운다`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()

        r.car.events.emit(VehicleEvent.PowerDownWarning(on = true))
        runCurrent()

        val alert = r.controller.state.value as NapState.Alert
        assertEquals(AlertReason.VEHICLE_WARNING, alert.reason)
        assertEquals("alert VEHICLE_WARNING", r.voice.spoken.last())

        r.controller.acknowledgeWake()
        val check = r.controller.state.value as NapState.DepartCheck
        assertFalse(check.canExtend) // 경고 뒤에는 더 자지 않게
    }

    @Test
    fun `운전석 문이 열리면 깬 것으로 보고 출발 확인으로 간다`() = runTest {
        val r = rig()
        r.controller.startNap(15)
        runCurrent()

        r.car.events.emit(VehicleEvent.DriverDoorOpened)
        runCurrent()

        assertTrue(r.controller.state.value is NapState.DepartCheck)
        assertTrue(r.car.defroster)
    }

    @Test
    fun `시연 모드에서만 동력원을 가정해 볼 수 있고 끄면 실제 값으로 돌아간다`() = runTest {
        val r = rig()
        r.car.status.update { it.copy(powertrain = Powertrain.ELECTRIC) }
        runCurrent()

        r.controller.cyclePowertrainOverride()
        runCurrent()
        assertEquals(Powertrain.ELECTRIC, r.controller.vehicleStatus.value.powertrain) // 시연 모드가 아니면 무시

        r.controller.demoMode = true
        r.controller.cyclePowertrainOverride()
        runCurrent()
        assertEquals(Powertrain.COMBUSTION, r.controller.vehicleStatus.value.powertrain)
        assertEquals("디 올 뉴 아반떼", r.controller.vehicleStatus.value.modelName)

        r.controller.cyclePowertrainOverride()
        runCurrent()
        assertEquals(Powertrain.HYBRID, r.controller.vehicleStatus.value.powertrain)

        r.controller.demoMode = false
        runCurrent()
        assertEquals(Powertrain.ELECTRIC, r.controller.vehicleStatus.value.powertrain)
        assertNull(r.store.powertrainOverride)
    }

    @Test
    fun `앱이 다시 켜졌을 때 이미 시간이 지났으면 바로 깨운다`() = runTest {
        val store = MemoryStore().apply {
            saved = NapSession.start(0, 15) to ClimateSnapshot(recirculation = true, temperatureC = 20f, fanSpeed = 1)
        }
        advanceTimeBy(20 * minute)
        val r = rig(store = store)

        assertTrue(r.controller.state.value is NapState.Waking)
        assertFalse(r.car.recirculation)

        r.controller.acknowledgeWake()
        r.controller.depart()
        runCurrent()
        assertTrue(r.car.recirculation)
        assertEquals(20f, r.car.temperature)
    }
}
