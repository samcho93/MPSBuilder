package com.example.mpsbuilder.ui.ladder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.mpsbuilder.data.ladder.LadderElement

object ElementRenderer {

    private val COLOR_INACTIVE = Color(0xFF333333)
    private val COLOR_ACTIVE = Color(0xFF00CC44)
    private val COLOR_COIL = Color(0xFFFF6600)
    private val COLOR_SET_RST = Color(0xFFAA00FF)
    private val COLOR_LINE = Color(0xFF555555)
    private val COLOR_TIMER_COUNTER = Color(0xFF008800)
    private val COLOR_FUNC_BLOCK = Color(0xFF0066AA)
    private val COLOR_VERTICAL = Color(0xFF555555)
    private val COLOR_VERTICAL_ACTIVE = Color(0xFF00CC44)
    private val COLOR_EMPTY = Color(0xFFDDDDDD)

    private val COLOR_ADDR_TEXT = Color(0xFF1565C0)   // IO 주소 색상 (파란색)
    private val COLOR_NAME_TEXT = Color(0xFF666666)    // 이름 색상 (회색)

    fun draw(
        scope: DrawScope,
        element: LadderElement?,
        x: Float, y: Float,
        cellW: Float, cellH: Float,
        isActive: Boolean,
        addressText: String,
        nameText: String,
        hasBottom: Boolean,
        isSelected: Boolean,
        timerCurrentMs: Int = 0,
        counterCurrentVal: Int = 0
    ) {
        // 선택 하이라이트 (진한 배경 + 테두리)
        if (isSelected) {
            scope.drawRect(
                color = Color(0x6600AAFF),
                topLeft = Offset(x, y - cellH / 2f),
                size = Size(cellW, cellH)
            )
            scope.drawRect(
                color = Color(0xFF0088FF),
                topLeft = Offset(x, y - cellH / 2f),
                size = Size(cellW, cellH),
                style = Stroke(width = 3f)
            )
        }

        when (element) {
            null -> drawEmptyCell(scope, x, y, cellW)
            is LadderElement.NormallyOpen -> drawContact(scope, x, y, cellW, cellH, isActive, addressText, nameText, ContactType.A)
            is LadderElement.NormallyClosed -> drawContact(scope, x, y, cellW, cellH, isActive, addressText, nameText, ContactType.B)
            is LadderElement.RisingEdgeContact -> drawContact(scope, x, y, cellW, cellH, isActive, addressText, nameText, ContactType.AP)
            is LadderElement.FallingEdgeContact -> drawContact(scope, x, y, cellW, cellH, isActive, addressText, nameText, ContactType.BP)
            is LadderElement.OutputCoil -> drawCoil(scope, x, y, cellW, cellH, isActive, addressText, nameText, "")
            is LadderElement.SetCoil -> drawCoil(scope, x, y, cellW, cellH, isActive, addressText, nameText, "S")
            is LadderElement.ResetCoil -> drawCoil(scope, x, y, cellW, cellH, isActive, addressText, nameText, "R")
            is LadderElement.RisingEdge -> drawCoil(scope, x, y, cellW, cellH, isActive, addressText, nameText, "P")
            is LadderElement.FallingEdge -> drawCoil(scope, x, y, cellW, cellH, isActive, addressText, nameText, "F")
            is LadderElement.Timer -> drawTimerCounter(scope, x, y, cellW, cellH, isActive, "T${element.timerNumber}", "K${element.preset}", nameText, timerCurrentMs)
            is LadderElement.Counter -> drawTimerCounter(scope, x, y, cellW, cellH, isActive, "C${element.counterNumber}", "K${element.preset}", nameText, 0, counterCurrentVal)
            is LadderElement.FunctionBlock -> drawFunctionBlock(scope, x, y, cellW, cellH, isActive, element.mnemonic, element.operand1, element.operand2)
            is LadderElement.SpecialRelay -> drawContact(scope, x, y, cellW, cellH, isActive, addressText, nameText, ContactType.A)
            is LadderElement.HorizontalLine -> drawHLine(scope, x, y, cellW, isActive)
        }

        // 수직 연결선 (아래로)
        if (hasBottom) {
            val vColor = if (isActive) COLOR_VERTICAL_ACTIVE else COLOR_VERTICAL
            scope.drawLine(
                vColor,
                Offset(x + cellW, y),
                Offset(x + cellW, y + cellH),
                strokeWidth = 3f
            )
        }
    }

    private enum class ContactType { A, B, AP, BP }

