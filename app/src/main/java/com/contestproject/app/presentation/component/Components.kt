package com.contestproject.app.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contestproject.app.presentation.theme.Shimpyo
import kotlinx.coroutines.delay

/** 화면 바깥 여백. 디자인 시안 1280×720 기준 */
val ScreenPadding = PaddingValues(horizontal = 36.dp, vertical = 28.dp)

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    container: Color = Shimpyo.colors.surface,
    border: Color = Shimpyo.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PanelLabel(text: String, color: Color = Shimpyo.colors.textSecondary) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** 차가 해 준 일 / 해 줄 일 한 줄 */
@Composable
fun CheckRow(text: String, done: Boolean = true, tint: Color = Shimpyo.colors.check) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (done) CheckMark(tint) else Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text("–", color = Shimpyo.colors.textSecondary)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) Shimpyo.colors.textPrimary else Shimpyo.colors.textSecondary,
        )
    }
}

@Composable
fun CheckMark(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.18f, w * 0.52f)
            lineTo(w * 0.40f, w * 0.72f)
            lineTo(w * 0.84f, w * 0.26f)
        }
        drawPath(path, tint, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 앱 표식: 쉼표(,) 모양. 꽉 찬 머리 + 아래로 가늘어지는 꼬리 (속이 빈 원은 숫자 9처럼 보여서 바꿈) */
@Composable
fun CommaMark(tint: Color = Shimpyo.colors.accent, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.5f, w * 0.36f))
        val tail = Path().apply {
            moveTo(w * 0.72f, w * 0.36f)
            quadraticTo(w * 0.74f, w * 0.78f, w * 0.34f, w * 0.94f)
            quadraticTo(w * 0.56f, w * 0.72f, w * 0.50f, w * 0.56f)
            close()
        }
        drawPath(tail, tint)
    }
}

@Composable
fun StatusChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Shimpyo.colors.surface)
            .border(1.dp, Shimpyo.colors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Shimpyo.colors.textPrimary,
    )
}

/** 화면의 주 동작. 높이 104dp: 장갑·흔들림에도 놓치지 않는 크기 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = Shimpyo.colors.primaryButton,
    content: Color = Shimpyo.colors.onPrimaryButton,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.45f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(32.dp), color = content, strokeWidth = 3.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** 보조 동작 (테두리 버튼) */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    subtitle: String? = null,
    large: Boolean = false,
) {
    val colors = Shimpyo.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (emphasized) colors.accentBorder else colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (emphasized) colors.accentContainer else colors.surface,
            contentColor = if (emphasized) colors.accentText else colors.textPrimary,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = if (emphasized || large) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * [ − ]  15분  [ + ] 시간 고르기. 차 화면에서는 슬라이더보다 큰 버튼이 정확히 누르기 쉽다.
 * 끝에 닿으면 그쪽 버튼을 끈다(범위를 벗어난 값은 만들 수 없다).
 */
@Composable
fun MinuteStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    minusDescription: String,
    plusDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val colors = Shimpyo.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, colors.accent, RoundedCornerShape(16.dp))
            .background(colors.accentContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", minusDescription, minusEnabled, onMinus, height)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.accentText,
            textAlign = TextAlign.Center,
        )
        StepperButton("+", plusDescription, plusEnabled, onPlus, height)
    }
}

/**
 * 한 번 누르면 1칸, 누르고 있으면 [HOLD_START_MS] 뒤부터 [HOLD_REPEAT_MS]마다 계속 움직인다.
 * 1분 단위라 10분→30분이 20번 누르기가 되어서, 길게 누르기로 빨리 옮길 수 있게 했다.
 * 끝에 닿아 버튼이 꺼지면 반복도 멈춘다.
 */
@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit, size: Dp) {
    val colors = Shimpyo.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val latestOnClick by rememberUpdatedState(onClick)
    val latestEnabled by rememberUpdatedState(enabled)
    // 길게 눌러 이미 움직였으면, 손을 뗄 때 오는 클릭 한 번은 버린다(한 칸 더 가지 않게).
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated = false
        delay(HOLD_START_MS)
        while (latestEnabled) {
            repeated = true
            latestOnClick()
            delay(HOLD_REPEAT_MS)
        }
    }
    Box(
        Modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
            ) {
                if (repeated) repeated = false else onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) colors.accentText else colors.accentText.copy(alpha = 0.3f),
        )
    }
}

private const val HOLD_START_MS = 450L
private const val HOLD_REPEAT_MS = 110L

@Composable
fun NapProgressBar(progress: Float, description: String, color: Color = Shimpyo.colors.accent) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Shimpyo.colors.track)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

/** 화면 상단 한 줄: 왼쪽 상태, 오른쪽 보조 정보 */
@Composable
fun TopLine(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = left)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = right)
    }
}

/** 이름-값 한 줄 (출발 전 요약) */
@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Shimpyo.colors.surface)
            .border(1.dp, Shimpyo.colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Shimpyo.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Shimpyo.colors.textPrimary)
    }
}
