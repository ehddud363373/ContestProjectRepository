package com.contestproject.app.presentation.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import com.contestproject.app.R
import com.contestproject.app.data.vehicle.GearState
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.domain.ClimateItem
import com.contestproject.app.domain.FreshAirMode
import com.contestproject.app.domain.NapEvent
import com.contestproject.app.domain.NapMonitor
import com.contestproject.app.domain.NapSession
import com.contestproject.app.presentation.component.MonitorRow
import com.contestproject.app.presentation.component.PanelLabel
import com.contestproject.app.presentation.component.TimelineMark
import com.contestproject.app.presentation.component.ValueTag
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.minuteMark
import com.contestproject.app.presentation.component.scaledDuration
import com.contestproject.app.presentation.theme.Shimpyo

/*
 * 쪽잠 중 화면의 숫자는 모두 여기서 만든다. 원칙:
 *  - 차에서 다시 읽은 값이 있으면 그 값(측정), 없으면 앱이 보낸 값(설정), 앞으로 바꿀 값은 예정.
 *  - 둘 다 없으면 그 줄을 보여 주지 않는다(추측한 숫자를 띄우지 않는다).
 */

/** 패널 제목 줄: "지금 차 상태 · 8초 전 확인" */
@Composable
internal fun MonitorHeader(monitor: NapMonitor, now: Long, title: String = stringResource(R.string.monitor_title)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PanelLabel(title)
        val readAt = monitor.readAtMs
        Text(
            if (readAt == null) stringResource(R.string.monitor_checking)
            else stringResource(R.string.monitor_checked_ago, ((now - readAt) / 1000).coerceAtLeast(0).toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = if (readAt == null) Shimpyo.colors.textSecondary else Shimpyo.colors.check,
        )
    }
}

@Composable
internal fun RowDivider() = HorizontalDivider(color = Shimpyo.colors.border)

@Composable
internal fun AirRow(monitor: NapMonitor, mode: FreshAirMode) {
    val reading = monitor.reading
    val record = monitor.record
    val reverts = record.count(NapEvent.Kind.AIR_REVERTED) + record.count(NapEvent.Kind.WINDOW_OPENED)
    val detail = stringResource(R.string.detail_air_checks, record.checks, record.freshAirOkChecks, reverts)
    when (mode) {
        FreshAirMode.OUTSIDE_AIR -> {
            val recirc = reading?.recirculation
            MonitorRow(
                stringResource(R.string.row_air),
                stringResource(if (recirc == true) R.string.val_recirculation else R.string.val_outside_air),
                detail,
                if (recirc != null) ValueTag.MEASURED else ValueTag.SET,
                valueColor = if (recirc == true) Shimpyo.colors.cautionTitle else Shimpyo.colors.textPrimary,
            )
        }
        FreshAirMode.WINDOW_GAP -> {
            val pos = reading?.driverWindow ?: monitor.target.driverWindow
            MonitorRow(
                stringResource(R.string.row_air),
                if (pos != null && pos > 0) stringResource(R.string.val_window_gap, pos) else stringResource(R.string.val_window_closed),
                detail,
                if (reading?.driverWindow != null) ValueTag.MEASURED else ValueTag.SET,
            )
        }
        FreshAirMode.NONE -> Unit
    }
}

/** 설정 온도(차가 알려 준 값) + 실내 실제 온도 + 바깥 온도 */
@Composable
internal fun TempRow(monitor: NapMonitor, vehicle: VehicleStatus): Boolean {
    val set = monitor.reading?.temperatureC ?: monitor.target.temperatureC ?: return false
    val detail = listOfNotNull(
        monitor.cabinTempC?.let { stringResource(R.string.detail_cabin, formatTemp(it)) },
        vehicle.outsideTempC?.let { stringResource(R.string.detail_outside, formatTemp(it)) },
    ).joinToString(" · ").ifEmpty { null }
    MonitorRow(
        stringResource(R.string.row_temp),
        stringResource(R.string.val_set_temp, formatTemp(set)),
        detail,
        if (monitor.reading?.temperatureC != null) ValueTag.MEASURED else ValueTag.SET,
    )
    return true
}

@Composable
internal fun FanRow(monitor: NapMonitor, fanMax: Int?): Boolean {
    val level = monitor.reading?.fanSpeed ?: monitor.target.fanSpeed ?: return false
    val ac = monitor.reading?.ac ?: monitor.target.ac
    MonitorRow(
        stringResource(R.string.row_fan),
        if (fanMax != null) stringResource(R.string.val_fan_of, level, fanMax) else stringResource(R.string.val_fan, level),
        ac?.let { stringResource(if (it) R.string.detail_ac_on else R.string.detail_ac_off) },
        if (monitor.reading?.fanSpeed != null) ValueTag.MEASURED else ValueTag.SET,
    )
    return true
}