    /**
     * 접점 그리기:
     *   addressText (위) ← IO 주소 (X000, M0 등)
     *   ┤ ├ 기호 (중간)
     *   nameText (아래) ← 이름 (PB_START 등)
     */
    private fun drawContact(
        scope: DrawScope, x: Float, y: Float, cellW: Float, cellH: Float,
        isActive: Boolean, addressText: String, nameText: String, type: ContactType
    ) {
        val color = if (isActive) COLOR_ACTIVE else COLOR_INACTIVE
        val strokeW = if (isActive) 3f else 2f
        val contactW = cellW * 0.28f
        val contactH = cellH * 0.35f
        val cx = x + cellW / 2f

        scope.apply {
            // 수평 연결선
            drawLine(color, Offset(x, y), Offset(cx - contactW / 2, y), strokeWidth = strokeW)
            drawLine(color, Offset(cx + contactW / 2, y), Offset(x + cellW, y), strokeWidth = strokeW)

            // 접점 세로선 ┤ ├
            drawLine(color, Offset(cx - contactW / 2, y - contactH / 2), Offset(cx - contactW / 2, y + contactH / 2), strokeWidth = strokeW + 1)
            drawLine(color, Offset(cx + contactW / 2, y - contactH / 2), Offset(cx + contactW / 2, y + contactH / 2), strokeWidth = strokeW + 1)

            when (type) {
                ContactType.B -> {
                    drawLine(color, Offset(cx - contactW / 3, y + contactH / 3), Offset(cx + contactW / 3, y - contactH / 3), strokeWidth = strokeW)
                }
                ContactType.AP -> {
                    drawContext.canvas.nativeCanvas.apply {
                        val p = android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            textSize = contactH * 0.55f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            isFakeBoldText = true
                        }
                        drawText("P", cx, y + contactH * 0.2f, p)
                    }
                }
                ContactType.BP -> {
                    drawContext.canvas.nativeCanvas.apply {
                        val p = android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            textSize = contactH * 0.55f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            isFakeBoldText = true
                        }
                        drawText("F", cx, y + contactH * 0.2f, p)
                    }
                }
                ContactType.A -> { /* a접점: 기호 없음 */ }
            }

