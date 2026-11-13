package com.contestproject.app.domain

/** 쪽잠 중 바깥 공기를 어떻게 지키고 있는지 */
enum class FreshAirMode { OUTSIDE_AIR, WINDOW_GAP, NONE }

data class RestClimate(
    val freshAir: FreshAirMode,
    val temperatureC: Float?,
    val fanLevel: Int?,
    val seatHeatOn: Boolean,
)

data class GuardEvent(val atMs: Long, val kind: Kind) {
    enum class Kind { RECIRCULATION_REVERTED, WINDOW_OPENED_INSTEAD }
}

enum class WakeStepKind { FAN_UP, TEMPERATURE, DEFROST, STEERING_HEAT, VOICE }
enum class StepStatus { PENDING, RUNNING, DONE, SKIPPED }

data class WakeStep(val kind: WakeStepKind, val status: StepStatus, val from: Float? = null, val to: Float? = null)

enum class AlertReason { VEHICLE_WARNING, FRESH_AIR_LOST, NO_RESPONSE }

sealed interface NapState {
    data object Idle : NapState

    sealed interface Active : NapState {
        val session: NapSession
    }

    data class Resting(
        override val session: NapSession,
        val climate: RestClimate,
        val guardLog: List<GuardEvent>,
    ) : Active

    data class PreWake(
        override val session: NapSession,
        val climate: RestClimate,
        val steps: List<WakeStep>,
    ) : Active

    data class Waking(
        override val session: NapSession,
        val repeats: Int,
        val defrosted: Boolean,
        val tempC: Float?,
        val fanLevel: Int?,
        val freshAir: FreshAirMode,
    ) : Active

    data class DepartCheck(
        override val session: NapSession,
        val restedMinutes: Int,
        val recirculationReverts: Int,
        val defrosted: Boolean,
        val canExtend: Boolean,
    ) : Active

    data class Alert(
        override val session: NapSession,
        val reason: AlertReason,
        val snoozedUntilMs: Long?,
    ) : Active
}

/** 화면 위에 잠깐 띄우는 안내 */
enum class NapNotice { ENDED_BY_GEAR, RESUMED_AFTER_RESTART, START_FAILED, DEPARTED }
