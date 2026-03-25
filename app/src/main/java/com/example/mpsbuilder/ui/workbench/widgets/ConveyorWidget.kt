package com.example.mpsbuilder.ui.workbench.widgets

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.ui.workbench.model.ConveyorConfig
import com.example.mpsbuilder.ui.workbench.model.Workpiece

@Composable
fun ConveyorWidget(
    config: ConveyorConfig,
    isRunning: Boolean,
    isForward: Boolean = true,
    workpieces: List<Workpiece>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "belt")

    val beltOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isForward) 1f else -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "beltOffset"
    )

    val animValue = if (isRunning) beltOffset else 0f

    Canvas(
        modifier = modifier.size(
            width = config.size.lengthDp.dp,
            height = config.size.widthDp.dp
        )
    ) {
        val w = size.width
        val h = size.height
        val stripeSpacing = 24.dp.toPx()
        val animShift = animValue * stripeSpacing

        // 벨트 본체
        drawRoundRect(
            color = Color(config.beltColor),
            cornerRadius = CornerRadius(h / 2)
        )

        // 벨트 스트라이프
        val stripeColor = Color.Black.copy(alpha = 0.25f)
        var x = -stripeSpacing + animShift % stripeSpacing
        while (x < w + stripeSpacing) {
            drawLine(
                color = stripeColor,
                start = Offset(x, 0f),
                end = Offset(x + h * 0.6f, h),
                strokeWidth = stripeSpacing * 0.4f
            )
            x += stripeSpacing
        }

        // 롤러
        val rollerR = h / 2f
        listOf(rollerR, w - rollerR).forEach { cx ->
            drawCircle(Color(0xFF424242), rollerR, Offset(cx, h / 2f))
            drawCircle(Color(0xFF757575), rollerR * 0.5f, Offset(cx, h / 2f))
        }

        // 크기 라벨
        drawContext.canvas.nativeCanvas.drawText(
            config.size.label,
            w / 2f, h * 0.38f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = h * 0.3f
                isAntiAlias = true
            }
        )
    }
}
