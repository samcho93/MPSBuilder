package com.example.mpsbuilder.ui.workbench.widgets

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.mpsbuilder.ui.workbench.model.ConveyorDirection
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState
import com.example.mpsbuilder.ui.workbench.model.SensorType
import com.example.mpsbuilder.ui.workbench.model.SignalTowerTiers
import com.example.mpsbuilder.ui.workbench.model.SwitchType
import com.example.mpsbuilder.ui.workbench.model.WidgetType
import com.example.mpsbuilder.ui.workbench.model.WorkpieceType

object WidgetRenderer {

    private const val BASE_WIDTH = 80f
    private const val BASE_HEIGHT = 60f

    fun getWidgetSize(widget: PlacedWidgetState): Size {
        val s = widget.scaleFactor
        return when (widget.widgetType) {
            WidgetType.CONVEYOR -> {
                val config = widget.conveyorConfig
                if (config != null) Size(config.size.lengthDp * s, config.size.widthDp * s)
                else Size(280f * s, 64f * s)
            }
            WidgetType.CYLINDER -> Size(120f * s, 50f * s)
            WidgetType.MOTOR -> Size(70f * s, 70f * s)
            WidgetType.SENSOR -> Size(24f * s, 50f * s)
            WidgetType.BUZZER -> Size(50f * s, 50f * s)
            WidgetType.STORAGE_BIN -> Size(90f * s, 80f * s)
            WidgetType.SIGNAL_TOWER -> Size(30f * s, (20f + widget.signalTowerTiers.count * 22f) * s)
            else -> Size(BASE_WIDTH * s, BASE_HEIGHT * s)
        }
    }