@Composable
internal fun SeatHeatRow(monitor: NapMonitor): Boolean {
    if (monitor.target.seatHeat == null) return false
    val level = monitor.reading?.seatHeat ?: monitor.target.seatHeat
    MonitorRow(
        stringResource(R.string.row_heat),
        stringResource(R.string.val_seat_heat, level),
        tag = if (monitor.reading?.seatHeat != null) ValueTag.MEASURED else ValueTag.SET,
    )
    return true
}

/** 기어·운전석 문·차량 경고. 차가 알려 준 항목만 이어 붙인다. */
@Composable
internal fun CarRow(vehicle: VehicleStatus): Boolean {
    val parts = listOfNotNull(
        when (vehicle.gear) {
            GearState.PARK -> stringResource(R.string.car_park)
            GearState.NOT_PARK -> stringResource(R.string.car_not_park)
            GearState.UNKNOWN -> null
        },
        vehicle.driverDoorOpen?.let { stringResource(if (it) R.string.car_door_open else R.string.car_door_closed) },
        vehicle.vehicleWarning?.let { stringResource(if (it) R.string.car_warning else R.string.car_no_warning) },
    )
    if (parts.isEmpty()) return false
    MonitorRow(
        stringResource(R.string.row_car),
        parts.joinToString(" · "),
        tag = ValueTag.MEASURED,
        valueColor = if (vehicle.vehicleWarning == true) Shimpyo.colors.cautionTitle else Shimpyo.colors.textPrimary,
    )
    return true
}

@Composable
internal fun DefrostRow(monitor: NapMonitor, session: NapSession, now: Long): Boolean {
    val on = monitor.reading?.defroster ?: monitor.target.defroster ?: return false
    if (!on) return false
    val detail = monitor.record.defrostRunMs(now)?.let { stringResource(R.string.detail_running_for, durationText(it, session.minuteMs)) }
    MonitorRow(
        stringResource(R.string.row_defrost),
        stringResource(R.string.val_on),
        detail,
        if (monitor.reading?.defroster != null) ValueTag.MEASURED else ValueTag.SET,
    )
    return true
}

@Composable
internal fun durationText(ms: Long, minuteMs: Long): String {
    val (m, s) = scaledDuration(ms, minuteMs)
    return when {
        m == 0 -> stringResource(R.string.duration_sec, s)
        s == 0 -> stringResource(R.string.duration_min, m)
        else -> stringResource(R.string.duration_min_sec, m, s)
    }
}

@Composable
internal fun itemName(item: ClimateItem?): String = stringResource(
    when (item) {
        ClimateItem.POWER -> R.string.item_power
        ClimateItem.AIR, null -> R.string.item_air
        ClimateItem.AC -> R.string.item_ac
        ClimateItem.TEMPERATURE -> R.string.item_temp
        ClimateItem.FAN -> R.string.item_fan
        ClimateItem.DEFROST -> R.string.item_defrost
        ClimateItem.SEAT_HEAT -> R.string.item_seat
        ClimateItem.WINDOW -> R.string.item_window
        ClimateItem.REAR_DEFROST -> R.string.item_rear_defrost
        ClimateItem.STEERING_HEAT -> R.string.item_steering
    }
)

/** 차가 스스로 지켜 낸 일(되돌림·창문·다시 맞춤)만 고른다. */
internal fun NapMonitor.guardEvents(fromMs: Long, toMs: Long = Long.MAX_VALUE): List<NapEvent> =
    record.events.filter {
        it.atMs in fromMs..toMs &&
            (it.kind == NapEvent.Kind.AIR_REVERTED || it.kind == NapEvent.Kind.WINDOW_OPENED || it.kind == NapEvent.Kind.CORRECTED)
    }

internal fun List<NapEvent>.marks(startMs: Long, totalMs: Long) =
    map { TimelineMark(((it.atMs - startMs).toFloat() / totalMs).coerceIn(0f, 1f)) }

/** "3분 · 내기순환이 켜져서 바깥 공기로 되돌렸어요" */
@Composable
internal fun eventText(event: NapEvent, startMs: Long, minuteMs: Long): String {
    val m = minuteMark(event.atMs, startMs, minuteMs)
    return when (event.kind) {
        NapEvent.Kind.AIR_REVERTED -> stringResource(R.string.ev_air_reverted, m)
        NapEvent.Kind.WINDOW_OPENED -> stringResource(R.string.ev_window_opened, m)
        else -> stringResource(R.string.ev_corrected, m, itemName(event.item))
    }
}
