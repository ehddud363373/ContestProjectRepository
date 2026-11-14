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
    /** 한 번이라도 연장했는지 */
    val extensionUsed: Boolean,
    val minuteMs: Long,
    /** 연장 전까지 쉰 시간(분, 여러 번 연장하면 누적). 출발 확인 화면의 "쉰 시간" 합계와 최대 시간 계산에 쓴다. */
    val priorRestMinutes: Int = 0,
) {
    val totalMs: Long get() = endAtMs - startedAtMs
    /** 연장까지 합친 계획 시간(분). 깨울 때 "20분 지났어요"처럼 전체 시간을 말한다. */
    val totalPlannedMinutes: Int get() = priorRestMinutes + plannedMinutes
    val preWakeAtMs: Long get() = endAtMs - NapPolicy.PRE_WAKE_LEAD_MIN * minuteMs
    val isDemo: Boolean get() = minuteMs != NapPolicy.MINUTE_MS

    fun phaseAt(nowMs: Long): NapPhase = when {
        nowMs >= endAtMs -> NapPhase.WAKE
        nowMs >= preWakeAtMs -> NapPhase.PRE_WAKE
        else -> NapPhase.RESTING
    }

    /** 깨울 시각이 STALE_AFTER_END_MIN 넘게 지났는지(차가 오래 꺼져 있다 켜진 경우) */
    fun isStaleAt(nowMs: Long): Boolean = nowMs > endAtMs + NapPolicy.STALE_AFTER_END_MIN * minuteMs

    fun remainingMs(nowMs: Long): Long = (endAtMs - nowMs).coerceAtLeast(0)

    fun progress(nowMs: Long): Float =
        ((nowMs - startedAtMs).toFloat() / totalMs).coerceIn(0f, 1f)

    /** 실제로 쉰 시간(분). 시연 모드에서도 '15분'처럼 계획 단위로 보여 준다. */
    fun restedMinutes(nowMs: Long): Int =
        priorRestMinutes + ((nowMs.coerceAtMost(endAtMs) - startedAtMs) / minuteMs).toInt()

    /**
     * 연장 상한 계산용 "이미 쓴 시간"(분, 올림). 버림으로 계산하면 연장할 때마다 1분 미만이 버려져
     * 실제 총합이 MAX_DURATION_MIN을 넘을 수 있어서 올림으로 센다.
     */
    fun usedMinutes(nowMs: Long): Int {
        val elapsed = (nowMs.coerceAtMost(endAtMs) - startedAtMs).coerceAtLeast(0)
        return priorRestMinutes + ((elapsed + minuteMs - 1) / minuteMs).toInt()
    }

    /** 지금 연장할 수 있는 최대 길이(분). 앞서 쉰 시간과 합쳐 최대 시간을 넘지 않게 STEP 단위로 내린다. 0이면 연장 불가. */
    fun maxExtensionMinutes(nowMs: Long): Int {
        val room = NapPolicy.MAX_DURATION_MIN - usedMinutes(nowMs)
        val fit = room / NapPolicy.EXTENSION_STEP_MIN * NapPolicy.EXTENSION_STEP_MIN
        return if (fit >= NapPolicy.MIN_EXTENSION_MIN) fit else 0
    }

    /**
     * @param awakeAtMs 깬 시각. 출발 확인 화면에 머문 시간은 쉰 시간이 아니므로 상한은 깬 시각 기준으로 센다.
     */
    fun extend(nowMs: Long, minutes: Int, awakeAtMs: Long = nowMs): NapSession {
        val max = maxExtensionMinutes(awakeAtMs)
        require(minutes >= NapPolicy.MIN_EXTENSION_MIN && minutes <= max) {
            "연장은 앞서 쉰 시간과 합쳐 ${NapPolicy.MAX_DURATION_MIN}분을 넘을 수 없다(지금 최대 ${max}분)"
        }
        return copy(
            startedAtMs = nowMs,
            endAtMs = nowMs + minutes * minuteMs,
            plannedMinutes = minutes,
            extensionUsed = true,
            priorRestMinutes = usedMinutes(awakeAtMs),
        )
    }

    companion object {
        fun start(nowMs: Long, minutes: Int, minuteMs: Long = NapPolicy.MINUTE_MS): NapSession {
            require(NapPolicy.isValidDuration(minutes)) {
                "쪽잠은 ${NapPolicy.MIN_DURATION_MIN}~${NapPolicy.MAX_DURATION_MIN}분, ${NapPolicy.DURATION_STEP_MIN}분 단위"
            }
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
