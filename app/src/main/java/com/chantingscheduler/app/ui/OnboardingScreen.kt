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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
    val phase: com.chantingscheduler.app.ui.motion.DayPhase,
)

/** First-launch introduction: three full-bleed gradient pages with vector art. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_1_title),
            body = stringResource(R.string.onboarding_1_body),
            gradient = listOf(Color(0xFF1A2340), Color(0xFF6B3B2A), Color(0xFFE8A35C)),
            phase = com.chantingscheduler.app.ui.motion.DayPhase.DAWN,
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_2_title),
            body = stringResource(R.string.onboarding_2_body),
            gradient = listOf(Color(0xFF2B6A8F), Color(0xFF7FB3C8), Color(0xFFEAF2EF)),
            phase = com.chantingscheduler.app.ui.motion.DayPhase.DAY,
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_3_title),
            body = stringResource(R.string.onboarding_3_body),
            gradient = listOf(Color(0xFF0B1030), Color(0xFF1E2A5A), Color(0xFF3A3F74)),
            phase = com.chantingscheduler.app.ui.motion.DayPhase.NIGHT,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            // 0 on the active page, ±1 on neighbours — drives the parallax.
            val pageOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            OnboardingPageContent(pages[index], pageOffset)
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
private fun OnboardingPageContent(page: OnboardingPage, pageOffset: Float) {
    val scene = com.chantingscheduler.app.ui.motion.rememberArtScene()
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
            // Pager parallax: the illustration travels at half the text speed.
            val parallaxPx = with(LocalDensity.current) { 48.dp.toPx() }
            Box(
                Modifier
                    .size(240.dp)
                    .graphicsLayer { translationX = pageOffset * parallaxPx },
            ) {
                com.chantingscheduler.app.ui.motion.SceneArt(
                    scene = scene,
                    phase = page.phase,
                    animate = com.chantingscheduler.app.ui.motion.rememberAmbientActive(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
                animationSpec = com.chantingscheduler.app.ui.motion.MotionTokens.springSlide(),
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
