package com.lefiru.music.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lefiru.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated introduction shown before the main screen.
 *
 * The app icon appears first, above the wordmark, then the text fades in and
 * lights up while the library loads. There is no progress bar or spinner: just
 * the emblem and the name. Colors come straight from the main theme so the
 * intro feels like the rest of the app. Tapping anywhere skips the intro (the
 * splash still fades out cleanly).
 */
private const val INTRO_DURATION_MS = 2450
private const val WAIT_DURATION_MS = 350
private const val FADE_OUT_DURATION_MS = 420

private val WordmarkFont = FontFamily.SansSerif
private const val WORDMARK = "LeFiru"
private const val TAGLINE = "MUSIC PLAYER"

@Composable
fun IntroSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val glowBlue = colorScheme.primary
    val glowTeal = colorScheme.secondary

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    var waiting by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "introPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "introPulseValue",
    )

    // Tapping the splash fast-forwards the intro. `progress.stop()` makes the
    // animation below throw, and its catch block snaps straight to the end.
    val skip: () -> Unit = {
        if (!waiting) {
            scope.launch { progress.stop() }
        }
    }

    LaunchedEffect(Unit) {
        try {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = INTRO_DURATION_MS,
                    easing = CubicBezierEasing(0.16f, 0.74f, 0.24f, 1f),
                ),
            )
        } catch (_: CancellationException) {
            progress.snapTo(1f)
        }
        waiting = true
        delay(WAIT_DURATION_MS.toLong())
        exit.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = FADE_OUT_DURATION_MS,
                easing = CubicBezierEasing(0.4f, 0f, 1f, 1f),
            ),
        )
        onFinished()
    }

    val p = progress.value
    val lit = smoothstep(0.12f, 0.72f, p)
    val halo = smoothstep(0.05f, 0.75f, p)
    val taglineAlpha = smoothstep(0.5f, 0.9f, p)
    val breathing = if (waiting) pulse else 0f
    val glow = (lit + 0.18f * breathing).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .pointerInput(Unit) {
                detectTapGestures { skip() }
            }
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorScheme.surfaceContainer,
                            colorScheme.surface,
                            colorScheme.background,
                        ),
                    ),
                )

                // Soft blue halo that comes alive with the emblem and wordmark.
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to glowBlue.copy(alpha = 0.20f * halo),
                            0.45f to glowTeal.copy(alpha = 0.09f * halo),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = size.maxDimension * 0.85f,
                    ),
                )
                // Teal counter-glow, straight from the app's secondary color.
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to glowTeal.copy(alpha = 0.12f * halo),
                            1f to Color.Transparent,
                        ),
                        center = Offset(
                            x = center.x + size.width * 0.18f,
                            y = center.y - size.height * 0.14f,
                        ),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
                // Gentle vignette so the light stays focused on the emblem.
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to colorScheme.background.copy(alpha = 0.75f),
                        ),
                        center = center,
                        radius = size.maxDimension * 0.78f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val wordSize = with(density) {
            (maxWidth.toPx() * 0.16f)
                .coerceIn(40.dp.toPx(), 92.dp.toPx())
                .toSp()
        }
        val wordStyle = TextStyle(
            fontFamily = WordmarkFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = wordSize,
            letterSpacing = 1.5.sp,
        )
        val emblemSize = (maxWidth * 0.30f).coerceIn(104.dp, 136.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            SplashEmblem(
                progress = p,
                glow = glow,
                modifier = Modifier.size(emblemSize),
            )

            Spacer(Modifier.height(28.dp))

            Wordmark(
                style = wordStyle,
                lit = lit,
                glow = glow,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = TAGLINE,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp,
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha },
            )
        }

        if (versionName.isNotBlank()) {
            Text(
                text = "v$versionName",
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = taglineAlpha },
            )
        }
    }
}

@Composable
private fun SplashEmblem(
    progress: Float,
    glow: Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val appear = smoothstep(0f, 0.32f, progress)
    val settle = smoothstep(0f, 0.6f, progress)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            alpha = appear
            val scale = 0.88f + 0.12f * settle
            scaleX = scale
            scaleY = scale
        },
    ) {
        // Soft blue halo that fades into the app's teal.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to colorScheme.primary.copy(alpha = 0.34f * glow),
                                0.55f to colorScheme.secondary.copy(alpha = 0.14f * glow),
                                1f to Color.Transparent,
                            ),
                        ),
                    )
                },
        )

        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.64f)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.8f * glow),
                            colorScheme.secondary.copy(alpha = 0.5f * glow),
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun Wordmark(
    style: TextStyle,
    lit: Float,
    glow: Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val glowBlue = colorScheme.primary
    val glowTeal = colorScheme.secondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        // Wide, soft halos behind the glyphs. `blur` is a no-op below Android
        // 12, where the text shadow on the main layer keeps the glow visible.
        Text(
            text = WORDMARK,
            style = style,
            color = glowBlue,
            maxLines = 1,
            modifier = Modifier
                .blur(30.dp, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = 0.30f * glow },
        )
        Text(
            text = WORDMARK,
            style = style,
            color = glowTeal,
            maxLines = 1,
            modifier = Modifier
                .blur(16.dp, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = 0.50f * glow },
        )
        Text(
            text = WORDMARK,
            style = style,
            color = glowBlue,
            maxLines = 1,
            modifier = Modifier
                .blur(7.dp, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = 0.65f * glow },
        )

        Text(
            text = WORDMARK,
            maxLines = 1,
            style = style.copy(
                brush = Brush.verticalGradient(
                    0f to lerp(colorScheme.surfaceVariant, colorScheme.onBackground, lit),
                    0.55f to lerp(colorScheme.surfaceVariant, Color.White, lit),
                    1f to lerp(colorScheme.surfaceVariant, glowBlue, lit),
                ),
                shadow = Shadow(
                    color = glowBlue.copy(alpha = 0.45f * glow),
                    blurRadius = 26f,
                    offset = Offset.Zero,
                ),
            ),
        )
    }
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
