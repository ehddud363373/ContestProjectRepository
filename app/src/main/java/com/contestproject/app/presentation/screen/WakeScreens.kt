package com.contestproject.app.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contestproject.app.R
import com.contestproject.app.data.vehicle.ClimateSnapshot
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.domain.AlertReason
import com.contestproject.app.domain.NapEvent
import com.contestproject.app.domain.NapMonitor
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapState
import com.contestproject.app.presentation.component.CallDots
import com.contestproject.app.presentation.component.MinuteStepper
import com.contestproject.app.presentation.component.MonitorRow
import com.contestproject.app.presentation.component.NapTimeline
import com.contestproject.app.presentation.component.Panel
import com.contestproject.app.presentation.component.PanelLabel
import com.contestproject.app.presentation.component.PrimaryButton
import com.contestproject.app.presentation.component.ScreenScaffold
import com.contestproject.app.presentation.component.SecondaryButton
import com.contestproject.app.presentation.component.StatTile
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.minuteMark
import com.contestproject.app.presentation.component.rememberTimeFormatter
import com.contestproject.app.presentation.theme.Shimpyo

@Composable
fun WakingScreen(
    state: NapState.Waking,
    monitor: NapMonitor,
    vehicle: VehicleStatus,
    fanMax: Int?,
    now: Long,
    onAwake: () -> Unit,
) {
    val formatTime = rememberTimeFormatter()
    val nextCallSec = ((state.lastCallAtMs + state.callIntervalMs - now) / 1000).coerceAtLeast(0).toInt()
    ScreenScaffold(
        header = {},
        main = { wide ->
            Text(formatTime(now), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Text(
                stringResource(R.string.wake_headline, state.session.totalPlannedMinutes),
                style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                color = Shimpyo.colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CallDots(called = state.repeats + 1)
                Text(
                    stringResource(R.string.wake_call_count, state.repeats + 1, nextCallSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Shimpyo.colors.textSecondary,
                )
            }
            PrimaryButton(stringResource(R.string.wake_awake), onClick = onAwake)
            Text(
                stringResource(R.string.wake_call_alert_note, NapPolicy.VOICE_REPEATS_BEFORE_ALERT),
                style = MaterialTheme.typography.bodySmall,
                color = Shimpyo.colors.textSecondary,
            )
        },
        side = { _ ->
            Panel(Modifier.fillMaxWidth()) {
                MonitorHeader(monitor, now, stringResource(R.string.wake_car_ready))
                Column {
                    if (DefrostRow(monitor, state.session, now)) RowDivider()
                    if (TempRow(monitor, vehicle)) RowDivider()
                    if (FanRow(monitor, fanMax)) RowDivider()
                    AirRow(monitor, state.freshAir)
                }
            }
        },
        sideWidth = 440.dp,
    )
}

@Composable
fun DepartScreen(
    state: NapState.DepartCheck,
    monitor: NapMonitor,
    vehicle: VehicleStatus,
    now: Long,
    busy: Boolean,
    onExtend: (Int) -> Unit,
    onDepart: () -> Unit,
) {
    ScreenScaffold(
        header = {},
        main = { _ ->
            Text(stringResource(R.string.depart_headline), style = MaterialTheme.typography.headlineLarge, color = Shimpyo.colors.textPrimary)
            Text(stringResource(R.string.depart_body), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                ExtendControl(state, busy, onExtend, Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = stringResource(R.string.depart_go),
                        onClick = onDepart,
                        // 연장 시간 고르기가 있으면 왼쪽 묶음(고르기 + 연장 버튼)과 높이를 맞춘다.
                        height = if (state.maxExtensionMin > 0) EXTEND_STEPPER_HEIGHT + EXTEND_GAP + EXTEND_BUTTON_HEIGHT else EXTEND_BUTTON_HEIGHT,
                        loading = busy,
                    )
                }
            }
            RestorePreview(monitor)
        },
        side = { _ -> NapReport(state, monitor, vehicle, now) },
        sideWidth = 460.dp,
    )
}

/**
 * 연장: 앞서 쉰 시간과 합쳐 최대 시간을 넘지 않는 안에서 1분 단위로(최소 5분) 고른다.
 * 고를 수 있는 끝(maxExtensionMin)은 컨트롤러가 깬 시각 기준으로 정해 둔 값이라 화면에 머무는 동안 바뀌지 않는다.
 */
@Composable
private fun ExtendControl(state: NapState.DepartCheck, busy: Boolean, onExtend: (Int) -> Unit, modifier: Modifier) {
    val max = state.maxExtensionMin
    val step = NapPolicy.EXTENSION_STEP_MIN
    val min = NapPolicy.MIN_EXTENSION_MIN
    var minutes by rememberSaveable(max) { mutableIntStateOf(NapPolicy.DEFAULT_EXTENSION_MIN.coerceIn(min, max.coerceAtLeast(min))) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(EXTEND_GAP)) {
        if (max > 0) {
            MinuteStepper(
                value = stringResource(R.string.minutes, minutes),
                onMinus = { minutes -= step },
                onPlus = { minutes += step },
                minusEnabled = minutes - step >= min,
                plusEnabled = minutes + step <= max,
                minusDescription = stringResource(R.string.stepper_less, step),
                plusDescription = stringResource(R.string.stepper_more, step),
                height = EXTEND_STEPPER_HEIGHT,
            )
        }
        val subtitle = when {
            max > 0 -> stringResource(R.string.depart_extend_total, state.restedMinutes + minutes, NapPolicy.MAX_DURATION_MIN)
            // 최대 시간이 남았는데도 막혔다면 차량 경고 때문이다.
            state.session.maxExtensionMinutes(state.awakeAtMs) > 0 -> stringResource(R.string.depart_extend_blocked)
            else -> stringResource(R.string.depart_extend_full, NapPolicy.MAX_DURATION_MIN)
        }
        SecondaryButton(
            text = if (max > 0) stringResource(R.string.depart_extend, minutes) else stringResource(R.string.depart_extend_none),
            subtitle = subtitle,
            onClick = { onExtend(minutes) },
            enabled = max > 0 && !busy,
            modifier = Modifier.fillMaxWidth(),
            height = EXTEND_BUTTON_HEIGHT,
            large = true,
        )
    }
}

private val EXTEND_STEPPER_HEIGHT = 64.dp
private val EXTEND_GAP = 10.dp
private val EXTEND_BUTTON_HEIGHT = 96.dp

/** "출발을 누르면 되돌리는 값": 지금 값(차에서 읽은 값) → 쪽잠 전 값. 달라진 항목만. */
@Composable
private fun RestorePreview(monitor: NapMonitor) {
    val now = monitor.reading ?: monitor.target
    val before = monitor.original
    val res = LocalContext.current.resources
    val on = stringResource(R.string.val_on)
    val off = stringResource(R.string.val_off)
    val recirc = stringResource(R.string.val_recirculation)
    val outside = stringResource(R.string.val_outside_air)
    val closed = stringResource(R.string.val_window_closed)
    val rows = buildList {
        fun <T> add(label: String, current: T?, original: T?, text: (T) -> String) {
            if (current != null && original != null && current != original) add(Triple(label, text(current), text(original)))
        }
        add(stringResource(R.string.row_temp), now.temperatureC, before.temperatureC) { formatTemp(it) }
        add(stringResource(R.string.row_fan), now.fanSpeed, before.fanSpeed) { res.getString(R.string.val_fan, it) }
        add(stringResource(R.string.row_air), now.recirculation, before.recirculation) { if (it) recirc else outside }
        add(stringResource(R.string.item_ac), now.ac, before.ac) { if (it) on else off }
        add(stringResource(R.string.row_defrost), now.defroster, before.defroster) { if (it) on else off }
        add(stringResource(R.string.item_rear_defrost), now.rearDefroster, before.rearDefroster) { if (it) on else off }
        add(stringResource(R.string.item_steering), now.steeringHeat, before.steeringHeat) { if (it > 0) res.getString(R.string.val_level, it) else off }
        add(stringResource(R.string.row_heat), now.seatHeat, before.seatHeat) { res.getString(R.string.val_seat_heat, it) }
        add(stringResource(R.string.item_window), now.driverWindow, before.driverWindow) { if (it > 0) res.getString(R.string.val_window_gap, it) else closed }
    }
    val colors = Shimpyo.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PanelLabel(stringResource(R.string.depart_restore_title))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.depart_restore_none), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        rows.forEach { (label, current, original) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$current →", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Text(original, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
        }
    }
}

