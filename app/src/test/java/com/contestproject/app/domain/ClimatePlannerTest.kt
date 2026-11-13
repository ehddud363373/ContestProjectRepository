package com.contestproject.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimatePlannerTest {

    @Test
    fun `바깥 온도로 계절을 정한다`() {
        assertEquals(Season.SUMMER, ClimatePlanner.seasonOf(31f, month = 1))
        assertEquals(Season.WINTER, ClimatePlanner.seasonOf(-2f, month = 7))
        assertEquals(Season.MILD, ClimatePlanner.seasonOf(18f, month = 1))
    }

    @Test
    fun `바깥 온도를 못 읽으면 달로 정한다`() {
        assertEquals(Season.SUMMER, ClimatePlanner.seasonOf(null, month = 8))
        assertEquals(Season.WINTER, ClimatePlanner.seasonOf(null, month = 1))
        assertEquals(Season.MILD, ClimatePlanner.seasonOf(null, month = 4))
    }

    @Test
    fun `여름엔 깰 때 시원하게, 겨울엔 따뜻하게`() {
        val summer = ClimatePlanner.planFor(Season.SUMMER)
        assertTrue(summer.acOn)
        assertTrue(summer.wakeTempC < summer.restTempC)

        val winter = ClimatePlanner.planFor(Season.WINTER)
        assertTrue(winter.wakeTempC > winter.restTempC)
        assertTrue(winter.restSeatHeatLevel > 0)
        assertTrue(winter.wakeSteeringHeat)
    }
}
