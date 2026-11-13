package com.contestproject.app.domain

import com.contestproject.app.data.vehicle.ClimateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NapRecordTest {

    @Test
    fun `저장 문자열로 바꿨다가 다시 읽으면 같다`() {
        val record = NapRecord(checks = 12, freshAirOkChecks = 11, defrostStartedAtMs = 500, defrostEndedAtMs = null)
            .with(NapEvent(0, NapEvent.Kind.STARTED))
            .with(NapEvent(120, NapEvent.Kind.CORRECTED, ClimateItem.FAN))
            .with(NapEvent(300, NapEvent.Kind.WAKE_CALL))
        assertEquals(record, NapRecord.decode(record.encode()))
    }

    @Test
    fun `깨진 문자열은 읽지 않는다`() {
        assertNull(NapRecord.decode(""))
        assertNull(NapRecord.decode("abc"))
        val partial = NapRecord.decode("3|2|||10,STARTED,;bad;20,UNKNOWN,")
        assertEquals(1, partial?.events?.size)
    }

    @Test
    fun `연장하면 깨운 횟수를 새로 센다`() {
        val record = NapRecord()
            .with(NapEvent(0, NapEvent.Kind.STARTED))
            .with(NapEvent(1, NapEvent.Kind.WAKE_CALL))
            .with(NapEvent(2, NapEvent.Kind.WAKE_CALL))
            .with(NapEvent(3, NapEvent.Kind.EXTENDED))
            .with(NapEvent(4, NapEvent.Kind.WAKE_CALL))
        assertEquals(1, record.wakeCallsLastRound)
    }

    @Test
    fun `읽지 못한 값은 비교하지 않고 다른 값만 고른다`() {
        val target = ClimateSnapshot(recirculation = false, temperatureC = 24f, fanSpeed = 2, ac = true)
        val reading = ClimateSnapshot(recirculation = true, temperatureC = 24.1f, fanSpeed = null, ac = false)
        assertEquals(listOf(ClimateItem.AIR, ClimateItem.AC), ClimateCheck.mismatches(target, reading))
    }
}
