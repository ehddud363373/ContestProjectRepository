package com.contestproject.app.domain

import java.util.Calendar

enum class Season { SUMMER, MILD, WINTER }

/**
 * 쪽잠 중·기상 직전 두 단계의 공조 값. 기획서 3-3 표 그대로이며
 * 숫자는 팀 초기값이라 사용자 테스트 뒤 여기서만 고치면 된다.
 */
data class ClimatePlan(
    val season: Season,
    val restTempC: Float,
    val wakeTempC: Float,
    val acOn: Boolean,
    val restFanLevel: Int,
    val wakeFanLevel: Int,
    val restSeatHeatLevel: Int,
    val wakeSteeringHeat: Boolean,
)

object ClimatePlanner {
    private const val SUMMER_FROM_C = 24f
    private const val WINTER_UNDER_C = 12f

    fun seasonOf(outsideTempC: Float?, month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1): Season =
        when {
            outsideTempC != null && outsideTempC >= SUMMER_FROM_C -> Season.SUMMER
            outsideTempC != null && outsideTempC <= WINTER_UNDER_C -> Season.WINTER
            outsideTempC != null -> Season.MILD
            month in 6..9 -> Season.SUMMER
            month in 11..12 || month in 1..3 -> Season.WINTER
            else -> Season.MILD
        }

    fun planFor(season: Season): ClimatePlan = when (season) {
        Season.SUMMER -> ClimatePlan(season, 24f, 22.5f, acOn = true, restFanLevel = 2, wakeFanLevel = 4, restSeatHeatLevel = 0, wakeSteeringHeat = false)
        Season.MILD -> ClimatePlan(season, 23f, 22f, acOn = false, restFanLevel = 2, wakeFanLevel = 4, restSeatHeatLevel = 0, wakeSteeringHeat = false)
        Season.WINTER -> ClimatePlan(season, 22f, 23.5f, acOn = false, restFanLevel = 2, wakeFanLevel = 4, restSeatHeatLevel = 1, wakeSteeringHeat = true)
    }
}
