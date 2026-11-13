package com.contestproject.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/*
 * 차량 화면용 글자 체계.
 * - 서체: 시스템 기본(Pleos Connect = Asta Sans). 플랫폼 서체를 따라야 다른 Pleos 앱과 같은 인상이 난다.
 * - 크기: 팔 길이(약 70cm)에서 한눈에 읽히도록 본문 최소 18sp. 화면 크기 보정(DesignScale)이 위에 곱해진다.
 * - 숫자: 남은 시간·시각이 바뀔 때 글자 폭이 흔들리지 않게 고정폭 숫자(tnum).
 * - 줄바꿈: 한국어를 어절 단위로 끊는다("깨워 / 드릴게요" 같은 끊김 방지).
 * - 단계는 6개만 실제로 쓴다: 숫자(대형) / 제목 / 버튼 / 본문 / 보조 / 캡션.
 */
private val Base = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "tnum",
    lineBreak = LineBreak(
        strategy = LineBreak.Strategy.HighQuality,
        strictness = LineBreak.Strictness.Normal,
        wordBreak = LineBreak.WordBreak.Phrase,
    ),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val ShimpyoTypography = Typography(
    // 남은 시간 (가로 창 / 세로 창·기상 연출)
    displayLarge = Base.copy(fontSize = 200.sp, lineHeight = 200.sp, fontWeight = FontWeight.Bold, letterSpacing = (-6).sp),
    displayMedium = Base.copy(fontSize = 132.sp, lineHeight = 132.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp),

    // 화면 제목 ("15분 뒤에 깨워 드릴게요")
    headlineLarge = Base.copy(fontSize = 56.sp, lineHeight = 70.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = Base.copy(fontSize = 46.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),

    // 주 버튼 / 앱 이름·상태 제목
    titleLarge = Base.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleMedium = Base.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleSmall = Base.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),

    // 본문: 큰 설명 / 목록·체크 / 보조(최소 크기)
    bodyLarge = Base.copy(fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = Base.copy(fontSize = 21.sp, lineHeight = 30.sp),
    bodySmall = Base.copy(fontSize = 18.sp, lineHeight = 26.sp),

    // 버튼 라벨: 선택 버튼 / 보조 버튼 / 버튼 아래 설명
    labelLarge = Base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    labelMedium = Base.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.copy(fontSize = 18.sp, lineHeight = 24.sp),
)
