package com.contestproject.app.presentation.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contestproject.app.R
import com.contestproject.app.domain.AlertReason
import com.contestproject.app.domain.FreshAirMode
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapState
import com.contestproject.app.presentation.component.Panel
import com.contestproject.app.presentation.component.PanelLabel
import com.contestproject.app.presentation.component.PrimaryButton
import com.contestproject.app.presentation.component.ScreenScaffold
import com.contestproject.app.presentation.component.SecondaryButton
import com.contestproject.app.presentation.component.SummaryRow
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.rememberTimeFormatter
import com.contestproject.app.presentation.theme.Shimpyo

@Composable
fun WakingScreen(state: NapState.Waking, now: Long, onAwake: () -> Unit) {
    val formatTime = rememberTimeFormatter()
    ScreenScaffold(
        header = {},
        main = { wide ->
            Text(formatTime(now), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Text(
                stringResource(R.string.wake_headline, state.session.plannedMinutes),
                style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                color = Shimpyo.colors.textPrimary,
            )
            Text(
                stringResource(if (state.repeats == 0) R.string.wake_voice_now else R.string.wake_voice_repeat, state.repeats + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = Shimpyo.colors.textSecondary,
            )
            PrimaryButton(stringResource(R.string.wake_awake), onClick = onAwake)
            Text(
                stringResource(R.string.wake_repeat_note, NapPolicy.VOICE_REPEAT_SEC),
                style = MaterialTheme.typography.bodySmall,
                color = Shimpyo.colors.textSecondary,
            )
        },
        side = { _ ->
            PanelLabel(stringResource(R.string.wake_car_ready))
            if (state.defrosted) ReadyItem(stringResource(R.string.wake_defrost_done))
            val climate = listOfNotNull(
                state.tempC?.let { stringResource(R.string.wake_cabin_temp, formatTemp(it)) },
                state.fanLevel?.let { stringResource(R.string.wake_fan_level, it) },
            ).joinToString(" · ")
            if (climate.isNotEmpty()) ReadyItem(climate)
            ReadyItem(
                stringResource(if (state.freshAir == FreshAirMode.WINDOW_GAP) R.string.guard_window_gap else R.string.guard_outside_air)
            )
        },
        sideWidth = 420.dp,
    )
}

@Composable
private fun ReadyItem(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Shimpyo.colors.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Shimpyo.colors.textPrimary,
    )
}

@Composable
fun DepartScreen(state: NapState.DepartCheck, busy: Boolean, onExtend: () -> Unit, onDepart: () -> Unit) {
    ScreenScaffold(
        header = {},
        main = { _ ->
            Text(stringResource(R.string.depart_headline), style = MaterialTheme.typography.headlineLarge, color = Shimpyo.colors.textPrimary)
            Text(stringResource(R.string.depart_body), style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.depart_extend, NapPolicy.EXTENSION_MIN),
                    subtitle = stringResource(if (state.canExtend) R.string.depart_extend_once else R.string.depart_extend_used),
                    onClick = onExtend,
                    enabled = state.canExtend && !busy,
                    modifier = Modifier.weight(1f),
                    height = 128.dp,
                    large = true,
                )
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = stringResource(R.string.depart_go),
                        onClick = onDepart,
                        height = 128.dp,
                        loading = busy,
                    )
                }
            }
            Text(stringResource(R.string.depart_go_note), style = MaterialTheme.typography.bodySmall, color = Shimpyo.colors.textSecondary)
        },
        side = { _ ->
            PanelLabel(stringResource(R.string.depart_summary_title))
            SummaryRow(stringResource(R.string.depart_rested), stringResource(R.string.minutes, state.restedMinutes))
            SummaryRow(
                stringResource(R.string.depart_air),
                if (state.recirculationReverts > 0) stringResource(R.string.depart_air_reverted, state.recirculationReverts)
                else stringResource(R.string.depart_air_kept),
            )
            if (state.defrosted) SummaryRow(stringResource(R.string.depart_windshield), stringResource(R.string.depart_windshield_clear))
            Text(
                stringResource(R.string.depart_coffee_tip),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Shimpyo.colors.surfaceRaised)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Shimpyo.colors.accentText,
            )
        },
        sideWidth = 420.dp,
    )
}

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
                listOf(R.string.alert_also_warning, R.string.alert_also_air, R.string.alert_also_gear, R.string.alert_also_door)
                    .forEachIndexed { index, res ->
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        if (index < 3) HorizontalDivider(color = colors.border)
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
}

private fun alertBody(reason: AlertReason) = when (reason) {
    AlertReason.VEHICLE_WARNING -> R.string.alert_vehicle_body
    AlertReason.FRESH_AIR_LOST -> R.string.alert_air_body
    AlertReason.NO_RESPONSE -> R.string.alert_no_response_body
}