/** 이번 쪽잠 리포트: 숫자 3칸 + 전체 타임라인 + 김서림·깨운 횟수·차량 경고 */
@Composable
private fun NapReport(state: NapState.DepartCheck, monitor: NapMonitor, vehicle: VehicleStatus, now: Long) {
    val record = monitor.record
    val session = state.session
    val start = record.startedAtMs ?: session.startedAtMs
    val total = (now - start).coerceAtLeast(1)
    val guard = monitor.guardEvents(start)
    val extendedAt = record.events.lastOrNull { it.kind == NapEvent.Kind.EXTENDED }?.atMs
    val prewakeAt = record.events.lastOrNull { it.kind == NapEvent.Kind.PRE_WAKE }?.atMs

    Panel(Modifier.fillMaxWidth()) {
        PanelLabel(stringResource(R.string.depart_summary_title))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.stat_rested), stringResource(R.string.minutes, state.restedMinutes), Modifier.weight(1f))
            StatTile(
                stringResource(R.string.stat_air),
                if (record.checks > 0) stringResource(R.string.val_ratio, record.freshAirOkChecks, record.checks) else "–",
                Modifier.weight(1.2f),
            )
            StatTile(stringResource(R.string.stat_fixed), stringResource(R.string.val_times, guard.size), Modifier.weight(1f))
        }
        NapTimeline(
            progress = 1f,
            split = prewakeAt?.let { (it - start).toFloat() / total } ?: 1f,
            marks = guard.marks(start, total),
            labels = listOfNotNull(
                0f to stringResource(R.string.tl_start),
                prewakeAt?.takeIf { extendedAt == null }?.let {
                    ((it - start).toFloat() / total) to stringResource(R.string.tl_prewake, minuteMark(it, start, session.minuteMs))
                },
                extendedAt?.let { ((it - start).toFloat() / total) to stringResource(R.string.tl_extended, minuteMark(it, start, session.minuteMs)) },
                1f to stringResource(R.string.tl_now, state.restedMinutes),
            ),
            description = stringResource(R.string.tl_desc, state.restedMinutes, state.restedMinutes, guard.size),
        )
        Column {
            record.defrostRunMs(now)?.let { ms ->
                MonitorRow(stringResource(R.string.report_defrost), stringResource(R.string.detail_ran_for, durationText(ms, session.minuteMs)), labelWidth = REPORT_LABEL_WIDTH)
                HorizontalDivider(color = Shimpyo.colors.border)
            }
            val calls = record.wakeCallsLastRound
            MonitorRow(
                stringResource(R.string.report_wake),
                if (calls > 0) stringResource(R.string.report_woke_at, calls) else stringResource(R.string.report_woke_early),
                labelWidth = REPORT_LABEL_WIDTH,
            )
            vehicle.vehicleWarning?.let { warn ->
                HorizontalDivider(color = Shimpyo.colors.border)
                MonitorRow(
                    stringResource(R.string.report_warning),
                    stringResource(if (warn) R.string.car_warning else R.string.car_no_warning),
                    valueColor = if (warn) Shimpyo.colors.cautionTitle else Shimpyo.colors.textPrimary,
                    labelWidth = REPORT_LABEL_WIDTH,
                )
            }
        }
    }
}

