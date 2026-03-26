package com.example.mpsbuilder.ui.ladder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.data.ladder.LadderElement
import com.example.mpsbuilder.data.ladder.LadderRung

/**
 * 래더 뷰어:
 * - 접점은 좌측 파워라인에, 출력은 우측 파워라인에 최대한 밀착
 * - 접점~출력 사이 연결선은 남은 공간을 채움
 * - 수평+수직 동시 확대/축소 + 수평 스크롤
 */
@Composable
fun LadderViewerCanvas(
    rungs: List<LadderRung>,
    memory: Map<String, Boolean>,
    ioLabels: Map<String, String>,
    timerValues: Map<Int, Int>,
    counterValues: Map<Int, Int>,
    zoomFactor: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rungs.isEmpty()) return

    val density = LocalDensity.current

    // ── 유효 열 + 각 열이 의미 있는지(element) vs 연결선(bridge)인지
    val colLayout = calcColumnLayout(rungs)
    val meaningfulCount = colLayout.count { it.isMeaningful }

    // ── 초기 줌: 8열 기준 수평 fit. 의미 있는 열이 8개 이상이면 더 축소
    //    기준 셀 폭 = containerW / targetCols (bus bar 제외)
    //    baseCellW(72dp) 대비 비율이 초기 줌
    val targetCols = maxOf(meaningfulCount, 8)
    // 초기 줌은 BoxWithConstraints 안에서 계산 (containerW 필요)

    val hScrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // 줌 컨트롤
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onZoomChange((zoomFactor - 0.15f).coerceAtLeast(0.2f)) }, Modifier.size(28.dp)) {
                Icon(Icons.Default.ZoomOut, "축소", Modifier.size(16.dp))
            }
            Text(
                "${(zoomFactor.coerceAtLeast(0.1f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { onZoomChange((zoomFactor + 0.15f).coerceAtMost(3f)) }, Modifier.size(28.dp)) {
                Icon(Icons.Default.ZoomIn, "확대", Modifier.size(16.dp))
            }
            IconButton(onClick = { onZoomChange(-1f) }, Modifier.size(28.dp)) {
                Icon(Icons.Default.FitScreen, "맞춤", Modifier.size(16.dp))
            }
        }

        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoomChange((zoomFactor.coerceAtLeast(0.2f) * zoom).coerceIn(0.2f, 3f))
                    }
                }
        ) {
            val containerW = with(density) { maxWidth.toPx() }

            // ── 접점 셀 폭 = baseCellW × zoomFactor
            val baseCellW = 72f * density.density
            val autoZoom = (containerW * 0.93f) / (targetCols * baseCellW)
            val effectiveZoom = if (zoomFactor < 0f) autoZoom.coerceIn(0.2f, 1.5f) else zoomFactor
            if (zoomFactor < 0f) onZoomChange(effectiveZoom)

            val elemCellW = baseCellW * effectiveZoom  // 의미 있는 셀의 절대 폭
            val busBarWFixed = containerW * 0.035f

            // 의미 있는 셀 전체 폭
            val totalElemW = meaningfulCount * elemCellW
            // 최소 bridge 폭 (연결선 보이게)
            val bridgeCount = colLayout.count { !it.isMeaningful }
            val bridgeMinW = elemCellW * 0.3f
            val totalBridgeMin = bridgeCount * bridgeMinW

            // 총 필요 폭 (bus bar 포함)
            val neededW = busBarWFixed * 2 + totalElemW + totalBridgeMin

            // 축소: 항상 containerW 이상 유지 (bridge가 남은 공간 흡수)
            // 확대: neededW > containerW 이면 스크롤
            val totalWidthPx = maxOf(containerW, neededW)
            val totalWidthDp = with(density) { totalWidthPx.toDp() }
            val busBarW = busBarWFixed
            val usableW = totalWidthPx - busBarW * 2

            // 셀 높이 = 셀 폭과 동일 비율
            val cellH = elemCellW
            val cellHDp = with(density) { cellH.toDp() }

            // ── 열 폭 계산: 의미 있는 셀은 elemCellW 고정, bridge는 남은 공간 채움
            val colPositions = calcColPositions(colLayout, usableW, elemCellW)

            LazyColumn(
                modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState)
            ) {
                itemsIndexed(rungs, key = { idx, r -> r.id }) { rungIdx, rung ->
                    val rungHeightDp = cellHDp * rung.rowCount + 4.dp

                    Canvas(modifier = Modifier.width(totalWidthDp).height(rungHeightDp)) {
                        val rungH = rung.rowCount * cellH

                        // Bus bars
                        drawRect(Color(0xFF333333), Offset.Zero, Size(busBarW, rungH))
                        drawRect(Color(0xFF333333), Offset(totalWidthPx - busBarW, 0f), Size(busBarW, rungH))

                        // Rung number
                        drawContext.canvas.nativeCanvas.drawText(
                            "$rungIdx", busBarW * 0.5f, cellH * 0.6f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = (cellH * 0.2f).coerceIn(10f, 20f)
                                isAntiAlias = true
                            }
                        )

                        // Comment
                        if (rung.comment.isNotBlank()) {
                            drawContext.canvas.nativeCanvas.drawText(
                                rung.comment, busBarW + 4f, cellH * 0.15f,
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.rgb(100, 100, 100)
                                    textSize = (cellH * 0.15f).coerceIn(8f, 16f)
                                    isAntiAlias = true
                                }
                            )
                        }

                        // Draw cells
                        rung.grid.forEachIndexed { rowIdx, row ->
                            val rowY = rowIdx * cellH + cellH / 2f

                            // Bus-to-first connection (첫 열(col=0)에 요소가 있을 때만)
                            val firstElementCol = row.indexOfFirst { it.element != null }
                            val hasAnyElement = firstElementCol >= 0
                            if (colPositions.isNotEmpty() && hasAnyElement && firstElementCol == 0) {
                                drawLine(Color(0xFF555555), Offset(busBarW, rowY),
                                    Offset(busBarW + colPositions[0].x, rowY), strokeWidth = 2f)
                            }

                            colPositions.forEachIndexed { displayIdx, colPos ->
                                val cell = row.getOrNull(colPos.origCol) ?: return@forEachIndexed
                                val cellX = busBarW + colPos.x
                                val thisW = colPos.width
                                val addrStr = cell.element?.address?.toString() ?: ""
                                val nameStr = ioLabels[addrStr] ?: cell.element?.label ?: ""
                                val isActive = if (addrStr.isNotBlank()) memory[addrStr] ?: false else false

                                val timerMs = when (val el = cell.element) {
                                    is LadderElement.Timer -> timerValues[el.timerNumber] ?: 0
                                    else -> 0
                                }
                                val counterVal = when (val el = cell.element) {
                                    is LadderElement.Counter -> counterValues[el.counterNumber] ?: 0
                                    else -> 0
                                }

                                ElementRenderer.draw(
                                    scope = this, element = cell.element,
                                    x = cellX, y = rowY,
                                    cellW = thisW, cellH = cellH,
                                    isActive = isActive,
                                    addressText = addrStr, nameText = nameStr,
                                    hasBottom = cell.hasBottom,
                                    isSelected = false,
                                    timerCurrentMs = timerMs,
                                    counterCurrentVal = counterVal
                                )
                            }

                            // Last-to-right-bus connection (출력 요소가 있을 때만)
                            val hasOutput = row.getOrNull(LadderRung.OUTPUT_COL)?.element != null
                            if (colPositions.isNotEmpty() && hasOutput) {
                                val last = colPositions.last()
                                drawLine(Color(0xFF555555),
                                    Offset(busBarW + last.x + last.width, rowY),
                                    Offset(totalWidthPx - busBarW, rowY), strokeWidth = 2f)
                            }
                        }

                        // Rung separator
                        drawLine(Color(0xFFBBBBBB), Offset(0f, rungH + 1f),
                            Offset(totalWidthPx, rungH + 1f), strokeWidth = 1f)
                    }
                }
            }
        }
    }
}

