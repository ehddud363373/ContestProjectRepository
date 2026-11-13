package com.contestproject.app.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contestproject.app.R
import com.contestproject.app.domain.FreshAirMode
import com.contestproject.app.domain.GuardEvent
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapSession
import com.contestproject.app.domain.NapState
import com.contestproject.app.domain.StepStatus
import com.contestproject.app.domain.WakeStep
import com.contestproject.app.domain.WakeStepKind
import com.contestproject.app.presentation.component.CheckMark
import com.contestproject.app.presentation.component.CheckRow
import com.contestproject.app.presentation.component.CommaMark
import com.contestproject.app.presentation.component.NapProgressBar
import com.contestproject.app.presentation.component.Panel
import com.contestproject.app.presentation.component.PanelLabel
import com.contestproject.app.presentation.component.ScreenScaffold
import com.contestproject.app.presentation.component.SecondaryButton
import com.contestproject.app.presentation.component.TopLine
import com.contestproject.app.presentation.component.formatCountdown
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.rememberTimeFormatter
import com.contestproject.app.presentation.theme.Shimpyo

/** 시연 모드에서도 '15:00'처럼 실제 분 단위로 보이게 환산한다. */
internal fun displayRemainingMs(session: NapSession, now: Long): Long =
    session.remainingMs(now) * NapPolicy.MINUTE_MS / session.minuteMs

@Composable
fun RestingScreen(state: NapState.Resting, now: Long, busy: Boolean, onWakeNow: () -> Unit) {
    val formatTime = rememberTimeFormatter()
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
                right = {
                    Text(
                        stringResource(R.string.resting_until, formatTime(now), formatTime(session.endAtMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Shimpyo.colors.textSecondary,
                    )
                },
            )
        },
        main = { wide ->
            Text(stringResource(R.string.remaining), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Text(
                formatCountdown(displayRemainingMs(session, now)),
                style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = Shimpyo.colors.textPrimary,
            )
            NapProgressBar(
                progress = session.progress(now),
                description = stringResource(R.string.progress_desc, session.plannedMinutes, session.restedMinutes(now)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimelineLabel(stringResource(R.string.timeline_start, formatTime(session.startedAtMs)))
                TimelineLabel(stringResource(R.string.timeline_prewake, formatTime(session.preWakeAtMs)))
                TimelineLabel(stringResource(R.string.timeline_wake, formatTime(session.endAtMs)))
            }
        },
        side = { wide ->
            Panel(if (wide) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()) {
                PanelLabel(stringResource(R.string.guarding_title))
                CheckRow(
                    stringResource(
                        if (state.climate.freshAir == FreshAirMode.WINDOW_GAP) R.string.guard_window_gap else R.string.guard_outside_air
                    )
                )
                restClimateLine(state.climate.fanLevel, state.climate.temperatureC)?.let { CheckRow(it) }
                if (state.climate.seatHeatOn) CheckRow(stringResource(R.string.guard_seat_heat))
                CheckRow(stringResource(R.string.guard_no_warning))
                state.guardLog.lastOrNull()?.let { event ->
                    if (wide) Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(
                            when (event.kind) {
                                GuardEvent.Kind.RECIRCULATION_REVERTED -> R.string.guard_log_reverted
                                GuardEvent.Kind.WINDOW_OPENED_INSTEAD -> R.string.guard_log_window
                            },
                            formatTime(event.atMs),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Shimpyo.colors.surfaceRaised)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Shimpyo.colors.textSecondary,
                    )
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
        sideWidth = 400.dp,
    )

    if (dimmed) DimOverlay(remaining = formatCountdown(displayRemainingMs(session, now))) { dimmed = false }
}

@Composable
private fun restClimateLine(fanLevel: Int?, tempC: Float?): String? = when {
    fanLevel != null && tempC != null -> stringResource(R.string.guard_fan_temp, formatTemp(tempC))
    tempC != null -> formatTemp(tempC)
    fanLevel != null -> stringResource(R.string.guard_fan)
    else -> null
}

@Composable
private fun TimelineLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Shimpyo.colors.textSecondary)
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
fun PreWakeScreen(state: NapState.PreWake, now: Long, busy: Boolean, onWakeNow: () -> Unit) {
    val formatTime = rememberTimeFormatter()
    val session = state.session
    ScreenScaffold(
        header = {
            TopLine(
                left = {
                    CommaMark(size = 22.dp)
                    Text(stringResource(R.string.prewake_title), style = MaterialTheme.typography.bodyMedium, color = Shimpyo.colors.textPrimary)
                },
                right = {
                    Text(
                        stringResource(R.string.resting_until, formatTime(now), formatTime(session.endAtMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Shimpyo.colors.textSecondary,
                    )
                },
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
                PanelLabel(stringResource(R.string.prewake_steps_title))
                state.steps.forEachIndexed { index, step ->
                    WakeStepRow(step, formatTime(session.endAtMs))
                    if (index != state.steps.lastIndex) HorizontalDivider(color = Shimpyo.colors.border)
                }
            }
        },
        sideWidth = 460.dp,
    )
}

@Composable
private fun WakeStepRow(step: WakeStep, wakeTime: String) {
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
    val detail = when {
        active -> stringResource(R.string.step_running)
        step.status == StepStatus.SKIPPED -> stringResource(R.string.step_skipped)
        step.kind == WakeStepKind.FAN_UP -> stringResource(R.string.step_fan_detail, step.from?.toInt() ?: 0, step.to?.toInt() ?: 0)
        step.kind == WakeStepKind.TEMPERATURE -> "${formatTemp(step.from ?: 0f)} → ${formatTemp(step.to ?: 0f)}"
        step.kind == WakeStepKind.VOICE -> wakeTime
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> CheckMark(colors.check, 22.dp)
                active -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(colors.accent))
                else -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(999.dp)).background(colors.border))
            }
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = when {
                active -> colors.accentText
                done -> colors.textPrimary
                else -> colors.textSecondary
            },
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = if (active) colors.accentText else colors.textSecondary)
    }
}
