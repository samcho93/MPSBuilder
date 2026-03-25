package com.example.mpsbuilder.ui.workbench.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.mpsbuilder.ui.workbench.model.WorkbenchLayout

object WorkbenchRenderer {

    private val gridColor = Color(0xFFE0E0E0)
    private val gridMajorColor = Color(0xFFBDBDBD)
    private val backgroundColor = Color(0xFFF5F5F5)
    private val borderColor = Color(0xFF9E9E9E)

    fun drawBackground(
        scope: DrawScope,
        layout: WorkbenchLayout,
        panOffset: Offset,
        zoomScale: Float
    ) {
        with(scope) {
            // 전체 배경
            drawRect(color = Color(0xFFEEEEEE))

            val wbWidth = layout.workbenchWidthDp * zoomScale
            val wbHeight = layout.workbenchHeightDp * zoomScale
            val ox = panOffset.x * zoomScale
            val oy = panOffset.y * zoomScale

            // 작업대 영역 배경
            drawRect(
                color = backgroundColor,
                topLeft = Offset(ox, oy),
                size = androidx.compose.ui.geometry.Size(wbWidth, wbHeight)
            )

            // 격자 그리기
            val gridSpacing = 40f * zoomScale
            val majorEvery = 5

            // 세로선
            var i = 0
            var x = ox
            while (x <= ox + wbWidth) {
                val color = if (i % majorEvery == 0) gridMajorColor else gridColor
                val strokeW = if (i % majorEvery == 0) 1.5f else 0.5f
                if (x >= 0 && x <= size.width) {
                    drawLine(
                        color = color,
                        start = Offset(x, maxOf(0f, oy)),
                        end = Offset(x, minOf(size.height, oy + wbHeight)),
                        strokeWidth = strokeW
                    )
                }
                x += gridSpacing
                i++
            }

            // 가로선
            i = 0
            var y = oy
            while (y <= oy + wbHeight) {
                val color = if (i % majorEvery == 0) gridMajorColor else gridColor
                val strokeW = if (i % majorEvery == 0) 1.5f else 0.5f
                if (y >= 0 && y <= size.height) {
                    drawLine(
                        color = color,
                        start = Offset(maxOf(0f, ox), y),
                        end = Offset(minOf(size.width, ox + wbWidth), y),
                        strokeWidth = strokeW
                    )
                }
                y += gridSpacing
                i++
            }

            // 작업대 테두리
            drawRect(
                color = borderColor,
                topLeft = Offset(ox, oy),
                size = androidx.compose.ui.geometry.Size(wbWidth, wbHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}
