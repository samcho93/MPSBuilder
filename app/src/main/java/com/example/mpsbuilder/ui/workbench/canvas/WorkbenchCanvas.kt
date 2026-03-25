package com.example.mpsbuilder.ui.workbench.canvas

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState
import com.example.mpsbuilder.ui.workbench.model.WorkbenchLayout
import com.example.mpsbuilder.ui.workbench.model.Workpiece
import com.example.mpsbuilder.ui.workbench.widgets.WidgetRenderer
import com.example.mpsbuilder.ui.workbench.workpiece.WorkpieceRenderer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 터치 시작 시 히트 판정 결과 */
private enum class DragMode { PAN, WIDGET_DRAG, ROTATE, RESIZE, BLOCK_SELECT }

@Composable
fun WorkbenchCanvas(
    layout: WorkbenchLayout,
    workpieces: List<Workpiece>,
    selectedId: String?,
    selectedIds: Set<String> = emptySet(),
    simulationMemory: Map<String, Boolean>,
    isTestMode: Boolean = false,
    onWidgetSelect: (String?) -> Unit,
    onWidgetToggleSelect: (String) -> Unit = {},
    onBlockSelect: (left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
    onWidgetTap: (String) -> Unit = {},
    onWidgetPressDown: (String) -> Unit = {},
    onWidgetPressUp: (String) -> Unit = {},
    onWidgetMove: (id: String, dx: Float, dy: Float) -> Unit,
    onWidgetRotate: (id: String, deltaDeg: Float) -> Unit = { _, _ -> },
    onWidgetScaleAbsolute: (id: String, absoluteScale: Float) -> Unit = { _, _ -> },
    cylinderProgress: Map<String, Float> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset(50f, 50f)) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val currentWidgets by rememberUpdatedState(layout.placedWidgets)
    val currentSelectedId by rememberUpdatedState(selectedId)
    val currentIsTestMode by rememberUpdatedState(isTestMode)
    val currentOnSelect by rememberUpdatedState(onWidgetSelect)
    val currentOnTap by rememberUpdatedState(onWidgetTap)
    val currentOnPressDown by rememberUpdatedState(onWidgetPressDown)
    val currentOnPressUp by rememberUpdatedState(onWidgetPressUp)
    val currentOnMove by rememberUpdatedState(onWidgetMove)
    val currentOnRotate by rememberUpdatedState(onWidgetRotate)
    val currentOnScaleAbs by rememberUpdatedState(onWidgetScaleAbsolute)
    val currentOnToggleSelect by rememberUpdatedState(onWidgetToggleSelect)
    val currentOnBlockSelect by rememberUpdatedState(onBlockSelect)

    // 블록 선택 사각형 (화면 좌표)
    var blockSelectStart by remember { mutableStateOf<Offset?>(null) }
    var blockSelectEnd by remember { mutableStateOf<Offset?>(null) }

    // 컨베이어 벨트 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "belt")
    val beltAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "beltAnim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()
                        val startPos = firstDown.position

                        // ── 히트 판정: 핸들 → 위젯 → 빈 공간
                        val selId = currentSelectedId
                        val selWidget = selId?.let { id -> currentWidgets.find { it.id == id } }

                        var dragMode = DragMode.PAN
                        var targetId: String? = null

                        // 선택된 위젯이 있으면 핸들 히트 먼저 체크
                        if (selWidget != null) {
                            val handleHit = hitTestHandles(
                                startPos, selWidget, panOffset, zoomScale
                            )
                            when (handleHit) {
                                DragMode.ROTATE -> {
                                    dragMode = DragMode.ROTATE
                                    targetId = selWidget.id
                                }
                                DragMode.RESIZE -> {
                                    dragMode = DragMode.RESIZE
                                    targetId = selWidget.id
                                }
                                else -> {} // 핸들 아님 → 아래에서 위젯/빈공간 판정
                            }
                        }

                        // 핸들이 아니면 위젯 히트 체크
                        if (dragMode == DragMode.PAN) {
                            val hitWidget = findWidgetAt(
                                startPos, currentWidgets, panOffset, zoomScale
                            )
                            if (hitWidget != null) {
                                dragMode = DragMode.WIDGET_DRAG
                                targetId = hitWidget.id
                            }
                        }

                        // 테스트 모드: 위젯 press down 즉시 발행
                        if (currentIsTestMode && targetId != null) {
                            currentOnPressDown(targetId!!)
                        }

                        // ── 드래그 시작 시 고정 스냅샷 (ROTATE/RESIZE용)
                        val startCenterX: Float
                        val startCenterY: Float
                        val startScale: Float
                        val startDist: Float

                        if (selWidget != null && (dragMode == DragMode.ROTATE || dragMode == DragMode.RESIZE)) {
                            val wSize = WidgetRenderer.getWidgetSize(selWidget)
                            val sW = wSize.width * zoomScale
                            val sH = wSize.height * zoomScale
                            startCenterX = (selWidget.positionX + panOffset.x) * zoomScale + sW / 2f
                            startCenterY = (selWidget.positionY + panOffset.y) * zoomScale + sH / 2f
                            startScale = selWidget.scaleFactor
                            startDist = sqrt(
                                (startPos.x - startCenterX) * (startPos.x - startCenterX) +
                                        (startPos.y - startCenterY) * (startPos.y - startCenterY)
                            ).coerceAtLeast(1f)
                        } else {
                            startCenterX = 0f; startCenterY = 0f; startScale = 1f; startDist = 1f
                        }

                        var prevAngle = if (dragMode == DragMode.ROTATE) {
                            Math.toDegrees(
                                atan2(
                                    (startPos.y - startCenterY).toDouble(),
                                    (startPos.x - startCenterX).toDouble()
                                )
                            ).toFloat()
                        } else 0f

                        var isPinching = false
                        var totalDragDist = 0f
                        val hitWidgetForTap = if (dragMode == DragMode.WIDGET_DRAG)
                            currentWidgets.find { it.id == targetId } else null

                        // ── 이벤트 루프
                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val anyPressed = changes.any { it.pressed }
                            val pointerCount = changes.count { it.pressed }

                            if (pointerCount >= 2) {
                                isPinching = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                zoomScale = (zoomScale * zoom).coerceIn(0.3f, 3f)
                                panOffset += pan / zoomScale
                                changes.forEach { it.consume() }
                            } else if (pointerCount >= 1 || changes.size == 1) {
                                val change = changes.firstOrNull { it.pressed } ?: changes.first()
                                val dragDelta = change.positionChange()

                                if (dragDelta != Offset.Zero && change.pressed) {
                                    totalDragDist += abs(dragDelta.x) + abs(dragDelta.y)

                                    when (dragMode) {
                                        DragMode.WIDGET_DRAG -> {
                                            targetId?.let {
                                                currentOnMove(
                                                    it,
                                                    dragDelta.x / zoomScale,
                                                    dragDelta.y / zoomScale
                                                )
                                            }
                                        }
                                        DragMode.ROTATE -> {
                                            val pos = change.position
                                            val newAngle = Math.toDegrees(
                                                atan2(
                                                    (pos.y - startCenterY).toDouble(),
                                                    (pos.x - startCenterX).toDouble()
                                                )
                                            ).toFloat()
                                            val delta = newAngle - prevAngle
                                            if (abs(delta) < 180f) {
                                                targetId?.let { currentOnRotate(it, delta) }
                                            }
                                            prevAngle = newAngle
                                        }
                                        DragMode.RESIZE -> {
                                            val pos = change.position
                                            val curDist = sqrt(
                                                (pos.x - startCenterX) * (pos.x - startCenterX) +
                                                        (pos.y - startCenterY) * (pos.y - startCenterY)
                                            )
                                            val target = (startScale * curDist / startDist)
                                                .coerceIn(0.3f, 3.0f)
                                            targetId?.let { currentOnScaleAbs(it, target) }
                                        }
                                        DragMode.BLOCK_SELECT -> {
                                            blockSelectEnd = change.position
                                        }
                                        DragMode.PAN -> {
                                            if (!isPinching && !currentIsTestMode) {
                                                // 빈 공간 드래그 → 블록 선택 시작
                                                if (totalDragDist > 10f && blockSelectStart == null) {
                                                    dragMode = DragMode.BLOCK_SELECT
                                                    blockSelectStart = startPos
                                                    blockSelectEnd = change.position
                                                } else {
                                                    panOffset += dragDelta / zoomScale
                                                }
                                            } else {
                                                panOffset += dragDelta / zoomScale
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        } while (anyPressed)

                        // 테스트 모드 press up
                        if (currentIsTestMode && hitWidgetForTap != null && !isPinching) {
                            currentOnPressUp(hitWidgetForTap.id)
                        }

                        // 블록 선택 완료
                        if (dragMode == DragMode.BLOCK_SELECT && blockSelectStart != null && blockSelectEnd != null) {
                            val s = blockSelectStart!!
                            val e = blockSelectEnd!!
                            val l = minOf(s.x, e.x) / zoomScale - panOffset.x
                            val t = minOf(s.y, e.y) / zoomScale - panOffset.y
                            val r = maxOf(s.x, e.x) / zoomScale - panOffset.x
                            val b = maxOf(s.y, e.y) / zoomScale - panOffset.y
                            currentOnBlockSelect(l, t, r, b)
                            blockSelectStart = null
                            blockSelectEnd = null
                        } else {
                            blockSelectStart = null
                            blockSelectEnd = null
                        }

                        // 탭 판정
                        val wasTap = !isPinching && totalDragDist < 20f && dragMode != DragMode.BLOCK_SELECT
                        if (wasTap) {
                            if (currentIsTestMode && hitWidgetForTap != null) {
                                currentOnTap(hitWidgetForTap.id)
                            } else {
                                currentOnSelect(hitWidgetForTap?.id)
                            }
                        }
                    }
                }
        ) {
            // 1. 배경 + 격자
            WorkbenchRenderer.drawBackground(this, layout, panOffset, zoomScale)

            // 2. 위젯 렌더링
            layout.placedWidgets
                .sortedBy { it.zOrder }
                .forEach { widget ->
                    withTransform({
                        val cx = (widget.positionX + panOffset.x) * zoomScale
                        val cy = (widget.positionY + panOffset.y) * zoomScale
                        translate(cx, cy)
                        val wSize = WidgetRenderer.getWidgetSize(widget)
                        val pivotX = wSize.width * zoomScale / 2f
                        val pivotY = wSize.height * zoomScale / 2f
                        rotate(widget.rotationDeg, Offset(pivotX, pivotY))
                        scale(zoomScale, zoomScale, Offset.Zero)
                    }) {
                        WidgetRenderer.draw(
                            scope = this,
                            widget = widget,
                            memory = simulationMemory,
                            isSelected = (widget.id == selectedId || widget.id in selectedIds),
                            beltAnimOffset = beltAnim,
                            cylinderProgress = cylinderProgress[widget.id] ?: -1f
                        )
                    }
                }

            // 3. 공작물 렌더링
            workpieces.forEach { wp ->
                WorkpieceRenderer.draw(this, wp, panOffset, zoomScale)
            }

            // 4. 블록 선택 사각형
            val bs = blockSelectStart
            val be = blockSelectEnd
            if (bs != null && be != null) {
                val l = minOf(bs.x, be.x)
                val t = minOf(bs.y, be.y)
                val r = maxOf(bs.x, be.x)
                val b = maxOf(bs.y, be.y)
                drawRect(
                    color = Color(0x332196F3),
                    topLeft = Offset(l, t),
                    size = androidx.compose.ui.geometry.Size(r - l, b - t)
                )
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(l, t),
                    size = androidx.compose.ui.geometry.Size(r - l, b - t),
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                )
            }
        }

        // 오버레이 (순수 그리기 — 터치 없음)
        selectedId?.let { id ->
            layout.placedWidgets.find { it.id == id }?.let { widget ->
                WidgetOverlay(widget = widget, panOffset = panOffset, zoom = zoomScale)
            }
        }
    }
}

