package com.contestproject.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun `쪽잠은 10~30분 5분 단위만 시작할 수 있다`() {
        listOf(10, 15, 20, 25, 30).forEach { NapSession.start(0, it) }
        listOf(5, 9, 12, 31, 35).forEach { m ->
            assertThrows(IllegalArgumentException::class.java) { NapSession.start(0, m) }
        }
    }

    @Test
    fun `연장은 앞서 쉰 시간과 합쳐 30분까지, 여러 번 나눠서 할 수 있다`() {
        val s = NapSession.start(0, 15)
        assertEquals(15, s.maxExtensionMinutes(15 * minute))
        val first = s.extend(nowMs = 16 * minute, minutes = 10, awakeAtMs = 15 * minute)
        assertEquals(16 * minute, first.startedAtMs)
        assertEquals(26 * minute, first.endAtMs)
        assertEquals(25, first.totalPlannedMinutes)
        assertTrue(first.extensionUsed)
        // 15 + 10 = 25분 → 남은 5분만 더 연장할 수 있다
        assertEquals(5, first.maxExtensionMinutes(first.endAtMs))
        val second = first.extend(first.endAtMs, 5)
        assertEquals(30, second.restedMinutes(second.endAtMs))
        assertEquals(0, second.maxExtensionMinutes(second.endAtMs))
    }

    @Test
    fun `최대 시간을 넘는 연장은 막는다`() {
        val s = NapSession.start(0, 25)
        assertThrows(IllegalArgumentException::class.java) { s.extend(25 * minute, 10) }
        assertThrows(IllegalArgumentException::class.java) { NapSession.start(0, 30).extend(30 * minute, 5) }
    }

    @Test
    fun `일찍 깨면 실제로 쉰 시간만 빼고 연장할 수 있다`() {
        val s = NapSession.start(0, 20)
        // 7분째에 깸 → 7분 사용, 23분 남음 → 5분 단위로 20분까지
        assertEquals(20, s.maxExtensionMinutes(7 * minute))
        // 7분 30초에 깨면 올림으로 8분을 쓴 것으로 센다(초가 쌓여 30분을 넘지 않게)
        assertEquals(20, s.maxExtensionMinutes(7 * minute + 30_000))
        assertEquals(15, s.maxExtensionMinutes(10 * minute + 1))
    }

    @Test
    fun `출발 확인 화면에 머문 시간은 쉰 시간에 넣지 않는다`() {
        val s = NapSession.start(0, 15)
        // 7분째에 깨고 화면에서 3분 머문 뒤 연장해도 깬 시각 기준 상한(20분)이 그대로다
        val extended = s.extend(nowMs = 10 * minute, minutes = 20, awakeAtMs = 7 * minute)
        assertEquals(7, extended.priorRestMinutes)
        assertEquals(27, extended.totalPlannedMinutes)
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
