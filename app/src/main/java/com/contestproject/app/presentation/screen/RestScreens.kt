package com.contestproject.app.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contestproject.app.R
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.domain.NapMonitor
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapSession
import com.contestproject.app.domain.NapState
import com.contestproject.app.domain.StepStatus
import com.contestproject.app.domain.WakeStep
import com.contestproject.app.domain.WakeStepKind
import com.contestproject.app.presentation.component.CheckMark
import com.contestproject.app.presentation.component.CommaMark
import com.contestproject.app.presentation.component.MonitorRow
import com.contestproject.app.presentation.component.NapTimeline
import com.contestproject.app.presentation.component.Panel
import com.contestproject.app.presentation.component.ScreenScaffold
import com.contestproject.app.presentation.component.SecondaryButton
import com.contestproject.app.presentation.component.TopLine
import com.contestproject.app.presentation.component.ValueTag
import com.contestproject.app.presentation.component.formatCountdown
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.minuteMark
import com.contestproject.app.presentation.component.rememberTimeFormatter
import com.contestproject.app.presentation.theme.Shimpyo

/** 시연 모드에서도 '15:00'처럼 실제 분 단위로 보이게 환산한다. */
internal fun displayRemainingMs(session: NapSession, now: Long): Long =
    session.remainingMs(now) * NapPolicy.MINUTE_MS / session.minuteMs

/** 상단 오른쪽: "15분 쪽잠 · 6분 지남 · 오후 3:20에 깨워요" (시연 모드는 시계가 의미 없어 뺀다) */
@Composable
private fun SessionProgressText(session: NapSession, now: Long) {
    val formatTime = rememberTimeFormatter()
    val elapsed = minuteMark(now.coerceAtMost(session.endAtMs), session.startedAtMs, session.minuteMs)
    val text = buildString {
        append(stringResource(R.string.rest_header_progress, session.plannedMinutes, elapsed))
        if (!session.isDemo) append(" · ").append(stringResource(R.string.rest_header_until, formatTime(session.endAtMs)))
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Shimpyo.colors.textSecondary)
}

/** 이번 구간(시작 ~ 깨우기)의 타임라인. 차가 실제로 한 일을 점으로 찍는다. */
@Composable
private fun SessionTimeline(session: NapSession, monitor: NapMonitor, now: Long) {
    val events = monitor.guardEvents(session.startedAtMs, session.endAtMs)
    val split = (session.preWakeAtMs - session.startedAtMs).toFloat() / session.totalMs
    val prewakeMin = session.plannedMinutes - NapPolicy.PRE_WAKE_LEAD_MIN
    NapTimeline(
        progress = session.progress(now),
        split = split,
        marks = events.marks(session.startedAtMs, session.totalMs),
        labels = listOf(
            0f to stringResource(R.string.tl_start),
            split to stringResource(R.string.tl_prewake, prewakeMin),
            1f to stringResource(R.string.tl_wake, session.plannedMinutes),
        ),
        description = stringResource(
            R.string.tl_desc,
            session.plannedMinutes,
            minuteMark(now.coerceAtMost(session.endAtMs), session.startedAtMs, session.minuteMs),
            events.size,
        ),
    )
}

/** 타임라인 아래 한 줄: 가장 최근에 차가 한 일, 없으면 점검 횟수 */
@Composable
private fun LatestEventLine(session: NapSession, monitor: NapMonitor) {
    val latest = monitor.guardEvents(session.startedAtMs).lastOrNull()
    val text = when {
        latest != null -> eventText(latest, session.startedAtMs, session.minuteMs)
        monitor.readAtMs == null -> stringResource(R.string.ev_waiting)
        else -> stringResource(R.string.ev_none, monitor.record.checks)
    }
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Shimpyo.colors.surfaceRaised)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (latest != null) Shimpyo.colors.textPrimary else Shimpyo.colors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RestingScreen(
    state: NapState.Resting,
    monitor: NapMonitor,
    vehicle: VehicleStatus,
    fanMax: Int?,
    now: Long,
    busy: Boolean,
    onWakeNow: () -> Unit,
) {
    val session = state.session
    var dimmed by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = dimmed) { dimmed = false }

    ScreenScaffold(
        header = {
            TopLine(
                left = {
                    CommaMark(tint = Shimpyo.colors.accent, size = 22.dp)
                    Text(stringResource(R.string.resting_title), style = MaterialTheme.typography.bodyMedium, color = Shimpyo.colors.textPrimary)
                },
                right = { SessionProgressText(session, now) },
            )
        },
        main = { wide ->
            Text(stringResource(R.string.remaining), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Text(
                formatCountdown(displayRemainingMs(session, now)),
                style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = Shimpyo.colors.textPrimary,
            )
            SessionTimeline(session, monitor, now)
            LatestEventLine(session, monitor)
        },
        side = { wide ->
            Panel(Modifier.fillMaxWidth()) {
                MonitorHeader(monitor, now)
                Column {
                    AirRow(monitor, state.climate.freshAir)
                    RowDivider()
                    if (TempRow(monitor, vehicle)) RowDivider()
                    if (FanRow(monitor, fanMax)) RowDivider()
                    if (SeatHeatRow(monitor)) RowDivider()
                    if (CarRow(vehicle)) RowDivider()
                    NextRow(session, monitor, now)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.dim_screen), onClick = { dimmed = true }, modifier = Modifier.weight(1f))
                SecondaryButton(
                    stringResource(R.string.wake_now),
                    onClick = onWakeNow,
                    modifier = Modifier.weight(1f),
                    emphasized = true,
                    enabled = !busy,
                )
            }
            Text(
                stringResource(R.string.gear_leave_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = Shimpyo.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
        sideWidth = 440.dp,
    )

    if (dimmed) DimOverlay(remaining = formatCountdown(displayRemainingMs(session, now))) { dimmed = false }
}

/** "4분 뒤 깨울 준비 · 바람 4단 · 22.5℃ · 김서림 제거" (예정) */
@Composable
private fun NextRow(session: NapSession, monitor: NapMonitor, now: Long) {
    val leftMin = ((session.preWakeAtMs - now).coerceAtLeast(0) + session.minuteMs - 1) / session.minuteMs
    val plan = monitor.wakePlan
    val detail = listOfNotNull(
        plan.fanSpeed?.let { stringResource(R.string.val_fan, it) },
        plan.temperatureC?.let { formatTemp(it) },
        plan.defroster?.let { stringResource(R.string.detail_defrost) },
        plan.steeringHeat?.let { stringResource(R.string.detail_steering) },
    ).joinToString(" · ").ifEmpty { null }
    MonitorRow(
        stringResource(R.string.row_next),
        stringResource(R.string.val_next_prewake, leftMin.toInt()),
        detail,
        ValueTag.PLANNED,
        valueColor = Shimpyo.colors.accentText,
    )
}

/** 차량 화면은 앱이 끌 수 없어서, 거의 검은 화면으로 덮고 남은 시간만 흐리게 둔다. 아무 데나 누르면 돌아온다. */
@Composable
private fun DimOverlay(remaining: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(remaining, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A414C))
            Text(stringResource(R.string.dim_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E343D))
        }
    }
}