private val REPORT_LABEL_WIDTH = 150.dp

@Composable
fun AlertScreen(state: NapState.Alert, now: Long, onAwake: () -> Unit, onSnooze: () -> Unit) {
    val colors = Shimpyo.colors
    val snoozed = state.snoozedUntilMs?.let { now < it } == true
    ScreenScaffold(
        header = {
            Text(
                stringResource(if (snoozed) R.string.alert_snoozed else R.string.alert_repeating),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        main = { wide ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)).background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.accent, modifier = Modifier.size(50.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(alertTitle(state.reason)),
                        style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(stringResource(alertBody(state.reason)), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(2f)) {
                    PrimaryButton(stringResource(R.string.alert_awake), onClick = onAwake, height = 88.dp)
                }
                SecondaryButton(
                    stringResource(R.string.alert_snooze),
                    onClick = onSnooze,
                    enabled = !snoozed,
                    modifier = Modifier.weight(1f),
                    height = 88.dp,
                )
            }
        },
        side = { _ ->
            Panel(Modifier.fillMaxWidth(), container = colors.surface, border = colors.border) {
                PanelLabel(stringResource(R.string.alert_also_title), color = colors.textSecondary)
                val also = listOf(
                    R.string.alert_also_warning, R.string.alert_also_air, R.string.alert_also_climate,
                    R.string.alert_also_gear, R.string.alert_also_door,
                )
                also.forEachIndexed { index, res ->
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        if (index < also.lastIndex) HorizontalDivider(color = colors.border)
                    }
                Text(
                    stringResource(R.string.alert_co_note),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
            }
        },
        sideWidth = 400.dp,
    )
}

private fun alertTitle(reason: AlertReason) = when (reason) {
    AlertReason.VEHICLE_WARNING -> R.string.alert_vehicle_title
    AlertReason.FRESH_AIR_LOST -> R.string.alert_air_title
    AlertReason.NO_RESPONSE -> R.string.alert_no_response_title
    AlertReason.CLIMATE_OFF -> R.string.alert_climate_off_title
    AlertReason.RAIN -> R.string.alert_rain_title
}

private fun alertBody(reason: AlertReason) = when (reason) {
    AlertReason.VEHICLE_WARNING -> R.string.alert_vehicle_body
    AlertReason.FRESH_AIR_LOST -> R.string.alert_air_body
    AlertReason.NO_RESPONSE -> R.string.alert_no_response_body
    AlertReason.CLIMATE_OFF -> R.string.alert_climate_off_body
    AlertReason.RAIN -> R.string.alert_rain_body
}
