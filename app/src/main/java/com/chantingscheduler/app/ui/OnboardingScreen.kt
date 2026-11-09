package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.chantingscheduler.app.ui.theme.UrbanistFamily
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val gradient: List<Color>,
    val art: @Composable () -> Unit,
)

/** First-launch introduction: three full-bleed gradient pages with vector art. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_1_title),
            body = stringResource(R.string.onboarding_1_body),
            gradient = listOf(Color(0xFF1A2340), Color(0xFF6B3B2A), Color(0xFFE8A35C)),
            art = { DawnArt() },
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_2_title),
            body = stringResource(R.string.onboarding_2_body),
            gradient = listOf(Color(0xFF2B6A8F), Color(0xFF7FB3C8), Color(0xFFEAF2EF)),
            art = { DayArt() },
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_3_title),
            body = stringResource(R.string.onboarding_3_body),
            gradient = listOf(Color(0xFF0B1030), Color(0xFF1E2A5A), Color(0xFF3A3F74)),
            art = { NightArt() },
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            OnboardingPageContent(pages[index])
        }

        TextButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Text(stringResource(R.string.onboarding_skip), color = Color.White.copy(alpha = 0.85f))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(count = pages.size, current = pagerState.currentPage)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1A2340),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    if (pagerState.currentPage == pages.lastIndex) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(page.gradient)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1.1f))
            Box(Modifier.size(240.dp)) { page.art() }
            Spacer(Modifier.weight(0.7f))
            Text(
                page.title,
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val width by animateDpAsState(
                targetValue = if (index == current) 22.dp else 8.dp,
                label = "dotWidth",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        if (index == current) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.4f)
                        },
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/** Dawn: rising sun with glow over a horizon, a few fading stars. */
@Composable
private fun DawnArt() {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        // horizon
        val horizonY = size.height * 0.72f
        drawRect(
            Color(0xFF241A30),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - horizonY),
        )
        // sun glow
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFD9A0).copy(alpha = 0.9f), Color.Transparent),
                center = Offset(cx, horizonY - 30.dp.toPx()),
                radius = 90.dp.toPx(),
            ),
            radius = 90.dp.toPx(),
            center = Offset(cx, horizonY - 30.dp.toPx()),
        )
        // sun
        drawCircle(Color(0xFFFFC36B), radius = 34.dp.toPx(), center = Offset(cx, horizonY - 30.dp.toPx()))
        // stars
        val starColor = Color.White.copy(alpha = 0.6f)
        listOf(
            Offset(0.15f, 0.12f), Offset(0.8f, 0.08f), Offset(0.3f, 0.28f),
            Offset(0.65f, 0.22f), Offset(0.9f, 0.3f),
        ).forEach { p ->
            drawCircle(starColor, radius = 2.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
        }
    }
}

/** Day: bright sun with rays and two soft clouds. */
@Composable
private fun DayArt() {
    Canvas(Modifier.fillMaxSize()) {
        val sun = Offset(size.width * 0.5f, size.height * 0.34f)
        val sunR = 34.dp.toPx()
        // rays
        val rayColor = Color(0xFFFFE9B8)
        for (a in 0 until 8) {
            val rad = Math.toRadians(a * 45.0)
            val start = Offset(
                (sun.x + (sunR + 8.dp.toPx()) * kotlin.math.cos(rad)).toFloat(),
                (sun.y + (sunR + 8.dp.toPx()) * kotlin.math.sin(rad)).toFloat(),
            )
            val end = Offset(
                (sun.x + (sunR + 20.dp.toPx()) * kotlin.math.cos(rad)).toFloat(),
                (sun.y + (sunR + 20.dp.toPx()) * kotlin.math.sin(rad)).toFloat(),
            )
            drawLine(rayColor, start, end, strokeWidth = 4.dp.toPx())
        }
        drawCircle(Color(0xFFFFD166), radius = sunR, center = sun)
        // clouds
        val cloud = Color.White.copy(alpha = 0.9f)
        fun cloud(cx: Float, cy: Float, s: Float) {
            drawCircle(cloud, radius = 16.dp.toPx() * s, center = Offset(cx, cy))
            drawCircle(cloud, radius = 22.dp.toPx() * s, center = Offset(cx + 20.dp.toPx() * s, cy - 6.dp.toPx() * s))
            drawCircle(cloud, radius = 16.dp.toPx() * s, center = Offset(cx + 40.dp.toPx() * s, cy))
            drawRect(
                cloud,
                topLeft = Offset(cx - 14.dp.toPx() * s, cy - 2.dp.toPx() * s),
                size = androidx.compose.ui.geometry.Size(68.dp.toPx() * s, 16.dp.toPx() * s),
            )
        }
        cloud(size.width * 0.14f, size.height * 0.62f, 1f)
        cloud(size.width * 0.55f, size.height * 0.76f, 0.75f)
    }
}

/** Night: crescent moon, stars, and a shooting star. */
@Composable
private fun NightArt() {
    Canvas(Modifier.fillMaxSize()) {
        val moon = Offset(size.width * 0.5f, size.height * 0.3f)
        val moonR = 36.dp.toPx()
        // moon glow
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF3C4).copy(alpha = 0.35f), Color.Transparent),
                center = moon,
                radius = moonR * 2.2f,
            ),
            radius = moonR * 2.2f,
            center = moon,
        )
        // crescent: full moon minus an offset disc of the background color
        drawCircle(Color(0xFFFFF3C4), radius = moonR, center = moon)
        drawCircle(
            Color(0xFF16204A),
            radius = moonR * 0.85f,
            center = Offset(moon.x + moonR * 0.45f, moon.y - moonR * 0.2f),
        )
        // stars
        val starColor = Color.White.copy(alpha = 0.85f)
        listOf(
            Offset(0.12f, 0.1f), Offset(0.85f, 0.14f), Offset(0.25f, 0.45f),
            Offset(0.72f, 0.5f), Offset(0.9f, 0.4f), Offset(0.1f, 0.68f),
        ).forEach { p ->
            drawCircle(starColor, radius = 2.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
        }
        // shooting star
        val tail = Offset(size.width * 0.62f, size.height * 0.66f)
        drawLine(
            Brush.linearGradient(
                listOf(Color.White, Color.Transparent),
                start = tail,
                end = Offset(tail.x + 56.dp.toPx(), tail.y - 24.dp.toPx()),
            ),
            start = tail,
            end = Offset(tail.x + 56.dp.toPx(), tail.y - 24.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
        )
        drawCircle(Color.White, radius = 3.dp.toPx(), center = tail)
    }
}
