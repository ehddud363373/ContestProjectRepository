package com.contestproject.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK(공조·시트·핸들·창문·안전) + AAOS CarPropertyManager(기어·바깥 온도·동력원) 구현.
 * 기어는 Pleos 공개 API에 없어서 Pleos 문서가 안내하는 대로 AAOS 표준 속성으로 읽는다.
 */
class PleosVehicleGateway(
    context: Context,
    private val scope: CoroutineScope,
) : VehicleGateway {

    private val appContext = context.applicationContext
    private val carProperties = CarPropertySource(appContext)

    private val _status = MutableStateFlow(VehicleStatus())
    override val status: StateFlow<VehicleStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()

    private var vehicle: Vehicle? = null
    private var cachedCapabilities: ClimateCapabilities? = null
    private val capabilityLock = Mutex()

    // 차종마다 공조 영역 구성이 달라서, 능력 확인에서 돌려받은 영역 객체를 그대로 쓴다.
    // 공조 속성마다 지원하는 영역(area ID)이 다르다(예: 외기는 전체 좌석 묶음, 온도는 운전석). 속성별로 따로 둔다.
    private var recircArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var powerArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var acArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var tempArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var fanArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var seatHeatArea: SeatArea = SeatArea(SeatValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)

    private val recirculationListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
            state?.let { _events.tryEmit(VehicleEvent.RecirculationChanged(it)) }
        }
        override fun onFailed(e: Exception) = logFailure("recirculation listener", e)
    }
    private val powerDownListener = object : PowerDownWarningIndicatorListener {
        override fun onPowerDownWarningIndicatorUpdated(isOn: Boolean?) {
            isOn?.let { _events.tryEmit(VehicleEvent.PowerDownWarning(it)) }
        }
        override fun onFailed(e: Exception) = logFailure("power-down listener", e)
    }
    // 등록 직후 들어오는 현재값은 "열림 이벤트"가 아니다. 닫힘(0) → 열림(>0)으로 바뀔 때만 알린다.
    private var lastDoorPosition: Int? = null
    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            val previous = lastDoorPosition
            lastDoorPosition = pos
            if (previous == 0 && (pos ?: 0) > 0) _events.tryEmit(VehicleEvent.DriverDoorOpened)
        }
        override fun onFailed(e: Exception) = logFailure("door listener", e)
    }

    override suspend fun connect() {
        if (vehicle != null) return
        _status.update { it.copy(connection = VehicleConnection.Connecting) }

        // Car.createCar()·Vehicle.initialize()는 서비스에 붙을 때까지 기다리는 호출이라 메인 스레드에서 하면
        // 느린 환경에서 앱 시작이 막힌다(에뮬레이터에서 ANR로 종료됨). 백그라운드에서 연결한다.
        val carResult = withContext(Dispatchers.IO) { carProperties.connect { gear, outside -> _status.update { it.copy(gear = gear, outsideTempC = outside ?: it.outsideTempC) } } }
        if (carResult != null) {
            _status.update { it.copy(connection = VehicleConnection.Unavailable(carResult)) }
            return
        }

        val sdk = try {
            withContext(Dispatchers.IO) { Vehicle(appContext).also { it.initialize() } }
        } catch (e: SecurityException) {
            logFailure("initialize", e)
            _status.update { it.copy(connection = VehicleConnection.Unavailable(UnavailableReason.PERMISSION_DENIED)) }
            return
        } catch (e: Exception) {
            logFailure("initialize", e)
            _status.update { it.copy(connection = VehicleConnection.Unavailable(UnavailableReason.SERVICE_MISSING)) }
            return
        }
        vehicle = sdk

        val info = withContext(Dispatchers.IO) {
            VehicleStatus(
                connection = VehicleConnection.Connected,
                powertrain = carProperties.readPowertrain(),
                modelName = carProperties.readModelName(),
                gear = carProperties.readGear(),
                outsideTempC = carProperties.readOutsideTemp(),
            )
        }
        _status.update { info.copy(cabinTempC = it.cabinTempC) }
        // 리스너는 능력 확인으로 실제 영역을 알아낸 뒤에 등록한다.
        scope.launch {
            capabilities()
            // Pleos SDK addListener는 서비스와 통신하며 1~2초씩 걸려 메인 스레드에서 하지 않는다.
            withContext(Dispatchers.IO) { registerListeners(sdk) }
            refreshCabinTemp()
        }
    }

    private fun registerListeners(sdk: Vehicle) {
        runCatching { sdk.getHVAC().registerHvacRecirculationState(recircArea, recirculationListener) }
            .onFailure { logFailure("register recirculation", it) }
        runCatching { sdk.getSafety().registerPowerDownWarningIndicator(powerDownListener) }
            .onFailure { logFailure("register power-down", it) }
        runCatching { sdk.getDoor().registerDoorPosition(driverDoor, doorListener) }
            .onFailure { logFailure("register door", it) }
    }

    private suspend fun refreshCabinTemp() {
        val hvac = vehicle?.getHVAC() ?: return
        val cabin = awaitSdk<Float?> { ok, fail -> hvac.getHvacTemperatureCurrent(tempArea, TemperatureUnit.CELSIUS, ok, fail) }
        if (cabin != null) _status.update { it.copy(cabinTempC = cabin) }
    }

    override suspend fun capabilities(): ClimateCapabilities = capabilityLock.withLock {
        cachedCapabilities?.let { return it }
        val sdk = vehicle ?: return ClimateCapabilities()
        // 초기화 직후에는 서비스 바인딩이 끝나지 않아 실패할 수 있어 짧게 재시도한다.
        repeat(CAPABILITY_RETRIES) { attempt ->
            val caps = readCapabilities(sdk)
            if (caps != ClimateCapabilities() || attempt == CAPABILITY_RETRIES - 1) {
                if (caps != ClimateCapabilities()) cachedCapabilities = caps
                Log.i(TAG, "capabilities: $caps")
                return caps
            }
            delay(RETRY_DELAY_MS)
        }
        return ClimateCapabilities()
    }

    private suspend fun readCapabilities(sdk: Vehicle): ClimateCapabilities = coroutineScope {
        val hvac = sdk.getHVAC()
        // 9가지 확인을 동시에 보낸다. 부팅 직후 차량 서비스가 느려도 전체 대기 시간이 한 번 분량으로 끝난다.
        // 읽기만 되는(READ) 항목은 제어할 수 없으므로 쓰기 권한이 있는 항목만 '지원'으로 본다.
        val recircAreasJob = async { awaitSdk<List<SeatArea>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacRecirculationCapabilities({ c: CapabilityConfig, areas: List<SeatArea> -> ok(areas.takeIf { c.writable }) }, fail) }
        }
        val powerJob = async { awaitSdk<List<SeatArea>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacPowerCapabilities({ c: CapabilityConfig, areas: List<SeatArea> -> ok(areas.takeIf { c.writable }) }, fail) }
        }
        val acJob = async { awaitSdk<List<SeatArea>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacAcCapabilities({ c: CapabilityConfig, areas: List<SeatArea> -> ok(areas.takeIf { c.writable }) }, fail) }
        }
        val tempJob = async { awaitSdk<Map<SeatArea, AreaBounds<Float>>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacTemperatureCapabilities({ c: CapabilityConfig, b: Map<SeatArea, AreaBounds<Float>>, _: HvacTemperatureConfigArray? -> ok(b.takeIf { c.writable }) }, fail) }
        }
        val fanJob = async { awaitSdk<Map<SeatArea, AreaBounds<Int>>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacFanSpeedCapabilities({ c: CapabilityConfig, b: Map<SeatArea, AreaBounds<Int>> -> ok(b.takeIf { c.writable }) }, fail) }
        }
        val defrostJob = async { awaitSdk<List<WindowArea>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            hvac.checkHvacDefrosterCapabilities({ c: CapabilityConfig, areas: List<WindowArea> -> ok(areas.takeIf { c.writable }) }, fail) }
        }
        val seatJob = async { awaitSdk<Map<SeatArea, AreaBounds<Int>>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            sdk.getSeat().checkSeatTemperatureCapabilities({ c: CapabilityConfig, b: Map<SeatArea, AreaBounds<Int>> -> ok(b.takeIf { c.writable }) }, fail) }
        }
        val steeringJob = async { awaitSdk<AreaBounds<Int>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            sdk.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities({ c: CapabilityConfig, b: AreaBounds<Int> -> ok(b.takeIf { c.writable }) }, fail) }
        }
        val windowJob = async { awaitSdk<Map<WindowArea, AreaBounds<Int>>?>(CAPABILITY_TIMEOUT_MS) { ok, fail ->
            sdk.getWindow().checkWindowPositionCapabilities({ c: CapabilityConfig, b: Map<WindowArea, AreaBounds<Int>> -> ok(b.takeIf { c.writable }) }, fail) }
        }

        val recircAreas = recircAreasJob.await()
        val power = powerJob.await()
        val ac = acJob.await()
        val temp = tempJob.await()
        val fan = fanJob.await()
        val defrost = defrostJob.await()
        val seat = seatJob.await()
        val steering = steeringJob.await()
        val window = windowJob.await()

        recircAreas?.let { pickDriverArea(it) }?.let { recircArea = it }
        power?.let { pickDriverArea(it) }?.let { powerArea = it }
        ac?.let { pickDriverArea(it) }?.let { acArea = it }
        val tempEntry = temp?.let { driverEntry(it) }?.also { tempArea = it.key }
        val fanEntry = fan?.let { driverEntry(it) }?.also { fanArea = it.key }
        val tempBounds = tempEntry?.value
        val fanBounds = fanEntry?.value
        val seatEntry = seat?.entries?.let { entries -> entries.firstOrNull { SeatValue.ROW_1_LEFT in it.key.values } ?: entries.firstOrNull() }
        seatEntry?.let { seatHeatArea = it.key }
        val windowBounds = window?.entries?.firstOrNull { WindowValue.ROW_1_LEFT in it.key.values }?.value

        ClimateCapabilities(
            hvacPower = !power.isNullOrEmpty(),
            recirculation = !recircAreas.isNullOrEmpty(),
            ac = !ac.isNullOrEmpty(),
            temperature = tempBounds?.let { it.minValue..it.maxValue },
            fanSpeed = fanBounds?.let { it.minValue..it.maxValue },
            defroster = defrost?.any { WindowValue.FRONT_WINDSHIELD in it.values } == true,
            seatHeat = seatEntry?.value?.let { it.minValue..it.maxValue }?.takeIf { it.last > 0 },
            steeringHeat = steering?.let { it.minValue..it.maxValue }?.takeIf { it.last > 0 },
            driverWindow = windowBounds?.let { it.minValue..it.maxValue },
        )
    }

    private val CapabilityConfig.writable: Boolean
        get() = accessType == AccessType.WRITE || accessType == AccessType.READ_WRITE

    private fun pickDriverArea(areas: List<SeatArea>): SeatArea? =
        areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull()

    private fun <T> driverEntry(map: Map<SeatArea, AreaBounds<T>>): Map.Entry<SeatArea, AreaBounds<T>>? =
        map.entries.firstOrNull { SeatValue.ROW_1_LEFT in it.key.values } ?: map.entries.firstOrNull()

    override suspend fun readClimate(): ClimateSnapshot {
        val sdk = vehicle ?: return ClimateSnapshot()
        val caps = capabilities()
        val hvac = sdk.getHVAC()
        return ClimateSnapshot(
            hvacPower = if (caps.hvacPower) awaitSdk<Boolean?> { ok, fail -> hvac.getHvacPowerState(powerArea, ok, fail) } else null,
            recirculation = if (caps.recirculation) awaitSdk<Boolean?> { ok, fail -> hvac.getHvacRecirculationState(recircArea, ok, fail) } else null,
            ac = if (caps.ac) awaitSdk<Boolean?> { ok, fail -> hvac.getHvacAcState(acArea, ok, fail) } else null,
            temperatureC = if (caps.temperature != null) awaitSdk<Float?> { ok, fail -> hvac.getHvacTemperature(tempArea, TemperatureUnit.CELSIUS, ok, fail) } else null,
            fanSpeed = if (caps.fanSpeed != null) awaitSdk<Int?> { ok, fail -> hvac.getHvacFanSpeed(fanArea, ok, fail) } else null,
            defroster = if (caps.defroster) awaitSdk<Boolean?> { ok, fail -> hvac.getHvacDefrosterState(windshield, ok, fail) } else null,
            seatHeat = if (caps.seatHeat != null) awaitSdk<Int?> { ok, fail -> sdk.getSeat().getHvacSeatTemperature(seatHeatArea, ok, fail) } else null,
            steeringHeat = if (caps.steeringHeat != null) awaitSdk<Int?> { ok, fail -> sdk.getSteeringWheel().getHvacSteeringWheelHeatState(ok, fail) } else null,
            driverWindow = if (caps.driverWindow != null) awaitSdk<Int?> { ok, fail -> sdk.getWindow().getCurrentWindowPosition(driverWindow, ok, fail) } else null,
        )
    }

    override suspend fun setHvacPower(on: Boolean): Boolean {
        val ok = control("hvac power") { done, fail ->
            requireVehicle().getHVAC().setHvacPower(powerArea, on, { _, _ -> done() }, fail)
        }
        if (ok) {
            val actual = awaitSdk<Boolean?> { done, fail -> requireVehicle().getHVAC().getHvacPowerState(powerArea, done, fail) }
            Log.d(TAG, "hvac power area=${powerArea.values.toList()} requested=$on actual=$actual")
        }
        return ok
    }

    override suspend fun setRecirculation(on: Boolean) = control("recirculation") { ok, fail ->
        requireVehicle().getHVAC().setHvacRecirculation(recircArea, on, { _, _ -> ok() }, fail)
    }

    override suspend fun setAc(on: Boolean) = control("ac") { ok, fail ->
        requireVehicle().getHVAC().setHvacAc(acArea, on, { _, _ -> ok() }, fail)
    }

    override suspend fun setTemperature(celsius: Float): Boolean {
        val range = capabilities().temperature ?: return false
        val target = celsius.coerceIn(range.start, range.endInclusive)
        return control("temperature") { ok, fail ->
            requireVehicle().getHVAC().setHvacTemperature(tempArea, target, TemperatureUnit.CELSIUS, { _, _ -> ok() }, fail)
        }
    }

    override suspend fun setFanSpeed(level: Int): Boolean {
        val range = capabilities().fanSpeed ?: return false
        val target = level.coerceIn(range.first, range.last)
        return control("fan speed") { ok, fail ->
            requireVehicle().getHVAC().setHvacFanSpeed(fanArea, target, { _, _ -> ok() }, fail)
        }
    }

    override suspend fun setDefroster(on: Boolean) = control("defroster") { ok, fail ->
        requireVehicle().getHVAC().setHvacDefroster(windshield, on, { _, _ -> ok() }, fail)
    }

    override suspend fun setSeatHeat(level: Int): Boolean {
        val range = capabilities().seatHeat ?: return false
        val target = level.coerceIn(range.first, range.last)
        return control("seat heat") { ok, fail ->
            requireVehicle().getSeat().setHvacSeatTemperature(seatHeatArea, target, { _, _ -> ok() }, fail)
        }
    }

    override suspend fun setSteeringHeat(level: Int): Boolean {
        val range = capabilities().steeringHeat ?: return false
        val target = level.coerceIn(range.first, range.last)
        return control("steering heat") { ok, fail ->
            requireVehicle().getSteeringWheel().setHvacSteeringWheelHeat(target, { _ -> ok() }, fail)
        }
    }

    override suspend fun setDriverWindow(position: Int): Boolean {
        val range = capabilities().driverWindow ?: return false
        val target = position.coerceIn(range.first, range.last)
        return control("driver window") { ok, fail ->
            requireVehicle().getWindow().setWindowPosition(driverWindow, target, { _, _ -> ok() }, fail)
        }
    }

    override fun release() {
        val sdk = vehicle ?: return
        runCatching { sdk.getHVAC().unregisterHvacRecirculationState(recircArea, recirculationListener) }
        runCatching { sdk.getSafety().unregisterPowerDownWarningIndicator(powerDownListener) }
        runCatching { sdk.getDoor().unregisterDoorPosition(driverDoor, doorListener) }
        runCatching { sdk.release() }
        carProperties.release()
        vehicle = null
        cachedCapabilities = null
    }

    private fun requireVehicle(): Vehicle = checkNotNull(vehicle) { "Vehicle SDK not connected" }

    private suspend fun control(name: String, call: (ok: () -> Unit, fail: (Exception) -> Unit) -> Unit): Boolean =
        (awaitSdk<Boolean> { ok, fail -> call({ ok(true) }, fail) } == true)
            .also { Log.d(TAG, "control $name -> ${if (it) "ok" else "failed"}") }

    /** 콜백 API를 suspend로 바꾼다. 실패·시간 초과·예외는 모두 null. */
    private suspend fun <T> awaitSdk(
        timeoutMs: Long = SDK_TIMEOUT_MS,
        call: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): T? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<T?> { cont ->
                try {
                    call(
                        { value -> if (cont.isActive) cont.resume(value) },
                        { e -> logFailure("sdk call", e); if (cont.isActive) cont.resume(null) },
                    )
                } catch (e: Exception) {
                    logFailure("sdk call", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private fun logFailure(what: String, t: Throwable) {
        Log.w(TAG, "$what failed: ${t.javaClass.simpleName} ${t.message}")
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val SDK_TIMEOUT_MS = 2_000L   // Pleos 심사 기준: 버튼 반응 2초 이내
        // 시작할 때 한 번 하는 능력 확인은 로딩 표시 중이라 더 기다린다(부팅 직후 차량 서비스가 5초 넘게 걸린 적 있음).
        const val CAPABILITY_TIMEOUT_MS = 6_000L
        const val CAPABILITY_RETRIES = 3
        const val RETRY_DELAY_MS = 400L
    }
}
