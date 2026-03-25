package com.example.mpsbuilder.ui.workbench.workpiece

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.mpsbuilder.ui.workbench.model.Workpiece
import com.example.mpsbuilder.ui.workbench.model.WorkpieceShape

object WorkpieceRenderer {

    private const val WP_SIZE = 36f  // 1.5배 (24→36)

    fun draw(
        scope: DrawScope,
        workpiece: Workpiece,
        panOffset: Offset,
        zoomScale: Float
    ) {
        with(scope) {
            val x = (workpiece.positionX + panOffset.x) * zoomScale
            val y = (workpiece.positionY + panOffset.y) * zoomScale
            val s = WP_SIZE * zoomScale
            val color = Color(workpiece.type.color)

            when (workpiece.type.shape) {
                WorkpieceShape.RECTANGLE -> {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - s / 2, y - s / 2),
                        size = Size(s, s)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(x - s / 2, y - s / 2),
                        size = Size(s, s),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
                    )
                }
                WorkpieceShape.CIRCLE -> {
                    drawCircle(color = color, radius = s / 2, center = Offset(x, y))
                }
                WorkpieceShape.CYLINDER_TOP -> {
                    drawOval(
                        color = color,
                        topLeft = Offset(x - s / 2, y - s / 3),
                        size = Size(s, s * 0.66f)
                    )
                }
            }
        }
    }
}
