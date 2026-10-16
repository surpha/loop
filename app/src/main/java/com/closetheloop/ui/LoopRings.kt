package com.closetheloop.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF453A)

    fun of(urgency: Urgency): Color = when (urgency) {
        Urgency.CALM -> Green
        Urgency.WARNING -> Amber
        Urgency.CRITICAL -> Red
    }
}

data class Ring(val fraction: Float, val color: Color)

private val RingEdge = 3.dp
private val RingStroke = 7.dp
private val RingGap = 4.dp

/** Horizontal space the rings occupy, so content can sit inside them. */
fun ringsInset(count: Int): Dp =
    if (count == 0) 0.dp else RingEdge + RingStroke * count + RingGap * (count - 1) + 4.dp

/** Concentric bezel rings, index 0 = outermost. Each sweeps clockwise from 12 o'clock with a soft glow. */
@Composable
fun LoopRings(rings: List<Ring>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = RingStroke.toPx()
        rings.forEachIndexed { i, ring ->
            val inset = RingEdge.toPx() + stroke / 2 + i * (stroke + RingGap.toPx())
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
                    color = ring.color.copy(alpha = 0.7f)
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
