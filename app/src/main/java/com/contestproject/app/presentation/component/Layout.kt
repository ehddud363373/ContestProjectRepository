package com.contestproject.app.presentation.component

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contestproject.app.presentation.theme.Shimpyo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 가로 창(앱 2개 중 하나 또는 전체)이면 좌우 2단, 세로 창이면 위아래로 쌓는다. */
val WIDE_BREAKPOINT: Dp = 840.dp

@Composable
fun ScreenScaffold(
    header: @Composable (wide: Boolean) -> Unit,
    main: @Composable ColumnScope.(wide: Boolean) -> Unit,
    side: (@Composable ColumnScope.(wide: Boolean) -> Unit)? = null,
    sideWidth: Dp = 440.dp,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Shimpyo.colors.background),
    ) {
        val wide = maxWidth >= WIDE_BREAKPOINT
        Column(
            Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            header(wide)
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(20.dp, androidx.compose.ui.Alignment.CenterVertically),
                    ) { main(true) }
                    if (side != null) {
                        Column(
                            Modifier.width(sideWidth).fillMaxHeight(),
                            // 왼쪽 본문과 같은 높이 기준(가운데)으로 맞춘다.
                            verticalArrangement = Arrangement.spacedBy(14.dp, androidx.compose.ui.Alignment.CenterVertically),
                        ) { side(true) }
                    }
                }
            } else {
                // 세로 창은 높이가 모자랄 수 있어 스크롤을 허용한다.
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    main(false)
                    side?.invoke(this, false)
                }
            }
        }
    }
}

@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    return remember(context) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "a h:mm"
        val format = SimpleDateFormat(pattern, Locale.KOREAN)
        val formatter: (Long) -> String = { ms -> format.format(Date(ms)) }
        formatter
    }
}

/** 남은 시간 m:ss */
fun formatCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs + 999) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

fun formatTemp(celsius: Float): String =
    if (celsius % 1f == 0f) "%.0f℃".format(celsius) else "%.1f℃".format(celsius)
