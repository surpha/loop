package com.closetheloop.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.closetheloop.domain.Urgency

object LoopColors {
    val Green = Color(0xFF4CD964)
    val Red = Color(0xFFFF453A)

    // No reds: red is reserved for "almost out of time".
    private val Palette = listOf(
        Color(0xFF4FA3FF), // blue
        Color(0xFFB07CFF), // purple
        Color(0xFF2DD4BF), // teal
        Color(0xFFFF6FB5), // pink
        Color(0xFFFF9F43), // orange
        Color(0xFFA3E635), // lime
        Color(0xFFFFD60A), // yellow
        Color(0xFF7DD3FC), // sky
    )

    /** Stable per-task color, derived from the loop id. */
    fun forLoop(loopId: Long): Color = Palette[Math.floorMod(loopId, Palette.size.toLong()).toInt()]

    /** Task color, switching to red in the final 20%. */
    fun display(loopId: Long, urgency: Urgency): Color =
        if (urgency == Urgency.CRITICAL) Red else forLoop(loopId)
}

data class Ring(val fraction: Float, val color: Color, val critical: Boolean)

private val RingEdge = 3.dp
private val RingBand = 29.dp
private val MaxStroke = 7.dp
private val MinStroke = 1.2.dp

/** Rings share a fixed band, so each gets thinner as more are added (hairlines past ~10). */
private fun ringStroke(count: Int): Dp = (RingBand / (count * 1.5f - 0.5f)).coerceIn(MinStroke, MaxStroke)

/** Horizontal space the rings occupy, so content can sit inside them. */
fun ringsInset(count: Int): Dp {
    if (count == 0) return 0.dp
    val stroke = ringStroke(count)
    return RingEdge + stroke * count + stroke * 0.5f * (count - 1) + 4.dp
}

/** Concentric bezel rings, index 0 = outermost. Each sweeps clockwise from 12 o'clock with a soft glow; critical rings pulse. */
@Composable
fun LoopRings(rings: List<Ring>, modifier: Modifier = Modifier) {
    val pulse = if (rings.any { it.critical }) {
        val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "alpha",
        )
        alpha
    } else {
        1f
    }
    Canvas(modifier) {
        val stroke = ringStroke(rings.size).toPx()
        val gap = stroke * 0.5f
        rings.forEachIndexed { i, base ->
            val ring = if (base.critical) base.copy(color = base.color.copy(alpha = pulse)) else base
            val inset = RingEdge.toPx() + stroke / 2 + i * (stroke + gap)
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val sweep = 360f * ring.fraction

            drawArc(
                color = ring.color.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = stroke),
            )
            if (sweep <= 0f) return@forEachIndexed

            drawIntoCanvas { canvas ->
                val glow = Paint().apply {
                    color = ring.color.copy(alpha = 0.7f * ring.color.alpha)
                    style = PaintingStyle.Stroke
                    strokeWidth = stroke
                    strokeCap = StrokeCap.Round
                }
                glow.asFrameworkPaint().maskFilter = BlurMaskFilter(stroke * 0.9f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawArc(left, top, right, bottom, -90f, sweep, false, glow)

                val core = Paint().apply {
                    color = ring.color
                    style = PaintingStyle.Stroke
                    strokeWidth = stroke
                    strokeCap = StrokeCap.Round
                }
                canvas.drawArc(left, top, right, bottom, -90f, sweep, false, core)
            }
        }
    }
}
