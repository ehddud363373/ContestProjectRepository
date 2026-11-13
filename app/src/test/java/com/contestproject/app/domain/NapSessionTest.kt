package com.contestproject.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapSessionTest {

    private val minute = NapPolicy.MINUTE_MS

    @Test
    fun `15분 쪽잠은 12분까지 쉬고 3분 전부터 기상 연출, 15분에 깨운다`() {
        val s = NapSession.start(nowMs = 0, minutes = 15)
        assertEquals(NapPhase.RESTING, s.phaseAt(0))
        assertEquals(NapPhase.RESTING, s.phaseAt(12 * minute - 1))
        assertEquals(NapPhase.PRE_WAKE, s.phaseAt(12 * minute))
        assertEquals(NapPhase.PRE_WAKE, s.phaseAt(15 * minute - 1))
        assertEquals(NapPhase.WAKE, s.phaseAt(15 * minute))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `최대 20분을 넘길 수 없다`() {
        NapSession.start(0, 21)
    }

    @Test
    fun `연장은 10분 한 번만`() {
        val s = NapSession.start(0, 20)
        assertTrue(s.canExtend)
        val extended = s.extend(nowMs = 21 * minute)
        assertFalse(extended.canExtend)
        assertEquals(31 * minute, extended.endAtMs)
        assertEquals(10, extended.plannedMinutes)
        // 연장 뒤 "쉰 시간"은 처음 20분 + 연장 10분 = 30분
        assertEquals(30, extended.restedMinutes(extended.endAtMs))
    }

    @Test(expected = IllegalStateException::class)
    fun `두 번째 연장은 막는다`() {
        NapSession.start(0, 15).extend(15 * minute).extend(25 * minute)
    }

    @Test
    fun `남은 시간과 진행률은 범위를 벗어나지 않는다`() {
        val s = NapSession.start(1_000, 15)
        assertEquals(0L, s.remainingMs(1_000 + 20 * minute))
        assertEquals(1f, s.progress(1_000 + 20 * minute))
        assertEquals(0f, s.progress(0))
        assertEquals(15, s.restedMinutes(1_000 + 40 * minute))
    }

    @Test
    fun `시연 모드는 1분이 4초`() {
        val s = NapSession.start(0, 15, NapPolicy.DEMO_MINUTE_MS)
        assertTrue(s.isDemo)
        assertEquals(60_000L, s.endAtMs)
        assertEquals(NapPhase.PRE_WAKE, s.phaseAt(48_000))
    }
}