    fun draw(
        scope: DrawScope,
        widget: PlacedWidgetState,
        memory: Map<String, Boolean>,
        isSelected: Boolean,
        beltAnimOffset: Float = 0f,
        cylinderProgress: Float = -1f  // -1 = 사용 안함, 0~1 = 부드러운 전후진
    ) {
        val isOn = memory[widget.linkedIOAddress] ?: false
        val size = getWidgetSize(widget)

        // IO 상태별 하이라이트 수집
        val activeAddrs = widget.ioSlots
            .filter { it.address.isNotBlank() && memory[it.address] == true }
            .map { it.address }
            .toSet()

        with(scope) {
            when (widget.widgetType) {
                WidgetType.CYLINDER -> drawCylinder(size, isOn, cylinderProgress)
                WidgetType.LAMP -> drawLamp(size, isOn, widget.widgetColor)
                WidgetType.PUSH_BUTTON -> drawPushButton(size, isOn, widget.widgetColor, widget.switchType)
                WidgetType.MOTOR -> drawMotor(size, isOn)
                WidgetType.SENSOR -> drawSensor(size, isOn, widget.sensorType)
                WidgetType.VALVE -> drawValve(size, isOn)
                WidgetType.CONVEYOR -> drawConveyorTopView(size, widget, isOn, beltAnimOffset)
                WidgetType.WORKPIECE_SUPPLIER -> drawSupplier(size, widget.workpieceStack)
                WidgetType.BUZZER -> drawBuzzer(size, isOn, widget.widgetColor)
                WidgetType.STORAGE_BIN -> drawStorageBin(size, widget.storedWorkpieces)
                WidgetType.SIGNAL_TOWER -> drawSignalTower(size, widget, memory)
            }

            // 선택 테두리 (파란 박스)
            if (isSelected) {
                val pad = 5f
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(-pad, -pad),
                    size = Size(size.width + pad * 2, size.height + pad * 2),
                    style = Stroke(width = 2.5f)
                )
                // 네 모서리 점
                val dotR = 4f
                listOf(
                    Offset(-pad, -pad),
                    Offset(size.width + pad, -pad),
                    Offset(-pad, size.height + pad),
                    Offset(size.width + pad, size.height + pad)
                ).forEach { corner ->
                    drawCircle(Color(0xFF2196F3), dotR, corner)
                }
            }

            // IO 하이라이트 (활성 IO 주소 표시)
            if (activeAddrs.isNotEmpty()) {
                val hlText = activeAddrs.joinToString(" ")
                drawContext.canvas.nativeCanvas.drawText(
                    hlText,
                    size.width / 2f,
                    -6f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(76, 175, 80)  // 녹색
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                )
            }

            // 레이블
            if (widget.label.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(
                    widget.label,
                    size.width / 2f,
                    size.height + 14f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 12f
                        isAntiAlias = true
                    }
                )
            }
        }
    }

    // ── 실린더 (progress: 0=후진, 1=전진, -1=isOn으로 즉시전환)
    private fun DrawScope.drawCylinder(size: Size, isOn: Boolean, progress: Float = -1f) {
        val bodyColor = Color(0xFF78909C)
        val rodColor = Color(0xFFB0BEC5)
        val p = if (progress >= 0f) progress else if (isOn) 1f else 0f
        val rodExtend = size.width * 0.05f + size.width * 0.30f * p

        drawRoundRect(bodyColor, Offset(0f, size.height * 0.15f),
            Size(size.width * 0.6f, size.height * 0.7f), CornerRadius(4f))
        drawRoundRect(rodColor, Offset(size.width * 0.6f, size.height * 0.35f),
            Size(rodExtend, size.height * 0.3f), CornerRadius(2f))
        drawCircle(Color(0xFF546E7A), size.height * 0.18f,
            Offset(size.width * 0.6f + rodExtend, size.height * 0.5f))
    }

    // ── 산업용 파일럿 램프 (원형 렌즈 + 금속 베젤 + 단자대)
    private fun DrawScope.drawLamp(size: Size, isOn: Boolean, userColor: Long = 0) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val lensColor = if (userColor != 0L) Color(userColor) else Color(0xFFFFEB3B)
        val lensOn = lensColor
        val lensOff = if (userColor != 0L) Color(userColor).copy(alpha = 0.25f) else Color(0xFF757575)

        // ── 1. 단자대/본체 (하단 사각형 — 패널 삽입부)
        val bodyTop = h * 0.6f
        val bodyW = w * 0.55f
        drawRoundRect(
            Color(0xFF37474F),
            Offset(cx - bodyW / 2f, bodyTop),
            Size(bodyW, h - bodyTop),
            CornerRadius(2f)
        )
        // 단자 핀 2개
        val pinW = 2f
        val pinH = h * 0.08f
        drawRect(Color(0xFFBDBDBD), Offset(cx - bodyW * 0.25f, h - pinH), Size(pinW, pinH))
        drawRect(Color(0xFFBDBDBD), Offset(cx + bodyW * 0.25f - pinW, h - pinH), Size(pinW, pinH))

        // ── 2. 금속 베젤 (크롬 링)
        val bezelCY = h * 0.35f
        val bezelR = minOf(w, h * 0.7f) * 0.42f
        // 외곽 링 (어두운 금속)
        drawCircle(Color(0xFF78909C), bezelR + 3f, Offset(cx, bezelCY))
        // 베젤 하이라이트 (상단 반사광)
        drawCircle(Color(0xFF90A4AE), bezelR + 2f, Offset(cx, bezelCY))
        drawCircle(Color(0xFF607D8B), bezelR + 2f, Offset(cx, bezelCY), style = Stroke(1.5f))

        // ── 3. 렌즈 (발광부)
        val lensR = bezelR - 1f
        val currentLensColor = if (isOn) lensOn else lensOff

        // 렌즈 본체
        drawCircle(currentLensColor, lensR, Offset(cx, bezelCY))

        if (isOn) {
            // 글로우 효과 (2단계)
            drawCircle(lensOn.copy(alpha = 0.25f), lensR * 1.6f, Offset(cx, bezelCY))
            drawCircle(lensOn.copy(alpha = 0.12f), lensR * 2.0f, Offset(cx, bezelCY))
            // 밝은 중심
            drawCircle(Color.White.copy(alpha = 0.5f), lensR * 0.3f,
                Offset(cx - lensR * 0.15f, bezelCY - lensR * 0.15f))
        } else {
            // 소등 시 약간의 반사
            drawCircle(Color.White.copy(alpha = 0.08f), lensR * 0.4f,
                Offset(cx - lensR * 0.15f, bezelCY - lensR * 0.15f))
        }

        // 렌즈 테두리 (유리 느낌)
        drawCircle(Color.Black.copy(alpha = 0.15f), lensR, Offset(cx, bezelCY), style = Stroke(1f))

        // ── 4. 베젤-본체 연결부
        val neckW = bodyW * 0.7f
        val neckTop = bezelCY + bezelR - 2f
        drawRect(Color(0xFF546E7A), Offset(cx - neckW / 2f, neckTop),
            Size(neckW, bodyTop - neckTop + 2f))
    }

    // ── 스위치 (타입별 디자인)
    private fun DrawScope.drawPushButton(
        size: Size, isOn: Boolean, userColor: Long = 0, switchType: SwitchType = SwitchType.PUSH
    ) {
        val accentColor = if (userColor != 0L) Color(userColor) else Color(0xFFF44336)

        when (switchType) {
            SwitchType.PUSH -> drawPushSwitch(size, isOn, accentColor)
            SwitchType.TOGGLE -> drawToggleSwitch(size, isOn, accentColor)
            SwitchType.SELECT -> drawSelectSwitch(size, isOn, accentColor)
        }
    }

    // ── 푸시 스위치: 사각 베이스 + 원형 버튼 (누르면 들어감)
    private fun DrawScope.drawPushSwitch(size: Size, isOn: Boolean, color: Color) {
        val pressedOffset = if (isOn) 3f else 0f
        // 베이스
        drawRoundRect(Color(0xFF424242), size = size, cornerRadius = CornerRadius(6f))
        val pad = size.width * 0.12f
        drawRoundRect(Color(0xFF333333), Offset(pad, pad),
            Size(size.width - pad * 2, size.height - pad * 2), CornerRadius(4f))
        // 그림자 (눌리지 않았을 때)
        if (!isOn) {
            val btnR = minOf(size.width, size.height) * 0.3f
            drawCircle(Color.Black.copy(alpha = 0.3f), btnR,
                Offset(size.width / 2f, size.height / 2f + 3f))
        }
        // 원형 버튼
        val btnRadius = minOf(size.width, size.height) * 0.3f
        val center = Offset(size.width / 2f, size.height / 2f + pressedOffset)
        drawCircle(color, btnRadius, center)
        drawCircle(Color.White.copy(alpha = 0.25f), btnRadius * 0.6f,
            Offset(center.x - btnRadius * 0.1f, center.y - btnRadius * 0.15f))
        drawCircle(Color.Black.copy(alpha = 0.2f), btnRadius, center, style = Stroke(1.5f))
    }

    // ── 토글 스위치: 레버가 좌/우로 넘어가는 형태
    private fun DrawScope.drawToggleSwitch(size: Size, isOn: Boolean, color: Color) {
        val w = size.width
        val h = size.height
        // 베이스
        drawRoundRect(Color(0xFF424242), size = size, cornerRadius = CornerRadius(6f))
        // 트랙 (가로 타원)
        val trackH = h * 0.35f
        val trackY = h * 0.5f - trackH / 2f
        val trackPad = w * 0.12f
        val trackW = w - trackPad * 2
        val trackColor = if (isOn) color.copy(alpha = 0.4f) else Color(0xFF555555)
        drawRoundRect(trackColor, Offset(trackPad, trackY),
            Size(trackW, trackH), CornerRadius(trackH / 2f))
        // 레버 (원형 노브)
        val knobR = trackH * 0.55f
        val knobCY = h * 0.5f
        val knobCX = if (isOn) trackPad + trackW - knobR - 2f else trackPad + knobR + 2f
        // 노브 그림자
        drawCircle(Color.Black.copy(alpha = 0.2f), knobR, Offset(knobCX + 1f, knobCY + 1f))
        // 노브
        drawCircle(if (isOn) color else Color(0xFFBDBDBD), knobR, Offset(knobCX, knobCY))
        drawCircle(Color.White.copy(alpha = 0.3f), knobR * 0.5f,
            Offset(knobCX - knobR * 0.1f, knobCY - knobR * 0.15f))
        drawCircle(Color.Black.copy(alpha = 0.15f), knobR, Offset(knobCX, knobCY), style = Stroke(1f))
        // ON/OFF 텍스트
        drawContext.canvas.nativeCanvas.drawText(
            if (isOn) "ON" else "OFF",
            w / 2f, h * 0.92f,
            android.graphics.Paint().apply {
                this.color = if (isOn) android.graphics.Color.rgb(76, 175, 80)
                else android.graphics.Color.GRAY
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = h * 0.15f; isAntiAlias = true; isFakeBoldText = true
            }
        )
    }

    // ── 선택 스위치: 열쇠 형태의 로터리 (2포지션, 45도 회전)
    private fun DrawScope.drawSelectSwitch(size: Size, isOn: Boolean, color: Color) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.45f
        // 베이스
        drawRoundRect(Color(0xFF424242), size = size, cornerRadius = CornerRadius(6f))
        // 셀렉터 원판
        val plateR = minOf(w, h) * 0.32f
        drawCircle(Color(0xFF616161), plateR, Offset(cx, cy))
        drawCircle(Color(0xFF757575), plateR, Offset(cx, cy), style = Stroke(2f))
        // 포지션 표시 (좌상, 우상)
        val dotR = 3f
        val posAngle0 = -45.0  // OFF 위치
        val posAngle1 = 45.0   // ON 위치
        listOf(posAngle0 to !isOn, posAngle1 to isOn).forEach { (angle, active) ->
            val rad = Math.toRadians(angle - 90)
            val px = cx + (plateR + 8f) * kotlin.math.cos(rad).toFloat()
            val py = cy + (plateR + 8f) * kotlin.math.sin(rad).toFloat()
            drawCircle(
                if (active) color else Color(0xFF9E9E9E),
                dotR, Offset(px, py)
            )
        }
        // 핸들 (회전하는 레버)
        val handleAngle = if (isOn) 45f else -45f
        val handleRad = Math.toRadians((handleAngle - 90).toDouble())
        val handleLen = plateR * 0.85f
        val hx = cx + handleLen * kotlin.math.cos(handleRad).toFloat()
        val hy = cy + handleLen * kotlin.math.sin(handleRad).toFloat()
        // 핸들 막대
        drawLine(Color(0xFFE0E0E0), Offset(cx, cy), Offset(hx, hy), strokeWidth = 4f)
        // 핸들 끝 노브
        drawCircle(color, 4f, Offset(hx, hy))
        // 중심점
        drawCircle(Color(0xFF424242), 3f, Offset(cx, cy))
        // 상태 텍스트
        drawContext.canvas.nativeCanvas.drawText(
            if (isOn) "ON" else "OFF",
            cx, h * 0.92f,
            android.graphics.Paint().apply {
                this.color = if (isOn) android.graphics.Color.rgb(76, 175, 80)
                else android.graphics.Color.GRAY
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = h * 0.15f; isAntiAlias = true; isFakeBoldText = true
            }
        )
    }

    // ── 모터
    private fun DrawScope.drawMotor(size: Size, isOn: Boolean) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) * 0.4f

        drawCircle(Color(0xFF5C6BC0), r, center)
        drawCircle(if (isOn) Color(0xFF66BB6A) else Color(0xFF757575), r * 0.6f, center)
        drawLine(Color(0xFFB0BEC5), center, Offset(center.x + r * 0.8f, center.y), 4f)
        drawContext.canvas.nativeCanvas.drawText("M", center.x, center.y + 5f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.6f; isFakeBoldText = true; isAntiAlias = true
            })
    }

    // ── 센서 (직사각형, 방향 있음, 감지부 상단)
    //    광전: 본체 파랑계열 / 감지부 빨강
    //    근접: 본체 갈색계열 / 감지부 금색
    private fun DrawScope.drawSensor(size: Size, isOn: Boolean, type: SensorType) {
        val w = size.width
        val h = size.height
        val detectH = h * 0.3f  // 상단 감지부 높이

        // 본체 색상
        val bodyColor = when (type) {
            SensorType.PHOTO -> Color(0xFF1565C0)      // 파랑
            SensorType.PROXIMITY -> Color(0xFF5D4037)   // 갈색
        }
        // 감지부 색상 (OFF/ON 구분)
        val detectOffColor = when (type) {
            SensorType.PHOTO -> Color(0xFFB71C1C)       // 어두운 빨강
            SensorType.PROXIMITY -> Color(0xFFBF8C00)   // 어두운 금색
        }
        val detectOnColor = when (type) {
            SensorType.PHOTO -> Color(0xFFFF1744)       // 밝은 빨강
            SensorType.PROXIMITY -> Color(0xFFFFD600)   // 밝은 금색
        }

        // 본체
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(0f, detectH),
            size = Size(w, h - detectH),
            cornerRadius = CornerRadius(3f)
        )

        // 감지부 (상단)
        val dColor = if (isOn) detectOnColor else detectOffColor
        drawRoundRect(
            color = dColor,
            topLeft = Offset(0f, 0f),
            size = Size(w, detectH),
            cornerRadius = CornerRadius(3f)
        )

        // 감지 하이라이트 글로우
        if (isOn) {
            drawRoundRect(
                color = dColor.copy(alpha = 0.35f),
                topLeft = Offset(-4f, -6f),
                size = Size(w + 8f, detectH + 10f),
                cornerRadius = CornerRadius(6f)
            )
            // 감지선 (위쪽으로 발사)
            drawLine(
                dColor.copy(alpha = 0.5f),
                Offset(w / 2f, 0f),
                Offset(w / 2f, -14f),
                strokeWidth = 2.5f
            )
            drawLine(
                dColor.copy(alpha = 0.3f),
                Offset(w * 0.25f, 0f),
                Offset(w * 0.25f, -8f),
                strokeWidth = 1.5f
            )
            drawLine(
                dColor.copy(alpha = 0.3f),
                Offset(w * 0.75f, 0f),
                Offset(w * 0.75f, -8f),
                strokeWidth = 1.5f
            )
        }

        // 센서 타입 약자
        val label = when (type) {
            SensorType.PHOTO -> "P"
            SensorType.PROXIMITY -> "N"
        }
        drawContext.canvas.nativeCanvas.drawText(
            label,
            w / 2f, h * 0.72f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = h * 0.22f
                isFakeBoldText = true
                isAntiAlias = true
            }
        )

        // 케이블 표시 (하단)
        drawLine(
            Color(0xFF424242),
            Offset(w / 2f, h),
            Offset(w / 2f, h + 8f),
            strokeWidth = 2f
        )
    }

    // ── 밸브
    private fun DrawScope.drawValve(size: Size, isOn: Boolean) {
        val c = if (isOn) Color(0xFF4CAF50) else Color(0xFFF44336)
        drawRect(Color(0xFF78909C), Offset(0f, size.height * 0.35f),
            Size(size.width * 0.2f, size.height * 0.3f))
        drawRect(Color(0xFF78909C), Offset(size.width * 0.8f, size.height * 0.35f),
            Size(size.width * 0.2f, size.height * 0.3f))
        drawPath(Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.2f)
            lineTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height * 0.8f); close()
        }, c)
        drawPath(Path().apply {
            moveTo(size.width * 0.8f, size.height * 0.2f)
            lineTo(size.width * 0.5f, size.height * 0.5f)
            lineTo(size.width * 0.8f, size.height * 0.8f); close()
        }, c)
    }

    // ── 컨베이어 (탑뷰 — 위에서 내려다보는 형상)
    private fun DrawScope.drawConveyorTopView(size: Size, widget: PlacedWidgetState, isOn: Boolean, beltAnimOffset: Float = 0f) {
        val w = size.width
        val h = size.height
        val config = widget.conveyorConfig ?: return

        val railThick = h * 0.12f          // 양쪽 사이드 레일 두께
        val railColor = Color(0xFF616161)
        val beltColor = Color(config.beltColor)
        val rollerColor = Color(0xFF9E9E9E)
        val rollerAxisColor = Color(0xFF757575)

        // ── 벨트 표면 (중앙 영역)
        drawRect(
            color = beltColor,
            topLeft = Offset(0f, railThick),
            size = Size(w, h - railThick * 2)
        )

        // ── 벨트 표면 줄무늬 (애니메이션 포함)
        val lineSpacing = w * 0.06f
        val lineColor = Color.Black.copy(alpha = if (isOn) 0.25f else 0.15f)
        val animShift = if (isOn) beltAnimOffset * lineSpacing else 0f
        var lx = lineSpacing + animShift % lineSpacing
        while (lx < w) {
            drawLine(lineColor, Offset(lx, railThick), Offset(lx, h - railThick), strokeWidth = 1.5f)
            lx += lineSpacing
        }

        // ── 롤러 (양 끝, 위에서 보면 가로 막대)
        val rollerW = w * 0.04f
        // 좌측 롤러
        drawRoundRect(rollerAxisColor, Offset(0f, railThick * 0.5f),
            Size(rollerW, h - railThick), CornerRadius(rollerW / 2))
        drawRoundRect(rollerColor, Offset(0f, railThick),
            Size(rollerW, h - railThick * 2), CornerRadius(rollerW / 2))
        // 우측 롤러
        drawRoundRect(rollerAxisColor, Offset(w - rollerW, railThick * 0.5f),
            Size(rollerW, h - railThick), CornerRadius(rollerW / 2))
        drawRoundRect(rollerColor, Offset(w - rollerW, railThick),
            Size(rollerW, h - railThick * 2), CornerRadius(rollerW / 2))

        // ── 사이드 레일 (위/아래 가로 막대)
        drawRect(railColor, Offset(0f, 0f), Size(w, railThick))
        drawRect(railColor, Offset(0f, h - railThick), Size(w, railThick))

        // ── 레일 하이라이트 (입체감)
        drawRect(Color.White.copy(alpha = 0.15f), Offset(0f, 0f), Size(w, railThick * 0.4f))
        drawRect(Color.White.copy(alpha = 0.15f),
            Offset(0f, h - railThick), Size(w, railThick * 0.4f))

        // ── 방향 화살표
        val arrowColor = if (isOn) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f)
        val cx = w / 2f
        val cy = h / 2f
        val arrowLen = w * 0.12f
        val arrowHead = h * 0.15f

        when (config.direction) {
            ConveyorDirection.RIGHT -> {
                drawLine(arrowColor, Offset(cx - arrowLen, cy), Offset(cx + arrowLen, cy), 3f)
                drawLine(arrowColor, Offset(cx + arrowLen, cy),
                    Offset(cx + arrowLen - arrowHead, cy - arrowHead), 3f)
                drawLine(arrowColor, Offset(cx + arrowLen, cy),
                    Offset(cx + arrowLen - arrowHead, cy + arrowHead), 3f)
            }
            ConveyorDirection.LEFT -> {
                drawLine(arrowColor, Offset(cx + arrowLen, cy), Offset(cx - arrowLen, cy), 3f)
                drawLine(arrowColor, Offset(cx - arrowLen, cy),
                    Offset(cx - arrowLen + arrowHead, cy - arrowHead), 3f)
                drawLine(arrowColor, Offset(cx - arrowLen, cy),
                    Offset(cx - arrowLen + arrowHead, cy + arrowHead), 3f)
            }
            ConveyorDirection.UP -> {
                drawLine(arrowColor, Offset(cx, cy + arrowLen), Offset(cx, cy - arrowLen), 3f)
                drawLine(arrowColor, Offset(cx, cy - arrowLen),
                    Offset(cx - arrowHead, cy - arrowLen + arrowHead), 3f)
                drawLine(arrowColor, Offset(cx, cy - arrowLen),
                    Offset(cx + arrowHead, cy - arrowLen + arrowHead), 3f)
            }
            ConveyorDirection.DOWN -> {
                drawLine(arrowColor, Offset(cx, cy - arrowLen), Offset(cx, cy + arrowLen), 3f)
                drawLine(arrowColor, Offset(cx, cy + arrowLen),
                    Offset(cx - arrowHead, cy + arrowLen - arrowHead), 3f)
                drawLine(arrowColor, Offset(cx, cy + arrowLen),
                    Offset(cx + arrowHead, cy + arrowLen - arrowHead), 3f)
            }
        }

        // ── 운전 중 표시 (녹색 테두리)
        if (isOn) {
            drawRect(
                Color(0xFF4CAF50),
                topLeft = Offset.Zero,
                size = Size(w, h),
                style = Stroke(width = 2f)
            )
        }
    }

    // ── 공작물 공급기 (스택 시각화)
    private fun DrawScope.drawSupplier(size: Size, stack: List<WorkpieceType>) {
        // 호퍼 본체
        drawPath(Path().apply {
            moveTo(size.width * 0.1f, 0f)
            lineTo(size.width * 0.9f, 0f)
            lineTo(size.width * 0.7f, size.height * 0.55f)
            lineTo(size.width * 0.3f, size.height * 0.55f)
            close()
        }, Color(0xFF8D6E63))

        // 스택된 공작물 (호퍼 내부에 위에서부터 쌓기)
        val maxVisible = 5
        val wpSize = size.width * 0.18f
        val startY = size.height * 0.08f
        stack.takeLast(maxVisible).reversed().forEachIndexed { idx, wpType ->
            val y = startY + idx * (wpSize + 2f)
            if (y + wpSize < size.height * 0.52f) {
                drawRoundRect(
                    color = Color(wpType.color),
                    topLeft = Offset(size.width * 0.5f - wpSize / 2f, y),
                    size = Size(wpSize, wpSize),
                    cornerRadius = CornerRadius(2f)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(size.width * 0.5f - wpSize / 2f, y),
                    size = Size(wpSize, wpSize),
                    cornerRadius = CornerRadius(2f),
                    style = Stroke(1f)
                )
            }
        }

        // 출구
        drawRect(Color(0xFF6D4C41), Offset(size.width * 0.35f, size.height * 0.55f),
            Size(size.width * 0.3f, size.height * 0.45f))

        // 수량 표시
        if (stack.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(
                "${stack.size}", size.width / 2f, size.height * 0.82f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = size.height * 0.2f; isAntiAlias = true; isFakeBoldText = true
                }
            )
        } else {
            drawContext.canvas.nativeCanvas.drawText(
                "▼", size.width / 2f, size.height * 0.85f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = size.height * 0.22f; isAntiAlias = true
                }
            )
        }
    }

    // ── 부저 (원형 스피커 모양)
    private fun DrawScope.drawBuzzer(size: Size, isOn: Boolean, userColor: Long = 0) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.4f
        val color = if (userColor != 0L) Color(userColor) else Color(0xFFFF9800)

        // 본체 (원형)
        drawCircle(Color(0xFF424242), r + 4f, Offset(cx, cy))
        drawCircle(Color(0xFF616161), r, Offset(cx, cy))
        // 스피커 콘
        drawCircle(if (isOn) color else Color(0xFF757575), r * 0.6f, Offset(cx, cy))
        drawCircle(Color(0xFF424242), r * 0.2f, Offset(cx, cy))
        // 음파 표시 (켜졌을 때)
        if (isOn) {
            val waveColor = color.copy(alpha = 0.4f)
            drawArc(waveColor, -45f, 90f, false,
                Offset(cx - r * 1.1f, cy - r * 1.1f), Size(r * 2.2f, r * 2.2f),
                style = Stroke(2f))
            drawArc(waveColor.copy(alpha = 0.25f), -45f, 90f, false,
                Offset(cx - r * 1.4f, cy - r * 1.4f), Size(r * 2.8f, r * 2.8f),
                style = Stroke(2f))
        }
        // 라벨
        drawContext.canvas.nativeCanvas.drawText(
            "♪", cx, cy + r * 0.15f,
            android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.5f; isAntiAlias = true
            }
        )
    }

    // ── 적재함 (위에서 본 상자 — 공작물 적재 표시)
    private fun DrawScope.drawStorageBin(size: Size, stored: List<WorkpieceType>) {
        val w = size.width
        val h = size.height
        val wallThick = 3f

        // 외벽
        drawRoundRect(Color(0xFF5D4037), size = size, cornerRadius = CornerRadius(4f))
        // 내부 공간
        drawRoundRect(Color(0xFF8D6E63),
            Offset(wallThick, wallThick),
            Size(w - wallThick * 2, h - wallThick * 2),
            CornerRadius(2f))

        // 적재된 공작물 표시 (실제 크기 = 36dp 기준, 적재함 스케일 반영)
        val realWpSize = 36f  // WorkpieceRenderer.WP_SIZE와 동일
        val gap = 3f
        val innerW = w - wallThick * 2 - 4f
        val cols = ((innerW) / (realWpSize + gap)).toInt().coerceAtLeast(1)
        stored.forEachIndexed { idx, wpType ->
            val col = idx % cols
            val row = idx / cols
            val px = wallThick + 2f + col * (realWpSize + gap)
            val py = wallThick + 2f + row * (realWpSize + gap)
            if (py + realWpSize < h - wallThick) {
                drawRoundRect(Color(wpType.color),
                    Offset(px, py), Size(realWpSize, realWpSize), CornerRadius(3f))
                drawRoundRect(Color.Black.copy(alpha = 0.25f),
                    Offset(px, py), Size(realWpSize, realWpSize), CornerRadius(3f), style = Stroke(1f))
            }
        }

        // 수량
        if (stored.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(
                "${stored.size}", w / 2f, h - 4f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = h * 0.15f; isAntiAlias = true; isFakeBoldText = true
                }
            )
        }
    }

    // ── 경광등 (수직 2/3/4단 — 위에서 아래로 빨강,노랑,녹색,파랑)
    private fun DrawScope.drawSignalTower(size: Size, widget: PlacedWidgetState, memory: Map<String, Boolean>) {
        val w = size.width
        val h = size.height
        val tiers = widget.signalTowerTiers.count
        val poleH = 14f * widget.scaleFactor
        val lightAreaH = h - poleH
        val lightH = lightAreaH / tiers
        val lightR = w * 0.4f

        // 기둥 (하단)
        val poleW = w * 0.3f
        drawRoundRect(Color(0xFF757575),
            Offset(w / 2f - poleW / 2f, lightAreaH),
            Size(poleW, poleH), CornerRadius(2f))

        // 각 단 색상
        val tierColors = listOf(
            0xFFF44336L, // 빨강
            0xFFFFEB3BL, // 노랑
            0xFF4CAF50L, // 녹색
            0xFF2196F3L, // 파랑
        )

        for (i in 0 until tiers) {
            val y = i * lightH
            val tColor = Color(tierColors.getOrElse(i) { 0xFF9E9E9EL })
            val ioAddr = widget.ioSlots.getOrNull(i)?.address?.takeIf { it.isNotBlank() }
            val isLit = ioAddr?.let { memory[it] } ?: false

            // 램프 본체
            drawRoundRect(Color(0xFF424242),
                Offset(0f, y), Size(w, lightH - 1f), CornerRadius(4f))
            // 렌즈 (ON: 밝게, OFF: 어둡게)
            val lensColor = if (isLit) tColor else tColor.copy(alpha = 0.25f)
            drawRoundRect(lensColor,
                Offset(2f, y + 2f), Size(w - 4f, lightH - 5f), CornerRadius(3f))
            // 글로우
            if (isLit) {
                drawRoundRect(tColor.copy(alpha = 0.2f),
                    Offset(-3f, y - 1f), Size(w + 6f, lightH + 1f), CornerRadius(5f))
            }
        }
    }
}
