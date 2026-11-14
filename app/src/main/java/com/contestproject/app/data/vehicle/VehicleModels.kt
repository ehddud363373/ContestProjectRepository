package com.contestproject.app.data.vehicle

enum class Powertrain { COMBUSTION, HYBRID, ELECTRIC, UNKNOWN }

enum class GearState { PARK, NOT_PARK, UNKNOWN }

sealed interface VehicleConnection {
    data object Connecting : VehicleConnection
    data object Connected : VehicleConnection
    /** 차량 서비스가 없거나(휴대폰 등) 권한이 거부된 경우 */
    data class Unavailable(val reason: UnavailableReason) : VehicleConnection
}

enum class UnavailableReason { NOT_A_CAR, SERVICE_MISSING, PERMISSION_DENIED, UNKNOWN }

data class VehicleStatus(
    val connection: VehicleConnection = VehicleConnection.Connecting,
    val gear: GearState = GearState.UNKNOWN,
    val outsideTempC: Float? = null,
    val cabinTempC: Float? = null,
    val powertrain: Powertrain = Powertrain.UNKNOWN,
    val modelName: String? = null,
    /** 운전석 문 열림. 문 위치를 못 읽으면 null */
    val driverDoorOpen: Boolean? = null,
    /** 전원 차단 경고(PowerDownWarningIndicator). 신호를 못 받으면 null */
    val vehicleWarning: Boolean? = null,
    /** 앞 와이퍼가 움직이는 중(비가 오는지 짐작하는 용도). 못 읽으면 null */
    val wiperOn: Boolean? = null,
)

/** 이 차에서 실제로 쓸 수 있는 제어. 차종마다 다르므로 시작 전에 확인한다. */
data class ClimateCapabilities(
    val hvacPower: Boolean = false,
    val recirculation: Boolean = false,
    val ac: Boolean = false,
    val temperature: ClosedFloatingPointRange<Float>? = null,
    val fanSpeed: IntRange? = null,
    val defroster: Boolean = false,
    /** 뒷유리 열선(김서림 제거) */
    val rearDefroster: Boolean = false,
    val seatHeat: IntRange? = null,
    val steeringHeat: IntRange? = null,
    val driverWindow: IntRange? = null,
    /** 운전석·동승석 온도를 따로 맞추는 듀얼 공조 상태를 읽을 수 있는가 */
    val dual: Boolean = false,
    /** 동승석 온도를 따로 맞출 수 있는가 */
    val passengerTemperature: Boolean = false,
) {
    /** 앞·뒤 유리 중 하나라도 김서림 제거가 되는가 */
    val anyDefroster: Boolean get() = defroster || rearDefroster

    /** 외기 유지 수단이 하나라도 있는가 (외기 전환 또는 창문 틈) */
    val canKeepFreshAir: Boolean get() = recirculation || driverWindow != null

    /** 재시도로 받은 결과를 합친다: 한 번이라도 지원한다고 답한 항목은 지원으로 본다. */
    fun mergedWith(other: ClimateCapabilities) = ClimateCapabilities(
        hvacPower = hvacPower || other.hvacPower,
        recirculation = recirculation || other.recirculation,
        ac = ac || other.ac,
        temperature = temperature ?: other.temperature,
        fanSpeed = fanSpeed ?: other.fanSpeed,
        defroster = defroster || other.defroster,
        rearDefroster = rearDefroster || other.rearDefroster,
        seatHeat = seatHeat ?: other.seatHeat,
        steeringHeat = steeringHeat ?: other.steeringHeat,
        driverWindow = driverWindow ?: other.driverWindow,
        dual = dual || other.dual,
        passengerTemperature = passengerTemperature || other.passengerTemperature,
    )
}

/** 쪽잠 시작 직전 공조 상태. 끝나면 이대로 되돌린다. null은 '읽지 못함 → 건드리지 않음'. */
data class ClimateSnapshot(
    val hvacPower: Boolean? = null,
    val recirculation: Boolean? = null,
    val ac: Boolean? = null,
    val temperatureC: Float? = null,
    val fanSpeed: Int? = null,
    val defroster: Boolean? = null,
    val rearDefroster: Boolean? = null,
    val seatHeat: Int? = null,
    val steeringHeat: Int? = null,
    val driverWindow: Int? = null,
    /** 동승석 설정 온도. 듀얼 공조가 켜져 있을 때만 읽고 맞춘다. */
    val passengerTemperatureC: Float? = null,
    /** 듀얼 공조(운전석·동승석 따로) 켜짐 여부 */
    val dualOn: Boolean? = null,
)

sealed interface VehicleEvent {
    data class RecirculationChanged(val on: Boolean) : VehicleEvent
    data class PowerDownWarning(val on: Boolean) : VehicleEvent
    data object DriverDoorOpened : VehicleEvent
    data class WiperChanged(val on: Boolean) : VehicleEvent
}