            // ── IO 주소 (위) ──
            if (addressText.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = COLOR_ADDR_TEXT.toArgb()
                        textSize = cellW * 0.13f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    drawText(addressText, cx, y - contactH / 2 - cellH * 0.04f, paint)
                }
            }

            // ── 이름 (아래) ──
            if (nameText.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = COLOR_NAME_TEXT.toArgb()
                        textSize = cellW * 0.11f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(nameText, cx, y + contactH / 2 + cellH * 0.13f, paint)
                }
            }
        }
    }

    /**
     * 코일 그리기:
     *   addressText (위) ← IO 주소
     *   ( ) 기호 (중간)
     *   nameText (아래) ← 이름
     */
    private fun drawCoil(
        scope: DrawScope, x: Float, y: Float, cellW: Float, cellH: Float,
        isActive: Boolean, addressText: String, nameText: String, symbol: String
    ) {
        val color = when (symbol) {
            "S", "R" -> if (isActive) COLOR_SET_RST else COLOR_SET_RST.copy(alpha = 0.5f)
            else -> if (isActive) COLOR_COIL else COLOR_COIL.copy(alpha = 0.5f)
        }
        val cx = x + cellW / 2f
        val radius = cellW * 0.16f

        scope.apply {
            drawLine(color, Offset(x, y), Offset(cx - radius, y), strokeWidth = 2f)
            drawLine(color, Offset(cx + radius, y), Offset(x + cellW, y), strokeWidth = 2f)

            drawCircle(color, radius, Offset(cx, y), style = Stroke(width = if (isActive) 3f else 2f))
            if (isActive) {
                drawCircle(color.copy(alpha = 0.25f), radius * 0.8f, Offset(cx, y))
            }

            // 코일 내부 기호 (S, R, P, F)
            if (symbol.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        textSize = cellW * 0.15f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    drawText(symbol, cx, y + cellW * 0.055f, p)
                }
            }

            // ── IO 주소 (위) ──
            if (addressText.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = COLOR_ADDR_TEXT.toArgb()
                        textSize = cellW * 0.13f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    drawText(addressText, cx, y - radius - cellH * 0.04f, paint)
                }
            }

            // ── 이름 (아래) ──
            if (nameText.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = COLOR_NAME_TEXT.toArgb()
                        textSize = cellW * 0.11f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(nameText, cx, y + radius + cellH * 0.14f, paint)
                }
            }
        }
    }

    /**
     * 타이머/카운터:
     *   이름 (위)
     *   ┤ T0 K100 ├ (중간)
     *   nameText (아래)
     */
    private val COLOR_TIMER_CURRENT = Color(0xFFFF4400)  // 타이머 현재값 색상

    private fun drawTimerCounter(
        scope: DrawScope, x: Float, y: Float, cellW: Float, cellH: Float,
        isActive: Boolean, name: String, preset: String, nameText: String,
        currentMs: Int = 0, counterVal: Int = 0
    ) {
        val color = if (isActive) COLOR_TIMER_COUNTER else COLOR_TIMER_COUNTER.copy(alpha = 0.5f)
        val boxW = cellW * 0.8f
        val boxH = cellH * 0.50f
        val cx = x + cellW / 2f

        scope.apply {
            drawLine(color, Offset(x, y), Offset(cx - boxW / 2, y), strokeWidth = 2f)
            drawLine(color, Offset(cx + boxW / 2, y), Offset(x + cellW, y), strokeWidth = 2f)
            drawRect(color, Offset(cx - boxW / 2, y - boxH / 2), Size(boxW, boxH), style = Stroke(2f))
            if (isActive) drawRect(color.copy(alpha = 0.1f), Offset(cx - boxW / 2, y - boxH / 2), Size(boxW, boxH))

            // ── 타이머/카운터 번호 (위) ──
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    this.color = COLOR_ADDR_TEXT.toArgb()
                    textSize = cellW * 0.12f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                drawText(name, cx, y - boxH / 2 - cellH * 0.03f, p)
            }

            // 박스 내부: 프리셋 (상단)
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    this.color = color.toArgb()
                    textSize = cellW * 0.11f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(preset, cx, y - cellH * 0.02f, p)
            }

            // 박스 내부: 타이머 현재값 (하단)
            if (currentMs > 0) {
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        this.color = COLOR_TIMER_CURRENT.toArgb()
                        textSize = cellW * 0.11f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    val secStr = String.format("%.1fs", currentMs / 1000.0)
                    drawText(secStr, cx, y + cellH * 0.12f, p)
                }
            }

            // 박스 내부: 카운터 현재값 (하단)
            if (counterVal > 0) {
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        this.color = COLOR_TIMER_CURRENT.toArgb()
                        textSize = cellW * 0.11f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    drawText("$counterVal", cx, y + cellH * 0.12f, p)
                }
            }

            // ── 이름 (아래) ──
            if (nameText.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = COLOR_NAME_TEXT.toArgb()
                        textSize = cellW * 0.10f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(nameText, cx, y + boxH / 2 + cellH * 0.12f, paint)
                }
            }
        }
    }

    private fun drawFunctionBlock(
        scope: DrawScope, x: Float, y: Float, cellW: Float, cellH: Float,
        isActive: Boolean, mnemonic: String, op1: String, op2: String
    ) {
        val color = if (isActive) COLOR_FUNC_BLOCK else COLOR_FUNC_BLOCK.copy(alpha = 0.5f)
        val boxW = cellW * 0.85f
        val boxH = cellH * 0.6f
        val cx = x + cellW / 2f

        scope.apply {
            drawLine(color, Offset(x, y), Offset(cx - boxW / 2, y), strokeWidth = 2f)
            drawLine(color, Offset(cx + boxW / 2, y), Offset(x + cellW, y), strokeWidth = 2f)
            drawRect(color, Offset(cx - boxW / 2, y - boxH / 2), Size(boxW, boxH), style = Stroke(2f))

            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    this.color = color.toArgb()
                    textSize = cellW * 0.12f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                drawText(mnemonic, cx, y - cellH * 0.06f, p)
                p.isFakeBoldText = false
                p.textSize = cellW * 0.1f
                if (op1.isNotEmpty()) drawText(op1, cx, y + cellH * 0.06f, p)
                if (op2.isNotEmpty()) drawText(op2, cx, y + cellH * 0.17f, p)
            }
        }
    }

    private fun drawHLine(scope: DrawScope, x: Float, y: Float, cellW: Float, isActive: Boolean) {
        val color = if (isActive) COLOR_ACTIVE else COLOR_LINE
        scope.drawLine(color, Offset(x, y), Offset(x + cellW, y), strokeWidth = if (isActive) 3f else 2f)
    }

    private fun drawEmptyCell(scope: DrawScope, x: Float, y: Float, cellW: Float) {
        scope.drawLine(
            COLOR_EMPTY,
            Offset(x + 4f, y),
            Offset(x + cellW - 4f, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
    }

    fun drawVerticalConnection(
        scope: DrawScope,
        x: Float, yFrom: Float, yTo: Float,
        isActive: Boolean
    ) {
        val color = if (isActive) COLOR_VERTICAL_ACTIVE else COLOR_VERTICAL
        scope.drawLine(color, Offset(x, yFrom), Offset(x, yTo), strokeWidth = 3f)
    }
}
