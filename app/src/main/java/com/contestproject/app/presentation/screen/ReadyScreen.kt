package com.contestproject.app.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contestproject.app.R
import com.contestproject.app.data.vehicle.ClimateCapabilities
import com.contestproject.app.data.vehicle.GearState
import com.contestproject.app.data.vehicle.Powertrain
import com.contestproject.app.data.vehicle.UnavailableReason
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.domain.ClimatePlanner
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.Season
import com.contestproject.app.presentation.component.CheckRow
import com.contestproject.app.presentation.component.CommaMark
import com.contestproject.app.presentation.component.MinuteStepper
import com.contestproject.app.presentation.component.Panel
import com.contestproject.app.presentation.component.PanelLabel
import com.contestproject.app.presentation.component.PrimaryButton
import com.contestproject.app.presentation.component.ScreenScaffold
import com.contestproject.app.presentation.component.StatusChip
import com.contestproject.app.presentation.component.TopLine
import com.contestproject.app.presentation.component.formatTemp
import com.contestproject.app.presentation.component.rememberTimeFormatter
import com.contestproject.app.presentation.theme.Shimpyo
import kotlin.math.roundToInt

@Composable
fun ReadyScreen(
    vehicle: VehicleStatus,
    capabilities: ClimateCapabilities?,
    selectedMinutes: Int,
    now: Long,
    busy: Boolean,
    demoMode: Boolean,
    onSelectMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onToggleDemo: () -> Unit,
    onCyclePowertrain: () -> Unit,
) {
    val formatTime = rememberTimeFormatter()
    val parked = vehicle.gear == GearState.PARK
    val caps = capabilities ?: ClimateCapabilities()
    val canKeepAir = capabilities == null || caps.canKeepFreshAir
    val plan = ClimatePlanner.planFor(ClimatePlanner.seasonOf(vehicle.outsideTempC))

    ScreenScaffold(
        header = { wide -> ReadyHeader(vehicle, demoMode, wide, onToggleDemo, onCyclePowertrain) },
        main = { wide ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.now_time, formatTime(now)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Shimpyo.colors.textSecondary,
                )
                Text(
                    stringResource(R.string.ready_headline, selectedMinutes),
                    style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = Shimpyo.colors.textPrimary,
                )
            }
            DurationPicker(selectedMinutes, onSelectMinutes)
            PrimaryButton(
                text = when {
                    !parked -> stringResource(R.string.start_needs_park)
                    !canKeepAir -> stringResource(R.string.start_unavailable)
                    else -> stringResource(R.string.start_nap)
                },
                onClick = onStart,
                enabled = parked && canKeepAir && capabilities != null,
                loading = busy || (parked && capabilities == null),
            )
        },
        side = { _ ->
            Panel(Modifier.fillMaxWidth()) {
                PanelLabel(stringResource(R.string.ready_auto_title))
                if (caps.recirculation || capabilities == null) {
                    CheckRow(stringResource(R.string.ready_auto_outside_air))
                } else if (caps.driverWindow != null) {
                    CheckRow(stringResource(R.string.ready_auto_window_gap))
                }
                if (caps.temperature != null) {
                    CheckRow(stringResource(R.string.ready_auto_temp, formatTemp(plan.restTempC)))
                }
                if (plan.season == Season.WINTER && caps.seatHeat != null) {
                    CheckRow(stringResource(R.string.ready_auto_seat_heat))
                }
                CheckRow(stringResource(R.string.ready_auto_prewake, NapPolicy.PRE_WAKE_LEAD_MIN))
                CheckRow(stringResource(R.string.ready_auto_restore))
            }
            SafetyPanel(vehicle.powertrain, canKeepAir)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadyHeader(
    vehicle: VehicleStatus,
    demoMode: Boolean,
    wide: Boolean,
    onToggleDemo: () -> Unit,
    onCyclePowertrain: () -> Unit,
) {
    TopLine(
        left = {
            // 시연용 압축 모드(1분=3초): 앱 이름을 길게 눌러 켠다. 심사·PT에서 15분을 기다릴 수 없어 릴리스에도 둔다.
            // 켜져 있으면 상단에 "시연 모드" 칩이 항상 보여 모르고 켜 둘 일이 없다.
            Row(
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onToggleDemo),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CommaMark()
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = Shimpyo.colors.textPrimary)
                if (wide) Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = Shimpyo.colors.textSecondary)
            }
        },
        right = {
            if (demoMode) StatusChip(stringResource(R.string.demo_chip))
            // 시연 모드에서는 차종 칩을 눌러 내연기관·하이브리드를 가정해 볼 수 있다(에뮬레이터는 전기차 고정).
            val pt = powertrainLabel(vehicle.powertrain)
            if (wide && (pt != null || demoMode)) {
                val label = pt?.let { p -> vehicle.modelName?.let { "$it · $p" } ?: p } ?: stringResource(R.string.chip_vehicle_unknown)
                StatusChip(label, onClick = if (demoMode) onCyclePowertrain else null)
            }
            StatusChip(
                when (vehicle.gear) {
                    GearState.PARK -> stringResource(R.string.chip_park)
                    GearState.NOT_PARK -> stringResource(R.string.chip_not_park)
                    GearState.UNKNOWN -> stringResource(R.string.chip_gear_unknown)
                }
            )
            vehicle.outsideTempC?.let { StatusChip(stringResource(R.string.chip_outside, it.roundToInt())) }
        },
    )
}

