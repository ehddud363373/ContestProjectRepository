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
)

/** 이 차에서 실제로 쓸 수 있는 제어. 차종마다 다르므로 시작 전에 확인한다. */
data class ClimateCapabilities(
    val hvacPower: Boolean = false,
    val recirculation: Boolean = false,
    val ac: Boolean = false,
    val temperature: ClosedFloatingPointRange<Float>? = null,
    val fanSpeed: IntRange? = null,
    val defroster: Boolean = false,
    val seatHeat: IntRange? = null,
    val steeringHeat: IntRange? = null,
    val driverWindow: IntRange? = null,
) {
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
        seatHeat = seatHeat ?: other.seatHeat,
        steeringHeat = steeringHeat ?: other.steeringHeat,
        driverWindow = driverWindow ?: other.driverWindow,
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
    val seatHeat: Int? = null,
    val steeringHeat: Int? = null,
    val driverWindow: Int? = null,
)

sealed interface VehicleEvent {
    data class RecirculationChanged(val on: Boolean) : VehicleEvent
    data class PowerDownWarning(val on: Boolean) : VehicleEvent
    data object DriverDoorOpened : VehicleEvent
}
