package com.contestproject.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contestproject.app.R
import com.contestproject.app.data.vehicle.VehicleConnection
import com.contestproject.app.domain.NapNotice
import com.contestproject.app.domain.NapState
import com.contestproject.app.presentation.screen.AlertScreen
import com.contestproject.app.presentation.screen.ConnectingScreen
import com.contestproject.app.presentation.screen.DepartScreen
import com.contestproject.app.presentation.screen.PreWakeScreen
import com.contestproject.app.presentation.screen.ReadyScreen
import com.contestproject.app.presentation.screen.RestingScreen
import com.contestproject.app.presentation.screen.UnavailableScreen
import com.contestproject.app.presentation.screen.WakingScreen
import com.contestproject.app.presentation.theme.AlertColors
import com.contestproject.app.presentation.theme.DawnColors
import com.contestproject.app.presentation.theme.Shimpyo
import com.contestproject.app.presentation.theme.ShimpyoSurfaceTheme
import com.contestproject.app.presentation.theme.ShimpyoTheme
import com.contestproject.app.presentation.theme.SleepColors
import com.contestproject.app.presentation.theme.WakeColors

/** 화면 이동은 쪽잠 상태가 결정한다. 사용자가 고를 메뉴가 없어서 Navigation 라이브러리는 쓰지 않는다. */
@Composable
fun ShimpyoRoot(viewModel: NapViewModel) {
    val napState by viewModel.napState.collectAsStateWithLifecycle()
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val monitor by viewModel.monitor.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val minutes by viewModel.selectedMinutes.collectAsStateWithLifecycle()
    val demo by viewModel.demoMode.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    KeepScreenOn(napState is NapState.Active)

    ShimpyoTheme {
        DesignScale {
            Box(Modifier.fillMaxSize().background(Shimpyo.colors.background)) {
                Crossfade(targetState = screenKey(napState), label = "screen") { key ->
                    when (key) {
                        ScreenKey.IDLE -> when (val connection = vehicle.connection) {
                            VehicleConnection.Connecting -> ConnectingScreen()
                            is VehicleConnection.Unavailable -> UnavailableScreen(connection.reason)
                            VehicleConnection.Connected -> ReadyScreen(
                                vehicle = vehicle,
                                capabilities = capabilities,
                                selectedMinutes = minutes,
                                now = now,
                                busy = busy,
                                demoMode = demo,
                                onSelectMinutes = viewModel::selectMinutes,
                                onStart = viewModel::startNap,
                                onToggleDemo = viewModel::toggleDemoMode,
                                onCyclePowertrain = viewModel::cyclePowertrain,
                            )
                        }
                        ScreenKey.RESTING -> (napState as? NapState.Resting)?.let {
                            ShimpyoSurfaceTheme(SleepColors) { RestingScreen(it, monitor, vehicle, capabilities?.fanSpeed?.last, now, busy, viewModel::wakeNow) }
                        }
                        ScreenKey.PRE_WAKE -> (napState as? NapState.PreWake)?.let {
                            ShimpyoSurfaceTheme(DawnColors) { PreWakeScreen(it, monitor, now, busy, viewModel::wakeNow) }
                        }
                        ScreenKey.WAKING -> (napState as? NapState.Waking)?.let {
                            ShimpyoSurfaceTheme(WakeColors) { WakingScreen(it, monitor, vehicle, capabilities?.fanSpeed?.last, now, viewModel::acknowledgeWake) }
                        }
                        ScreenKey.DEPART -> (napState as? NapState.DepartCheck)?.let {
                            ShimpyoSurfaceTheme(WakeColors) { DepartScreen(it, monitor, vehicle, now, busy, viewModel::extend, viewModel::depart) }
                        }
                        ScreenKey.ALERT -> (napState as? NapState.Alert)?.let {
                            ShimpyoSurfaceTheme(AlertColors) { AlertScreen(it, now, viewModel::acknowledgeWake, viewModel::snoozeAlert) }
                        }
                    }
                }
                NoticeBanner(notice, viewModel::dismissNotice, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private enum class ScreenKey { IDLE, RESTING, PRE_WAKE, WAKING, DEPART, ALERT }

private fun screenKey(state: NapState) = when (state) {
    NapState.Idle -> ScreenKey.IDLE
    is NapState.Resting -> ScreenKey.RESTING
    is NapState.PreWake -> ScreenKey.PRE_WAKE
    is NapState.Waking -> ScreenKey.WAKING
    is NapState.DepartCheck -> ScreenKey.DEPART
    is NapState.Alert -> ScreenKey.ALERT
}

@Composable
private fun NoticeBanner(notice: NapNotice?, onDismiss: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible = notice != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val text = when (notice) {
            NapNotice.ENDED_BY_GEAR -> stringResource(R.string.notice_ended_by_gear)
            NapNotice.RESUMED_AFTER_RESTART -> stringResource(R.string.notice_resumed)
            NapNotice.START_FAILED -> stringResource(R.string.notice_start_failed)
            NapNotice.DEPARTED -> stringResource(R.string.notice_departed)
            null -> ""
        }
        Text(
            text,
            modifier = Modifier
                .padding(bottom = 28.dp, start = 36.dp, end = 36.dp)
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Shimpyo.colors.surfaceRaised)
                .border(1.dp, Shimpyo.colors.border, RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Shimpyo.colors.textPrimary,
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * 시안은 가로 1280×720, 세로 600×720 캔버스 기준이다. 차량 디스플레이는 밀도가 낮아(에뮬레이터 160dpi)
 * dp 그대로 그리면 요소가 작아 보이므로, 창 크기에 맞춰 dp 자체를 키운다(최대 1.6배).
 * 이렇게 하면 레이아웃 비율은 시안과 같고 글자·버튼만 커진다.
 */
@Composable
private fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth >= maxHeight
        val designWidth = if (landscape) 1280f else 600f
        val scale = minOf(maxWidth.value / designWidth, maxHeight.value / 720f).coerceIn(1f, 1.6f)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            content()
        }
    }
}