@Composable
private fun powertrainLabel(powertrain: Powertrain): String? = when (powertrain) {
    Powertrain.COMBUSTION -> stringResource(R.string.pt_combustion)
    Powertrain.HYBRID -> stringResource(R.string.pt_hybrid)
    Powertrain.ELECTRIC -> stringResource(R.string.pt_electric)
    Powertrain.UNKNOWN -> null
}

@Composable
private fun SafetyPanel(powertrain: Powertrain, canKeepAir: Boolean) {
    val colors = Shimpyo.colors
    Panel(Modifier.fillMaxWidth(), container = colors.cautionContainer, border = colors.cautionBorder) {
        if (!canKeepAir) {
            Text(stringResource(R.string.safety_no_air_title), style = MaterialTheme.typography.bodySmall, color = colors.cautionTitle)
            Text(stringResource(R.string.safety_no_air_body), style = MaterialTheme.typography.bodyMedium, color = colors.cautionText)
            return@Panel
        }
        Text(
            stringResource(
                when (powertrain) {
                    Powertrain.HYBRID -> R.string.safety_title_hybrid
                    Powertrain.ELECTRIC -> R.string.safety_title_ev
                    else -> R.string.safety_title_combustion
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.cautionTitle,
        )
        if (powertrain == Powertrain.HYBRID) {
            Text(stringResource(R.string.safety_stay_mode), style = MaterialTheme.typography.bodyMedium, color = colors.cautionText)
        }
        Text(stringResource(R.string.safety_open_place), style = MaterialTheme.typography.bodyMedium, color = colors.cautionText)
        if (powertrain != Powertrain.ELECTRIC) {
            Text(stringResource(R.string.safety_exhaust), style = MaterialTheme.typography.bodyMedium, color = colors.cautionText)
        }
    }
}

@Composable
fun ConnectingScreen() {
    CenteredMessage {
        CircularProgressIndicator(Modifier.size(48.dp), color = Shimpyo.colors.accent)
        Text(stringResource(R.string.connecting), style = MaterialTheme.typography.titleMedium, color = Shimpyo.colors.textPrimary)
    }
}

@Composable
fun UnavailableScreen(reason: UnavailableReason) {
    CenteredMessage {
        CommaMark(size = 48.dp)
        Text(
            stringResource(
                when (reason) {
                    UnavailableReason.NOT_A_CAR -> R.string.unavailable_not_car
                    UnavailableReason.PERMISSION_DENIED -> R.string.unavailable_permission
                    UnavailableReason.SERVICE_MISSING, UnavailableReason.UNKNOWN -> R.string.unavailable_service
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = Shimpyo.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.unavailable_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Shimpyo.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** 쪽잠 길이 고르기: 10~30분, 1분 단위. +/−를 누르고 있으면 계속 바뀐다. 권장 범위와 긴 쪽잠 주의를 바로 아래에 보여 준다. */
@Composable
private fun DurationPicker(selectedMinutes: Int, onSelectMinutes: (Int) -> Unit) {
    val colors = Shimpyo.colors
    val step = NapPolicy.DURATION_STEP_MIN
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MinuteStepper(
            value = stringResource(R.string.minutes, selectedMinutes),
            onMinus = { onSelectMinutes(selectedMinutes - step) },
            onPlus = { onSelectMinutes(selectedMinutes + step) },
            minusEnabled = selectedMinutes - step >= NapPolicy.MIN_DURATION_MIN,
            plusEnabled = selectedMinutes + step <= NapPolicy.MAX_DURATION_MIN,
            minusDescription = stringResource(R.string.stepper_less, step),
            plusDescription = stringResource(R.string.stepper_more, step),
        )
        val recommended = NapPolicy.RECOMMENDED_DURATION_MIN
        val (hint, color) = when {
            selectedMinutes >= NapPolicy.LONG_NAP_FROM_MIN -> stringResource(R.string.duration_hint_long) to colors.cautionTitle
            selectedMinutes in recommended -> stringResource(R.string.duration_hint_recommended, recommended.first, recommended.last) to colors.check
            else -> stringResource(
                R.string.duration_hint_range, recommended.first, recommended.last, NapPolicy.MIN_DURATION_MIN, NapPolicy.MAX_DURATION_MIN,
            ) to colors.textSecondary
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