// ── 열 레이아웃 데이터 ──

/** 유효 열 정보 */
private data class ColInfo(
    val origCol: Int,        // 원래 그리드 열 인덱스
    val isMeaningful: Boolean // true=접점/코일 등, false=HLine/빈 열(연결선)
)

/** 계산된 열 위치/폭 */
private data class ColPosition(
    val origCol: Int,
    val x: Float,     // usableW 내 시작 x
    val width: Float
)

/**
 * 전체 런그에서 유효 열 목록 + 의미 있는지 판정
 */
private fun calcColumnLayout(rungs: List<LadderRung>): List<ColInfo> {
    val maxCols = LadderRung.GRID_COLS

    // 각 열이 의미 있는 요소를 포함하는지 (HLine 제외)
    val colMeaningful = BooleanArray(maxCols) { col ->
        rungs.any { rung ->
            rung.grid.any { row ->
                val el = row.getOrNull(col)?.element
                el != null && el !is LadderElement.HorizontalLine
            }
        }
    }

    // 수직 연결이 있는 열도 유지
    val colHasVertical = BooleanArray(maxCols) { col ->
        rungs.any { rung ->
            rung.grid.any { row -> row.getOrNull(col)?.hasBottom == true }
        }
    }

    // 유지할 열 = 의미 있거나 수직 연결이 있는 열
    val keepCols = (0 until maxCols).filter { colMeaningful[it] || colHasVertical[it] }
    if (keepCols.isEmpty()) return listOf(ColInfo(0, false))

    val outputCol = keepCols.last()
    val contactCols = keepCols.dropLast(1)

    val result = mutableListOf<ColInfo>()

    // 접점 영역
    contactCols.forEach { col ->
        result.add(ColInfo(col, colMeaningful[col]))
    }

    // 접점~출력 사이 연결선 1열 (갭이 있으면)
    if (contactCols.isNotEmpty()) {
        val lastContact = contactCols.last()
        if (outputCol - lastContact > 1) {
            result.add(ColInfo(lastContact + 1, false))  // bridge
        }
    }

    // 출력
    result.add(ColInfo(outputCol, true))

    return result
}