// ── 선택된 위젯의 회전바/모서리 핸들 히트 판정
private fun hitTestHandles(
    tapOffset: Offset,
    widget: PlacedWidgetState,
    panOffset: Offset,
    zoomScale: Float
): DragMode {
    val wSize = WidgetRenderer.getWidgetSize(widget)
    val sW = wSize.width * zoomScale
    val sH = wSize.height * zoomScale
    val wx = (widget.positionX + panOffset.x) * zoomScale
    val wy = (widget.positionY + panOffset.y) * zoomScale
    val pivotX = sW / 2f
    val pivotY = sH / 2f

    // 화면 좌표 → 위젯 로컬 좌표 (회전 역변환)
    val rad = -Math.toRadians(widget.rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val dx = tapOffset.x - (wx + pivotX)
    val dy = tapOffset.y - (wy + pivotY)
    val lx = dx * cosR - dy * sinR + pivotX
    val ly = dx * sinR + dy * cosR + pivotY

    val pad = 8f
    val barH = 22f
    val handleHitR = 30f

    // 회전 바 영역 체크 (위젯 상단 위)
    if (lx in -pad - 15f..sW + pad + 15f && ly in -pad - barH - 15f..-pad + 5f) {
        return DragMode.ROTATE
    }

    // 네 모서리 핸들 체크
    val corners = listOf(
        Offset(-pad, -pad),
        Offset(sW + pad, -pad),
        Offset(-pad, sH + pad),
        Offset(sW + pad, sH + pad),
    )
    for (c in corners) {
        val dist = sqrt((lx - c.x) * (lx - c.x) + (ly - c.y) * (ly - c.y))
        if (dist < handleHitR) return DragMode.RESIZE
    }

    return DragMode.PAN  // 핸들에 해당 없음
}

// ── 위젯 히트 판정 (회전 역변환)
private fun findWidgetAt(
    tapOffset: Offset,
    widgets: List<PlacedWidgetState>,
    panOffset: Offset,
    zoomScale: Float
): PlacedWidgetState? {
    val hitPad = 16f
    return widgets.sortedByDescending { it.zOrder }.firstOrNull { widget ->
        val wSize = WidgetRenderer.getWidgetSize(widget)
        val cx = (widget.positionX + panOffset.x) * zoomScale
        val cy = (widget.positionY + panOffset.y) * zoomScale
        val pivotX = wSize.width * zoomScale / 2f
        val pivotY = wSize.height * zoomScale / 2f

        var lx = tapOffset.x - cx
        var ly = tapOffset.y - cy
        lx -= pivotX; ly -= pivotY
        val radians = -Math.toRadians(widget.rotationDeg.toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()
        val rx = lx * cosR - ly * sinR
        val ry = lx * sinR + ly * cosR
        lx = rx + pivotX; ly = ry + pivotY
        lx /= zoomScale; ly /= zoomScale

        lx >= -hitPad && lx <= wSize.width + hitPad &&
                ly >= -hitPad && ly <= wSize.height + hitPad
    }
}
