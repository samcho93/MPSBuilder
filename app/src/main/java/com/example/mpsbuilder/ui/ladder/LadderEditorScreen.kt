package com.example.mpsbuilder.ui.ladder

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mpsbuilder.data.ladder.LadderElement
import com.example.mpsbuilder.data.ladder.LadderRung
import com.example.mpsbuilder.ui.ladder.toolbar.CommandPalette
import com.example.mpsbuilder.ui.ladder.toolbar.ElementToolbar

/**
 * 래더 편집기 풀스크린 다이얼로그
 * PLCSimul의 전체 편집 기능 포팅 (시뮬레이션 제외)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LadderEditorDialog(
    initialRungs: List<LadderRung> = emptyList(),
    initialLabels: Map<String, String> = emptyMap(),
    onApply: (rungs: List<LadderRung>, labels: Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val vm = remember {
        LadderEditorViewModel().also {
            if (initialRungs.isNotEmpty()) it.loadRungs(initialRungs, initialLabels)
        }
    }

    val rungs by vm.rungs.collectAsState()
    val selectedCell by vm.selectedCell.collectAsState()
    val selectedCells by vm.selectedCells.collectAsState()
    val isMultiSelect by vm.isMultiSelect.collectAsState()
    val isOverwriteMode by vm.isOverwriteMode.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val ioLabels by vm.ioLabels.collectAsState()
    val projectName by vm.projectName.collectAsState()
    val isModified by vm.isModified.collectAsState()
    val context = LocalContext.current

    var showCommandPalette by remember { mutableStateOf(false) }
    var showAddressEdit by remember { mutableStateOf(false) }
    var editingElement by remember { mutableStateOf<LadderElement?>(null) }
    var editingPosition by remember { mutableStateOf<LadderEditorViewModel.CellPosition?>(null) }
    var showFileMenu by remember { mutableStateOf(false) }
    var showNewConfirm by remember { mutableStateOf(false) }
    var showRungCommentEdit by remember { mutableStateOf<Int?>(null) }

    // 파일 런처
    val jsonOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
            val text = String(bytes, Charsets.UTF_8)
            if (vm.importProjectJson(text)) {
                Toast.makeText(context, "JSON 로드 완료", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val csvOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
            if (vm.importCsvBytes(bytes)) {
                Toast.makeText(context, "CSV 로드 완료", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(vm.exportProjectJson().toByteArray())
            }
            Toast.makeText(context, "JSON 저장 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val csvSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(vm.exportCsvBytes())
            }
            Toast.makeText(context, "CSV 저장 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            "래더 편집기" +
                                    if (projectName != "MAIN") " — $projectName" else "" +
                                            if (isModified) " *" else ""
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "닫기")
                        }
                    },
                    actions = {
                        // 파일 메뉴
                        Box {
                            IconButton(onClick = { showFileMenu = true }) {
                                Icon(Icons.Default.Menu, "파일 메뉴")
                            }
                            DropdownMenu(
                                expanded = showFileMenu,
                                onDismissRequest = { showFileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("새 프로젝트") },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = {
                                        showFileMenu = false
                                        if (isModified) showNewConfirm = true
                                        else vm.newProject()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("JSON 열기") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                    onClick = {
                                        showFileMenu = false
                                        jsonOpenLauncher.launch(arrayOf("application/json", "*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("JSON 저장") },
                                    leadingIcon = { Icon(Icons.Default.Save, null) },
                                    onClick = {
                                        showFileMenu = false
                                        jsonSaveLauncher.launch("${projectName}.json")
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("CSV 불러오기 (GX-Works2)") },
                                    leadingIcon = { Icon(Icons.Default.Upload, null) },
                                    onClick = {
                                        showFileMenu = false
                                        csvOpenLauncher.launch(arrayOf("text/*", "*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("CSV 내보내기 (GX-Works2)") },
                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                    onClick = {
                                        showFileMenu = false
                                        csvSaveLauncher.launch("${projectName}.csv")
                                    }
                                )
                            }
                        }

                        // 적용 (MPS Builder에 반영)
                        FilledTonalButton(
                            onClick = { onApply(rungs, ioLabels) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("적용")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // ── 요소 도구바 (2단)
                ElementToolbar(
                    hasSelection = selectedCells.isNotEmpty(),
                    isMultiSelect = isMultiSelect,
                    isOverwriteMode = isOverwriteMode,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onPlaceElement = { el ->
                        // GX-Works2 방식: 배치 전 위치 캡처 → 배치 → 즉시 주소 입력
                        val posBeforePlace = vm.selectedCell.value
                        val placed = vm.placeElement(el)
                        // 배치된 위치는 출력이면 OUTPUT_COL, 아니면 원래 위치
                        val placedPos = if (posBeforePlace != null) {
                            val targetCol = if (placed.isOutput()) LadderRung.OUTPUT_COL else posBeforePlace.col
                            LadderEditorViewModel.CellPosition(posBeforePlace.rungIdx, posBeforePlace.row, targetCol)
                        } else null
                        if (placed !is LadderElement.HorizontalLine && placedPos != null) {
                            editingElement = placed
                            editingPosition = placedPos
                            showAddressEdit = true
                        }
                    },
                    onPlaceHLine = { vm.placeHorizontalLine() },
                    onToggleVLine = { vm.toggleVerticalLine() },
                    onAddRow = { vm.addRow() },
                    onAddRung = { vm.addRung() },
                    onDeleteElement = { vm.deleteSelectedElements() },
                    onDeleteRow = { vm.deleteRow() },
                    onToggleMultiSelect = { vm.toggleMultiSelect() },
                    onToggleEditMode = { vm.toggleEditMode() },
                    onUndo = { vm.undo() },
                    onRedo = { vm.redo() },
                    onOpenCommandPalette = { showCommandPalette = true }
                )

                HorizontalDivider()

                // ── 래더 캔버스
                LadderEditorCanvas(
                    rungs = rungs,
                    ioLabels = ioLabels,
                    selectedCell = selectedCell,
                    selectedCells = selectedCells,
                    onCellTap = { pos -> vm.selectCell(pos) },
                    onCellDoubleTap = { pos ->
                        // 더블탭 → 해당 셀 요소 편집
                        val rung = rungs.getOrNull(pos.rungIdx)
                        val cell = rung?.grid?.getOrNull(pos.row)?.getOrNull(pos.col)
                        if (cell?.element != null && cell.element !is LadderElement.HorizontalLine) {
                            editingElement = cell.element
                            editingPosition = pos
                            showAddressEdit = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }

    // ── 명령어 팔레트
    if (showCommandPalette) {
        CommandPalette(
            onSelect = { fb -> vm.placeElement(fb) },
            onDismiss = { showCommandPalette = false }
        )
    }

    // ── 주소 편집 다이얼로그 (GX-Works2: 배치 즉시 표시)
    if (showAddressEdit && editingElement != null && editingPosition != null) {
        AddressEditDialog(
            element = editingElement!!,
            usedAddresses = vm.getUsedAddresses(),
            ioLabels = ioLabels,
            onConfirm = { updated ->
                val pos = editingPosition!!
                vm.replaceElement(pos.rungIdx, pos.row, pos.col, updated)
                // IO 라벨 자동 등록
                updated.address?.let { addr ->
                    if (updated.label.isNotBlank()) {
                        vm.setLabel(addr, updated.label)
                    }
                }
                showAddressEdit = false
                editingElement = null
                editingPosition = null
            },
            onDismiss = {
                showAddressEdit = false
                editingElement = null
                editingPosition = null
            }
        )
    }

    // ── 새 프로젝트 확인
    if (showNewConfirm) {
        AlertDialog(
            onDismissRequest = { showNewConfirm = false },
            title = { Text("새 프로젝트") },
            text = { Text("수정된 내용이 있습니다. 새 프로젝트를 시작하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { vm.newProject(); showNewConfirm = false }) {
                    Text("새로 시작")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewConfirm = false }) { Text("취소") }
            }
        )
    }

    // ── 런그 코멘트 편집
    showRungCommentEdit?.let { rungIdx ->
        var comment by remember { mutableStateOf(rungs.getOrNull(rungIdx)?.comment ?: "") }
        AlertDialog(
            onDismissRequest = { showRungCommentEdit = null },
            title = { Text("런그 $rungIdx 코멘트") },
            text = {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("코멘트") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRungComment(rungIdx, comment)
                    showRungCommentEdit = null
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showRungCommentEdit = null }) { Text("취소") }
            }
        )
    }
}