@Composable
fun PreWakeScreen(state: NapState.PreWake, monitor: NapMonitor, now: Long, busy: Boolean, onWakeNow: () -> Unit) {
    val session = state.session
    ScreenScaffold(
        header = {
            TopLine(
                left = {
                    CommaMark(size = 22.dp)
                    Text(stringResource(R.string.prewake_title), style = MaterialTheme.typography.bodyMedium, color = Shimpyo.colors.textPrimary)
                },
                right = { SessionProgressText(session, now) },
            )
        },
        main = { wide ->
            Text(
                stringResource(R.string.prewake_headline),
                style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                color = Shimpyo.colors.textPrimary,
            )
            Text(stringResource(R.string.prewake_body), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Text(
                formatCountdown(displayRemainingMs(session, now)),
                style = MaterialTheme.typography.displayMedium,
                color = Shimpyo.colors.accentText,
            )
            SessionTimeline(session, monitor, now)
            SecondaryButton(
                stringResource(R.string.prewake_get_up),
                onClick = onWakeNow,
                modifier = Modifier.widthIn(min = 360.dp),
                height = 72.dp,
                emphasized = true,
                enabled = !busy,
            )
        },
        side = { _ ->
            Panel(Modifier.fillMaxWidth()) {
                MonitorHeader(monitor, now, stringResource(R.string.prewake_steps_title))
                Column {
                    state.steps.forEachIndexed { index, step ->
                        WakeStepRow(step, session, monitor, now)
                        if (index != state.steps.lastIndex) RowDivider()
                    }
                }
            }
        },
        sideWidth = 460.dp,
    )
}

/**
 * 깨우는 순서 한 줄. 바람·온도는 바뀐 값(2단 → 4단)과 적용 시각을,
 * 김서림 제거는 몇 분째 작동 중인지를, 음성은 남은 시간을 보여 준다.
 */
@Composable
private fun WakeStepRow(step: WakeStep, session: NapSession, monitor: NapMonitor, now: Long) {
    val colors = Shimpyo.colors
    val active = step.status == StepStatus.RUNNING
    val done = step.status == StepStatus.DONE
    val label = stringResource(
        when (step.kind) {
            WakeStepKind.FAN_UP -> R.string.step_fan
            WakeStepKind.TEMPERATURE -> if ((step.to ?: 0f) < (step.from ?: 0f)) R.string.step_temp_down else R.string.step_temp_up
            WakeStepKind.DEFROST -> R.string.step_defrost
            WakeStepKind.STEERING_HEAT -> R.string.step_steering
            WakeStepKind.VOICE -> R.string.step_voice
        }
    )
    val value = when (step.kind) {
        WakeStepKind.FAN_UP -> stringResource(R.string.step_fan_detail, step.from?.toInt() ?: 0, step.to?.toInt() ?: 0)
        WakeStepKind.TEMPERATURE -> "${formatTemp(step.from ?: 0f)} → ${formatTemp(step.to ?: 0f)}"
        else -> null
    }
    val detail = when {
        step.status == StepStatus.SKIPPED -> stringResource(R.string.step_skipped)
        step.kind == WakeStepKind.DEFROST && active ->
            monitor.record.defrostRunMs(now)?.let { stringResource(R.string.detail_running_for, durationText(it, session.minuteMs)) }
                ?: stringResource(R.string.step_running)
        step.kind == WakeStepKind.VOICE ->
            stringResource(R.string.prewake_until_wake, formatCountdown(displayRemainingMs(session, now)))
        active -> stringResource(R.string.step_running)
        done && step.doneAtMs != null ->
            stringResource(R.string.step_done_at, minuteMark(step.doneAtMs, session.startedAtMs, session.minuteMs))
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> CheckMark(colors.check, 24.dp)
                active -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
                else -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(colors.border))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    active -> colors.accentText
                    done -> colors.textPrimary
                    else -> colors.textSecondary
                },
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (active) colors.accentText else colors.textSecondary) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary) }
    }
}
