package com.example.mpsbuilder.ui.workbench

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mpsbuilder.ui.ladder.LadderViewerCanvas
import com.example.mpsbuilder.ui.workbench.canvas.WorkbenchCanvas
import com.example.mpsbuilder.ui.workbench.model.WidgetType
import com.example.mpsbuilder.ui.workbench.test.IOMonitorPanel
import com.example.mpsbuilder.ui.workbench.test.WidgetTestPanel
import com.example.mpsbuilder.ui.workbench.widgets.PropertyPanel
import com.example.mpsbuilder.ui.workbench.widgets.WidgetPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchBuilderScreen(
    viewModel: WorkbenchBuilderViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()
    val memory by viewModel.simulationMemory.collectAsStateWithLifecycle()
    val layoutNames by viewModel.layoutNames.collectAsStateWithLifecycle()
    val isTestMode by viewModel.isTestMode.collectAsStateWithLifecycle()
    val ladderRungs by viewModel.ladderRungs.collectAsStateWithLifecycle()
    val ladderIoLabels by viewModel.ladderIoLabels.collectAsStateWithLifecycle()
    val timerValues by viewModel.timerValues.collectAsStateWithLifecycle()
    val counterValues by viewModel.counterValues.collectAsStateWithLifecycle()
    val hasLadder by viewModel.hasLadder.collectAsStateWithLifecycle()
    val cylProgress by viewModel.cylinderProgress.collectAsStateWithLifecycle()
    val multiSelectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val hasClipboard by viewModel.hasClipboard.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showTestPanel by remember { mutableStateOf(false) }
    var showLadderPanel by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLadderEditor by remember { mutableStateOf(false) }

    // 래더 파일 선택 런처
    val ladderFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@rememberLauncherForActivityResult
            inputStream.close()
            val fileName = uri.lastPathSegment ?: ""

            // 인코딩 감지
            val text = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                String(bytes, Charsets.UTF_16LE)
            } else if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                String(bytes, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }

            when {
                fileName.endsWith(".json", true) || text.trimStart().startsWith("{") ->
                    viewModel.importLadderJson(text)
                else -> viewModel.importLadderCsv(text)
            }
            showLadderPanel = true
            Toast.makeText(context, "래더 로드 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "래더 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 시뮬레이션 틱 루프 (50ms)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(50L)
            viewModel.tickSimulation()
        }
    }

    // 부저 — ON 동안 연속 비프음 (1kHz 사인파), OFF 즉시 정지
    val buzzerOn by viewModel.buzzerOn.collectAsStateWithLifecycle()
    DisposableEffect(buzzerOn) {
        if (!buzzerOn) return@DisposableEffect onDispose { }

        val sampleRate = 16000
        val freq = 1000.0
        val chunkSize = 800 // 50ms분량 (16000*0.05=800 samples) — 작게 하면 반응 빠름
        val bufSize = android.media.AudioTrack.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(chunkSize * 2)

        val track = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(android.media.AudioTrack.MODE_STREAM)
            .build()

        // 짧은 사인파 청크 생성
        val samples = ShortArray(chunkSize)
        for (i in samples.indices) {
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            samples[i] = (kotlin.math.sin(angle) * Short.MAX_VALUE * 0.4).toInt().toShort()
        }

        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val thread = Thread {
            track.play()
            while (running.get()) {
                track.write(samples, 0, samples.size)
            }
            track.stop()
            track.release()
        }
        thread.start()

        onDispose {
            running.set(false)  // 플래그 → write 루프 탈출 → stop+release
        }
    }

    val selectedWidget = selectedId?.let { id ->
        layout.placedWidgets.find { it.id == id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("MPS Builder")
                        // 현재 파일명 표시
                        if (layout.name != "NewLayout") {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "— ${layout.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        if (isTestMode) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    " TEST ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // 테스트 모드 토글
                    IconButton(onClick = { viewModel.toggleTestMode() }) {
                        Icon(
                            if (isTestMode) Icons.Default.Stop else Icons.Default.PlayArrow,
                            if (isTestMode) "테스트 종료" else "테스트 시작",
                            tint = if (isTestMode) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 래더 파일 읽기
                    IconButton(onClick = {
                        ladderFileLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(
                            Icons.Default.Description,
                            "래더 읽기",
                            tint = if (hasLadder) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 래더 편집기 열기
                    IconButton(onClick = { showLadderEditor = true }) {
                        Icon(Icons.Default.Edit, "래더 편집기",
                            tint = MaterialTheme.colorScheme.primary)
                    }

                    // 래더 보기 토글 (래더가 있을 때만)
                    if (hasLadder) {
                        IconButton(onClick = { showLadderPanel = !showLadderPanel }) {
                            Icon(
                                if (showLadderPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "래더 보기"
                            )
                        }
                    }

                    if (!isTestMode) {
                        // ── 편집 (Undo/Redo)
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                            Icon(Icons.Default.Undo, "실행취소")
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                            Icon(Icons.Default.Redo, "다시실행")
                        }

                        // ── 편집 (복사/붙여넣기/잘라내기/삭제) — 선택 있을 때만 활성
                        if (multiSelectedIds.isNotEmpty()) {
                            IconButton(onClick = { viewModel.copySelected() }) {
                                Icon(Icons.Default.ContentCopy, "복사")
                            }
                            IconButton(onClick = { viewModel.cutSelected() }) {
                                Icon(Icons.Default.ContentCut, "잘라내기")
                            }
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.Delete, "삭제",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (hasClipboard) {
                            IconButton(onClick = { viewModel.paste() }) {
                                Icon(Icons.Default.ContentPaste, "붙여넣기")
                            }
                        }

                        // ── 파일
                        IconButton(onClick = { viewModel.newLayout() }) {
                            Icon(Icons.Default.Add, "새 레이아웃")
                        }
                        // 저장 (현재 이름, 새 레이아웃이면 다이얼로그)
                        IconButton(onClick = {
                            if (layout.name != "NewLayout") {
                                val withLadder = ladderRungs.isNotEmpty()
                                viewModel.saveLayout(layout.name, withLadder)
                                val ext = if (withLadder) "mpsx" else "mps"
                                Toast.makeText(context, "저장: ${layout.name}.$ext", Toast.LENGTH_SHORT).show()
                            } else {
                                showSaveDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Save, "저장")
                        }
                        // 다른 이름으로 저장 (현재 이름을 기본값으로 표시)
                        IconButton(onClick = { showSaveAsDialog = true }) {
                            Icon(Icons.Default.SaveAs, "다른 이름으로 저장")
                        }
                        IconButton(onClick = { showLoadDialog = true }) {
                            Icon(Icons.Default.FolderOpen, "불러오기")
                        }
                    }
                    // 도움말 (항상 표시)
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, "도움말")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isTestMode)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isTestMode) {
                // ── 테스트 모드: 좌측 IO 모니터
                IOMonitorPanel(
                    widgets = layout.placedWidgets,
                    simulationMemory = memory
                )
            } else {
                // ── 편집 모드: 좌측 위젯 팔레트
                WidgetPalette(
                    onAddWidget = { type -> viewModel.addWidget(type) },
                    suppliers = layout.placedWidgets.filter {
                        it.widgetType == WidgetType.WORKPIECE_SUPPLIER
                    },
                    onAddWorkpieceToSupplier = { suppId, type ->
                        viewModel.addWorkpieceToSupplier(suppId, type)
                    }
                )
            }

            // 중앙: 캔버스
            WorkbenchCanvas(
                layout = layout,
                workpieces = workpieces,
                selectedId = if (!isTestMode) selectedId else null,
                selectedIds = if (!isTestMode) multiSelectedIds else emptySet(),
                simulationMemory = memory,
                isTestMode = isTestMode,
                onWidgetSelect = { if (!isTestMode) viewModel.selectWidget(it) },
                onWidgetToggleSelect = { if (!isTestMode) viewModel.toggleSelectWidget(it) },
                onBlockSelect = { l, t, r, b -> if (!isTestMode) viewModel.blockSelect(l, t, r, b) },
                onWidgetTap = { viewModel.testTapWidget(it) },
                onWidgetPressDown = { viewModel.testPressDown(it) },
                onWidgetPressUp = { viewModel.testPressUp(it) },
                onWidgetMove = { id, dx, dy ->
                    if (!isTestMode) viewModel.moveWidget(id, dx, dy)
                },
                onWidgetRotate = { id, delta ->
                    if (!isTestMode) viewModel.rotateWidget(id, delta)
                },
                onWidgetScaleAbsolute = { id, s ->
                    if (!isTestMode) viewModel.setWidgetScale(id, s)
                },
                cylinderProgress = cylProgress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            // 우측: 속성 패널 또는 래더 뷰어
            if (showLadderPanel && hasLadder) {
                // 래더 뷰어 (수평 fit, 수직 스크롤)
                VerticalDivider()
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("래더 다이어그램", style = MaterialTheme.typography.titleSmall)
                        Row {
                            IconButton(
                                onClick = { viewModel.clearLadder(); showLadderPanel = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "래더 제거", Modifier.size(16.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                    val ladderZoom by viewModel.ladderZoom.collectAsStateWithLifecycle()
                    LadderViewerCanvas(
                        rungs = ladderRungs,
                        memory = memory,
                        ioLabels = ladderIoLabels,
                        timerValues = timerValues,
                        counterValues = counterValues,
                        zoomFactor = ladderZoom,
                        onZoomChange = { viewModel.setLadderZoom(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            } else if (!isTestMode && selectedWidget != null) {
                VerticalDivider()
                PropertyPanel(
                    widget = selectedWidget,
                    motors = layout.placedWidgets.filter { it.widgetType == WidgetType.MOTOR },
                    cylinders = layout.placedWidgets.filter { it.widgetType == WidgetType.CYLINDER },
                    onLabelChange = { viewModel.updateWidgetLabel(selectedWidget.id, it) },
                    onIOSlotChange = { slotKey, addr ->
                        viewModel.updateIOSlotAddress(selectedWidget.id, slotKey, addr)
                    },
                    onRotate = { viewModel.rotateWidget(selectedWidget.id, it) },
                    onScale = { viewModel.scaleWidget(selectedWidget.id, it) },
                    onBringToFront = { viewModel.bringToFront(selectedWidget.id) },
                    onSendToBack = { viewModel.sendToBack(selectedWidget.id) },
                    onConveyorConfigChange = {
                        viewModel.updateConveyorConfig(selectedWidget.id, it)
                    },
                    onLinkMotor = { viewModel.linkConveyorToMotor(selectedWidget.id, it) },
                    onCylinderModeChange = { viewModel.setCylinderMode(selectedWidget.id, it) },
                    onConveyorDriveModeChange = {
                        viewModel.setConveyorDriveMode(selectedWidget.id, it)
                    },
                    onSensorTypeChange = { viewModel.setSensorType(selectedWidget.id, it) },
                    onSwitchTypeChange = { viewModel.setSwitchType(selectedWidget.id, it) },
                    onLinkCylinder = { viewModel.linkSupplierToCylinder(selectedWidget.id, it) },
                    onClearStack = { viewModel.clearSupplierStack(selectedWidget.id) },
                    onWidgetColorChange = { viewModel.setWidgetColor(selectedWidget.id, it) },
                    onSignalTowerTiersChange = { viewModel.setSignalTowerTiers(selectedWidget.id, it) },
                    onLinkTableToCylinder = { viewModel.linkTableToCylinder(selectedWidget.id, it) },
                    onLinkTableToConveyor = { viewModel.linkTableToConveyor(selectedWidget.id, it) },
                    onLinkSupplierToTable = { viewModel.linkSupplierToTable(selectedWidget.id, it) },
                    conveyors = layout.placedWidgets.filter { it.widgetType == WidgetType.CONVEYOR },
                    tables = layout.placedWidgets.filter { it.widgetType == WidgetType.TABLE },
                    onDelete = { viewModel.removeWidget(selectedWidget.id) },
                    onOpenTest = { showTestPanel = true }
                )
            }
        }
    }

    // 다이얼로그
    if (showSaveDialog) {
        SaveLayoutDialog(
            title = "레이아웃 저장",
            currentName = layout.name,
            showLadderOption = ladderRungs.isNotEmpty(),
            onSave = { name, withLadder ->
                viewModel.saveLayout(name, withLadder)
                showSaveDialog = false
                val ext = if (withLadder) "mpsx" else "mps"
                Toast.makeText(context, "저장: $name.$ext", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSaveDialog = false }
        )
    }
    if (showSaveAsDialog) {
        SaveLayoutDialog(
            title = "다른 이름으로 저장",
            currentName = layout.name,
            showLadderOption = ladderRungs.isNotEmpty(),
            onSave = { name, withLadder ->
                viewModel.saveLayout(name, withLadder)
                showSaveAsDialog = false
                val ext = if (withLadder) "mpsx" else "mps"
                Toast.makeText(context, "저장: $name.$ext", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSaveAsDialog = false }
        )
    }
    if (showLoadDialog) {
        LoadLayoutDialog(
            layoutEntries = layoutNames,
            onLoad = { name -> viewModel.loadLayout(name); showLoadDialog = false },
            onDelete = { name -> viewModel.deleteLayout(name) },
            onDismiss = { showLoadDialog = false }
        )
    }
    if (showTestPanel && selectedWidget != null && !isTestMode) {
        WidgetTestPanel(
            widget = selectedWidget,
            simulationMemory = memory,
            onForceOutput = { addr, value -> viewModel.forceOutput(addr, value) },
            onClose = { showTestPanel = false }
        )
    }
    if (showHelp) {
        com.example.mpsbuilder.ui.help.HelpDialog(onDismiss = { showHelp = false })
    }
    if (showLadderEditor) {
        com.example.mpsbuilder.ui.ladder.LadderEditorDialog(
            initialRungs = ladderRungs,
            initialLabels = ladderIoLabels,
            onApply = { rungs, labels ->
                // 편집기에서 적용 → MPS Builder에 반영
                viewModel.importLadderFromEditor(rungs, labels)
                showLadderEditor = false
                showLadderPanel = true
                Toast.makeText(context, "래더 적용 완료", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLadderEditor = false }
        )
    }
}

@Composable
private fun SaveLayoutDialog(
    title: String = "레이아웃 저장",
    currentName: String,
    showLadderOption: Boolean = false,
    onSave: (name: String, withLadder: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var withLadder by remember { mutableStateOf(showLadderOption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("레이아웃 이름") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // 파일 형식 선택
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("형식: ", style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = !withLadder,
                        onClick = { withLadder = false },
                        label = { Text(".mps", style = MaterialTheme.typography.labelSmall) }
                    )
                    Spacer(Modifier.width(4.dp))
                    FilterChip(
                        selected = withLadder,
                        onClick = { withLadder = true },
                        label = { Text(".mpsx", style = MaterialTheme.typography.labelSmall) },
                        enabled = showLadderOption
                    )
                }
                Text(
                    if (withLadder) "위젯 + 래더 함께 저장" else "위젯만 저장",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, withLadder) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun LoadLayoutDialog(
    layoutEntries: List<com.example.mpsbuilder.data.repository.LayoutEntry>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("레이아웃 불러오기") },
        text = {
            if (layoutEntries.isEmpty()) {
                Text("저장된 레이아웃이 없습니다.")
            } else {
                Column {
                    layoutEntries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onLoad(entry.name) }) {
                                Text(entry.name)
                            }
                            Text(
                                if (entry.hasLadder) ".mpsx" else ".mps",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.hasLadder) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { onDelete(entry.name) }) {
                                Icon(Icons.Default.Delete, "삭제",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