/**
 * 열 폭/위치 계산:
 * - 의미 있는 열: elemCellW 고정 폭
 * - bridge 열(연결선): 남은 공간을 균등 분배
 *
 * 축소 시: elemCellW가 작아져도 bridge가 남은 공간을 채워서 총 폭 유지
 * 확대 시: elemCellW가 커지면 bridge는 최소 폭, 총 폭 증가 → 스크롤
 */
private fun calcColPositions(layout: List<ColInfo>, usableW: Float, elemCellW: Float): List<ColPosition> {
    if (layout.isEmpty()) return emptyList()

    val meaningfulCount = layout.count { it.isMeaningful }
    val bridgeCount = layout.count { !it.isMeaningful }

    if (meaningfulCount == 0) {
        val w = usableW / layout.size
        return layout.mapIndexed { idx, info -> ColPosition(info.origCol, idx * w, w) }
    }

    // 의미 있는 열 = elemCellW 고정
    val totalElemW = meaningfulCount * elemCellW
    // bridge 열 = 남은 공간 균등 분배 (최소 elemCellW * 0.3)
    val bridgeMinW = elemCellW * 0.3f
    val remainW = usableW - totalElemW
    val bridgeW = if (bridgeCount > 0) {
        (remainW / bridgeCount).coerceAtLeast(bridgeMinW)
    } else 0f

    val positions = mutableListOf<ColPosition>()
    var x = 0f
    layout.forEach { info ->
        val w = if (info.isMeaningful) elemCellW else bridgeW
        positions.add(ColPosition(info.origCol, x, w))
        x += w
    }

    // 우측 bus bar에 정확히 맞도록 마지막 열 보정
    if (positions.isNotEmpty()) {
        val last = positions.last()
        val totalUsed = last.x + last.width
        val diff = usableW - totalUsed
        if (diff > 1f && bridgeCount > 0) {
            // bridge 열에 남은 공간 재분배
            val extra = diff / bridgeCount
            var adjust = 0f
            for (i in positions.indices) {
                positions[i] = positions[i].copy(x = positions[i].x + adjust)
                if (!layout[i].isMeaningful) {
                    positions[i] = positions[i].copy(width = positions[i].width + extra)
                    adjust += extra
                }
            }
        }
    }

    return positions
}
