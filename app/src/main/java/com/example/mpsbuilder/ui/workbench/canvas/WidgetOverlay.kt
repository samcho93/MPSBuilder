package com.example.mpsbuilder.ui.workbench.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState
import com.example.mpsbuilder.ui.workbench.widgets.WidgetRenderer

/** 순수 그리기 전용 — 터치 이벤트 없음 */
@Composable
fun WidgetOverlay(
    widget: PlacedWidgetState,
    panOffset: Offset,
    zoom: Float
) {
    val wSize = WidgetRenderer.getWidgetSize(widget)
    val scaledW = wSize.width * zoom
    val scaledH = wSize.height * zoom
    val wx = (widget.positionX + panOffset.x) * zoom
    val wy = (widget.positionY + panOffset.y) * zoom
    val pivotX = scaledW / 2f
    val pivotY = scaledH / 2f

    val pad = 8f
    val barH = 22f
    val barW = scaledW + pad * 2
    val handleR = 10f

    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(wx, wy)
            rotate(widget.rotationDeg, Offset(pivotX, pivotY))
        }) {
            // 점선 테두리
            drawRect(
                color = Color(0xFF2196F3),
                topLeft = Offset(-pad, -pad),
                size = Size(scaledW + pad * 2, scaledH + pad * 2),
                style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)))
            )

            // 회전 바
            drawRoundRect(
                color = Color(0xFF2196F3),
                topLeft = Offset(-pad, -pad - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(4f)
            )
            drawCircle(Color.White, 5f, Offset(scaledW / 2f, -pad - barH / 2f))

            // 네 모서리 핸들
            listOf(
                Offset(-pad, -pad),
                Offset(scaledW + pad, -pad),
                Offset(-pad, scaledH + pad),
                Offset(scaledW + pad, scaledH + pad),
            ).forEach { c ->
                drawCircle(Color.White, handleR, c)
                drawCircle(Color(0xFF2196F3), handleR, c, style = Stroke(2.5f))
            }
        }
    }
}
