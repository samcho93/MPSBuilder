package com.example.mpsbuilder.ui.ladder.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpsbuilder.data.ladder.IOAddress
import com.example.mpsbuilder.data.ladder.LadderElement
import java.util.UUID

/**
 * 래더 편집기 2단 도구바
 * Tier1: 접점/코일/타이머/카운터/특수릴레이/FB 배치
 * Tier2: 행추가/삭제/다중선택/Undo/Redo
 */
@Composable
fun ElementToolbar(
    hasSelection: Boolean,
    isMultiSelect: Boolean,
    isOverwriteMode: Boolean = true,
    canUndo: Boolean,
    canRedo: Boolean,
    onPlaceElement: (LadderElement) -> Unit,
    onPlaceHLine: () -> Unit,
    onToggleVLine: () -> Unit,
    onAddRow: () -> Unit,
    onAddRung: () -> Unit = {},
    onDeleteElement: () -> Unit,
    onDeleteRow: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onToggleEditMode: () -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSpecialRelayMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Tier 1: 요소 배치
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 접점
            ToolBtn("┤ ├", "a접점") {
                onPlaceElement(LadderElement.NormallyOpen(id = uuid(), address = null))
            }
            ToolBtn("┤/├", "b접점") {
                onPlaceElement(LadderElement.NormallyClosed(id = uuid(), address = null))
            }
            ToolBtn("┤P├", "상승") {
                onPlaceElement(LadderElement.RisingEdgeContact(id = uuid(), address = null))
            }
            ToolBtn("┤F├", "하강") {
                onPlaceElement(LadderElement.FallingEdgeContact(id = uuid(), address = null))
            }

            VerticalDivider(modifier = Modifier.height(32.dp))

            // 코일
            ToolBtn("( )", "OUT") {
                onPlaceElement(LadderElement.OutputCoil(id = uuid(), address = null))
            }
            ToolBtn("(S)", "SET") {
                onPlaceElement(LadderElement.SetCoil(id = uuid(), address = null))
            }
            ToolBtn("(R)", "RST") {
                onPlaceElement(LadderElement.ResetCoil(id = uuid(), address = null))
            }
            ToolBtn("(P)", "PLS") {
                onPlaceElement(LadderElement.RisingEdge(id = uuid(), address = null))
            }
            ToolBtn("(F)", "PLF") {
                onPlaceElement(LadderElement.FallingEdge(id = uuid(), address = null))
            }

            VerticalDivider(modifier = Modifier.height(32.dp))

            // 타이머/카운터
            ToolBtn("T", "타이머") {
                onPlaceElement(LadderElement.Timer(id = uuid(), address = null, timerNumber = 0, preset = 100))
            }
            ToolBtn("C", "카운터") {
                onPlaceElement(LadderElement.Counter(id = uuid(), address = null, counterNumber = 0, preset = 10))
            }

            VerticalDivider(modifier = Modifier.height(32.dp))

            // 수평/수직선
            ToolBtn("━", "수평") { onPlaceHLine() }
            ToolBtn("┃", "수직") { onToggleVLine() }

            VerticalDivider(modifier = Modifier.height(32.dp))

            // 특수 릴레이
            Box {
                ToolBtn("SM▼", "특수") { showSpecialRelayMenu = true }
                SpecialRelayDropdown(
                    expanded = showSpecialRelayMenu,
                    onDismiss = { showSpecialRelayMenu = false },
                    onSelect = { addr ->
                        showSpecialRelayMenu = false
                        onPlaceElement(LadderElement.SpecialRelay(
                            id = uuid(), address = addr
                        ))
                    }
                )
            }

            // 펑션 블록
            ToolBtn("F(x)", "명령어") { onOpenCommandPalette() }
        }

        HorizontalDivider()

        // ── Tier 2: 편집/구조
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBtn("+행", "OR행") { onAddRow() }
            ToolBtn("+런그", "런그") { onAddRung() }

            if (hasSelection) {
                ToolBtn("요소삭제", "삭제") { onDeleteElement() }
                ToolBtn("-행", "행삭제") { onDeleteRow() }
            }

            Spacer(Modifier.weight(1f))

            // Overwrite / Edit 모드 토글
            FilterChip(
                selected = !isOverwriteMode,
                onClick = onToggleEditMode,
                label = {
                    Text(
                        if (isOverwriteMode) "OVR" else "EDIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.width(4.dp))

            // 다중선택 토글
            FilterChip(
                selected = isMultiSelect,
                onClick = onToggleMultiSelect,
                label = { Text("다중", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp)
            )

            // Undo/Redo
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Undo, "되돌리기", Modifier.size(18.dp))
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Redo, "다시", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ToolBtn(text: String, desc: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SpecialRelayDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (IOAddress) -> Unit
) {
    val relays = listOf(
        400 to "SM400 항상 ON",
        401 to "SM401 항상 OFF",
        402 to "SM402 RUN후 1스캔ON",
        403 to "SM403 RUN후 1스캔OFF",
        409 to "SM409 0.01초 클록",
        410 to "SM410 0.1초 클록",
        411 to "SM411 0.2초 클록",
        412 to "SM412 0.5초 클록",
        413 to "SM413 1초 클록",
        414 to "SM414 2초 클록",
        415 to "SM415 2ms 클록",
        420 to "SM420 스캔시간 초과",
        430 to "SM430 STOP→RUN",
        431 to "SM431 RUN→STOP",
    )

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        relays.forEach { (num, label) ->
            DropdownMenuItem(
                text = { Text(label, fontSize = 12.sp) },
                onClick = { onSelect(IOAddress(IOAddress.AddressType.SM, num)) }
            )
        }
    }
}

private fun uuid() = UUID.randomUUID().toString()
