package com.contestproject.app.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contestproject.app.R
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.presentation.theme.Shimpyo

/** 값의 출처. 차에서 다시 읽은 값인지, 앱이 보낸 값인지, 앞으로 바꿀 값인지 구분해서 보여 준다. */
enum class ValueTag { MEASURED, SET, PLANNED }

@Composable
fun TagChip(tag: ValueTag) {
    val colors = Shimpyo.colors
    val (text, fg, bg) = when (tag) {
        ValueTag.MEASURED -> Triple(stringResource(R.string.tag_measured), colors.check, colors.check.copy(alpha = 0.14f))
        ValueTag.SET -> Triple(stringResource(R.string.tag_set), colors.textSecondary, colors.surfaceRaised)
        ValueTag.PLANNED -> Triple(stringResource(R.string.tag_planned), colors.accentText, colors.accentContainer)
    }
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
    )
}

/**
 * 상태판 한 줄: 이름 | 값(굵게) + 보조 설명 | 꼬리표
 * 값은 짧게(숫자 위주), 설명은 그 값의 근거(확인 횟수, 실내·바깥 온도 등)를 둔다.
 */
@Composable
fun MonitorRow(
    label: String,
    value: String,
    detail: String? = null,
    tag: ValueTag? = null,
    valueColor: Color = Shimpyo.colors.textPrimary,
    labelWidth: Dp = 72.dp,
) {
    val colors = Shimpyo.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.width(labelWidth), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        tag?.let { TagChip(it) }
    }
}

/** 출발 전 리포트의 숫자 칸 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = Shimpyo.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1)
    }
}

/** 타임라인 위의 점(되돌림·다시 맞춤처럼 차가 실제로 한 일). position은 0~1 */
data class TimelineMark(val position: Float, val strong: Boolean = false)

/**
 * 쪽잠 구간 타임라인: [쉬는 구간 | 깨울 준비] 막대, 지나간 부분 채움, 지금 위치 눈금, 일어난 일 점.
 * @param split 깨울 준비가 시작되는 위치(0~1)
 * @param labels 막대 아래 글자: 위치(0~1)와 글자. 겹치지 않게 시작·중간·끝만 쓴다.
 */
@Composable
fun NapTimeline(
    progress: Float,
    split: Float,
    marks: List<TimelineMark>,
    labels: List<Pair<Float, String>>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = Shimpyo.colors
    val restColor = colors.track
    val wakeColor = colors.accentContainer
    val fill = colors.accent
    val markColor = colors.check
    val background = colors.background
    val nowColor = colors.textPrimary
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 중간 표시(깨울 준비·연장)는 막대 위에 둔다. 깨울 준비 구간이 짧아 아래 끝 글자와 겹치기 때문이다.
        val middle = labels.drop(1).dropLast(1)
        if (middle.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                middle.forEach { (pos, text) ->
                    Text(
                        text,
                        modifier = Modifier.align(if (pos > 0.6f) Alignment.TopEnd else Alignment.TopCenter),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentText,
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val barH = 12.dp.toPx()
            val top = (size.height - barH) / 2
            val r = CornerRadius(barH / 2)
            val gap = 4.dp.toPx()
            val splitX = size.width * split.coerceIn(0f, 1f)
            drawRoundRect(restColor, Offset(0f, top), Size((splitX - gap / 2).coerceAtLeast(0f), barH), r)
            drawRoundRect(wakeColor, Offset(splitX + gap / 2, top), Size((size.width - splitX - gap / 2).coerceAtLeast(0f), barH), r)
            val nowX = size.width * progress.coerceIn(0f, 1f)
            if (nowX > 0f) drawRoundRect(fill, Offset(0f, top), Size(nowX, barH), r)
            marks.forEach { m ->
                val x = (size.width * m.position.coerceIn(0f, 1f)).coerceIn(barH / 2, size.width - barH / 2)
                drawCircle(background, radius = barH * 0.75f, center = Offset(x, size.height / 2))
                drawCircle(markColor, radius = barH * 0.5f, center = Offset(x, size.height / 2))
            }
            if (progress in 0.001f..0.999f) {
                drawRoundRect(nowColor, Offset(nowX - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.firstOrNull()?.let { Text(it.second, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
            if (labels.size > 1) Text(labels.last().second, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/** 몇 번째 깨우는지: 채운 점 = 이미 부른 횟수, 전체 = 경보로 올리기 전까지 부르는 횟수 */
@Composable
fun CallDots(called: Int, total: Int = NapPolicy.VOICE_REPEATS_BEFORE_ALERT) {
    val colors = Shimpyo.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (i < called) colors.accent else colors.track),
            )
        }
    }
}

/** 시연 모드에서도 실제 시간 단위로 보이게 환산한 경과 시간 "n분" 또는 "n분 m초" */
fun scaledDuration(ms: Long, minuteMs: Long): Pair<Int, Int> {
    val realSec = (ms.coerceAtLeast(0) * NapPolicy.MINUTE_MS / minuteMs) / 1000
    return (realSec / 60).toInt() to (realSec % 60).toInt()
}

/** 쪽잠 시작 뒤 몇 분째인지(시연 모드 환산 포함) */
fun minuteMark(atMs: Long, startMs: Long, minuteMs: Long): Int = ((atMs - startMs).coerceAtLeast(0) / minuteMs).toInt()
