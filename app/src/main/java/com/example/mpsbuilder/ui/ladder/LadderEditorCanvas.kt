package com.example.mpsbuilder.ui.ladder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.data.ladder.LadderRung

/**
 * 래더 편집기 캔버스 — 셀 탭으로 선택, 더블탭으로 편집
 * LadderViewerCanvas와 유사하나 편집 인터랙션 추가
 */
@Composable
fun LadderEditorCanvas(
    rungs: List<LadderRung>,
    ioLabels: Map<String, String>,
    selectedCell: LadderEditorViewModel.CellPosition?,
    selectedCells: Set<LadderEditorViewModel.CellPosition>,
    onCellTap: (LadderEditorViewModel.CellPosition) -> Unit,
    onCellDoubleTap: (LadderEditorViewModel.CellPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rungs.isEmpty()) return

    val density = LocalDensity.current
    val cellHDp: Dp = 72.dp

    // 수평 fit: 사용 열 수 계산
    val usedCols = rungs.maxOfOrNull { rung ->
        rung.grid.maxOfOrNull { row ->
            val lastUsed = row.indexOfLast { it.element != null }
            if (lastUsed >= 0) lastUsed + 1 else 1
        } ?: 1
    } ?: LadderRung.GRID_COLS
    val displayCols = maxOf(usedCols, 6).coerceAtMost(LadderRung.GRID_COLS)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rungs, key = { _, r -> r.id }) { rungIdx, rung ->
                val rungHeightDp = cellHDp * rung.rowCount + 4.dp

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rungHeightDp)
                        .pointerInput(rungIdx, rungs.size) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val canvasW = size.width.toFloat()
                                    val busBarW = canvasW * 0.04f
                                    val usableW = canvasW - busBarW * 2
                                    val cellW = usableW / displayCols
                                    val cellH = with(density) { cellHDp.toPx() }

                                    val col = ((offset.x - busBarW) / cellW).toInt()
                                        .coerceIn(0, LadderRung.GRID_COLS - 1)
                                    val row = (offset.y / cellH).toInt()
                                        .coerceIn(0, rung.rowCount - 1)

                                    // 디스플레이 열 → 그리드 열 매핑
                                    val gridCol = if (col >= displayCols - 1) LadderRung.OUTPUT_COL
                                    else col.coerceAtMost(LadderRung.CONTACT_COLS - 1)

                                    onCellTap(LadderEditorViewModel.CellPosition(rungIdx, row, gridCol))
                                },
                                onDoubleTap = { offset ->
                                    val canvasW = size.width.toFloat()
                                    val busBarW = canvasW * 0.04f
                                    val usableW = canvasW - busBarW * 2
                                    val cellW = usableW / displayCols
                                    val cellH = with(density) { cellHDp.toPx() }

                                    val col = ((offset.x - busBarW) / cellW).toInt()
                                        .coerceIn(0, LadderRung.GRID_COLS - 1)
                                    val row = (offset.y / cellH).toInt()
                                        .coerceIn(0, rung.rowCount - 1)

                                    val gridCol = if (col >= displayCols - 1) LadderRung.OUTPUT_COL
                                    else col.coerceAtMost(LadderRung.CONTACT_COLS - 1)

                                    onCellDoubleTap(LadderEditorViewModel.CellPosition(rungIdx, row, gridCol))
                                }
                            )
                        }
                ) {
                    val canvasW = size.width
                    val busBarW = canvasW * 0.04f
                    val usableW = canvasW - busBarW * 2
                    val cellW = usableW / displayCols
                    val cellH = with(density) { cellHDp.toPx() }
                    val rungH = rung.rowCount * cellH

                    // Left bus bar
                    drawRect(Color(0xFF333333), Offset(0f, 0f), Size(busBarW * 0.3f, rungH))
                    // Right bus bar
                    drawRect(Color(0xFF333333), Offset(canvasW - busBarW * 0.3f, 0f), Size(busBarW * 0.3f, rungH))

                    // Rung number
                    drawContext.canvas.nativeCanvas.drawText(
                        "${rungIdx}", busBarW * 0.15f, cellH * 0.6f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = cellH * 0.18f
                            isAntiAlias = true
                        }
                    )

                    // 런그 코멘트
                    if (rung.comment.isNotBlank()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            rung.comment, busBarW + 4f, cellH * 0.15f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.DKGRAY
                                textSize = cellH * 0.14f
                                isAntiAlias = true
                            }
                        )
                    }

                    // 각 행 렌더링
                    for (rowIdx in 0 until rung.rowCount) {
                        val row = rung.grid.getOrNull(rowIdx) ?: continue
                        val y = rowIdx * cellH + cellH / 2f

                        // 좌측 버스바 → 첫 접점 연결선
                        drawLine(Color(0xFF555555), Offset(busBarW * 0.3f, y), Offset(busBarW, y), strokeWidth = 2f)

                        // 각 셀
                        for (dispCol in 0 until displayCols) {
                            val gridCol = if (dispCol >= displayCols - 1) LadderRung.OUTPUT_COL
                            else dispCol.coerceAtMost(LadderRung.CONTACT_COLS - 1)

                            val cell = row.getOrNull(gridCol) ?: continue
                            val x = busBarW + dispCol * cellW

                            val cellPos = LadderEditorViewModel.CellPosition(rungIdx, rowIdx, gridCol)
                            val isSelected = cellPos == selectedCell || cellPos in selectedCells

                            // 선택 하이라이트
                            if (isSelected) {
                                drawRect(
                                    Color(0x3300AAFF),
                                    Offset(x, y - cellH / 2f),
                                    Size(cellW, cellH)
                                )
                                drawRect(
                                    Color(0xFF0088FF),
                                    Offset(x, y - cellH / 2f),
                                    Size(cellW, cellH),
                                    style = Stroke(2.5f)
                                )
                            }

                            val addrText = cell.element?.address?.toString() ?: ""
                            val nameText = if (addrText.isNotBlank()) ioLabels[addrText] ?: "" else ""

                            ElementRenderer.draw(
                                scope = this,
                                element = cell.element,
                                x = x, y = y,
                                cellW = cellW, cellH = cellH,
                                isActive = false,
                                addressText = addrText,
                                nameText = nameText,
                                hasBottom = cell.hasBottom,
                                isSelected = false  // 이미 위에서 하이라이트
                            )
                        }

                        // 우측 버스바 연결
                        val rightX = busBarW + (displayCols - 1) * cellW + cellW
                        drawLine(Color(0xFF555555), Offset(rightX, y), Offset(canvasW - busBarW * 0.3f, y), strokeWidth = 2f)
                    }

                    // 런그 구분선
                    drawLine(Color(0xFFBBBBBB), Offset(0f, rungH + 1f), Offset(canvasW, rungH + 1f), strokeWidth = 1f)
                }
            }
        }
    }
}
