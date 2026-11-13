package com.contestproject.app.domain

enum class NapPhase { RESTING, PRE_WAKE, WAKE }

/**
 * 진행 중인 쪽잠 한 번. 시각은 모두 System.currentTimeMillis() 기준이라
 * 앱이 죽었다 살아나도 저장된 값만으로 현재 단계를 다시 계산할 수 있다.
 */
data class NapSession(
    val startedAtMs: Long,
    val endAtMs: Long,
    val plannedMinutes: Int,
    val extensionUsed: Boolean,
    val minuteMs: Long,
) {
    val totalMs: Long get() = endAtMs - startedAtMs
    val preWakeAtMs: Long get() = endAtMs - NapPolicy.PRE_WAKE_LEAD_MIN * minuteMs
    val isDemo: Boolean get() = minuteMs != NapPolicy.MINUTE_MS

    fun phaseAt(nowMs: Long): NapPhase = when {
        nowMs >= endAtMs -> NapPhase.WAKE
        nowMs >= preWakeAtMs -> NapPhase.PRE_WAKE
        else -> NapPhase.RESTING
    }

    fun remainingMs(nowMs: Long): Long = (endAtMs - nowMs).coerceAtLeast(0)

    fun progress(nowMs: Long): Float =
        ((nowMs - startedAtMs).toFloat() / totalMs).coerceIn(0f, 1f)

    /** 실제로 쉰 시간(분). 시연 모드에서도 '15분'처럼 계획 단위로 보여 준다. */
    fun restedMinutes(nowMs: Long): Int =
        ((nowMs.coerceAtMost(endAtMs) - startedAtMs) / minuteMs).toInt()

    val canExtend: Boolean get() = !extensionUsed

    fun extend(nowMs: Long): NapSession {
        check(canExtend) { "연장은 한 번만 할 수 있다" }
        return copy(
            startedAtMs = nowMs,
            endAtMs = nowMs + NapPolicy.EXTENSION_MIN * minuteMs,
            plannedMinutes = NapPolicy.EXTENSION_MIN,
            extensionUsed = true,
        )
    }

    companion object {
        fun start(nowMs: Long, minutes: Int, minuteMs: Long = NapPolicy.MINUTE_MS): NapSession {
            require(minutes in 1..NapPolicy.MAX_DURATION_MIN) { "쪽잠은 최대 ${NapPolicy.MAX_DURATION_MIN}분" }
            return NapSession(
                startedAtMs = nowMs,
                endAtMs = nowMs + minutes * minuteMs,
                plannedMinutes = minutes,
                extensionUsed = false,
                minuteMs = minuteMs,
            )
        }
    }
}
