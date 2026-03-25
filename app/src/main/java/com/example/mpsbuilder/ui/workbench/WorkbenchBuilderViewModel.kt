package com.example.mpsbuilder.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpsbuilder.data.ladder.*
import com.example.mpsbuilder.data.repository.WorkbenchLayoutRepository
import com.example.mpsbuilder.ui.workbench.model.*
import com.example.mpsbuilder.ui.workbench.widgets.WidgetRenderer
import com.example.mpsbuilder.ui.workbench.workpiece.WorkpieceSupplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WorkbenchBuilderViewModel @Inject constructor(
    private val workpieceSupplier: WorkpieceSupplier,
    private val layoutRepository: WorkbenchLayoutRepository
) : ViewModel() {

    private val _layout = MutableStateFlow(WorkbenchLayout())
    val layout: StateFlow<WorkbenchLayout> = _layout.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    // ── 다중 선택
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    // ── 클립보드 (복사/잘라내기한 위젯 목록)
    private val _clipboard = MutableStateFlow<List<PlacedWidgetState>>(emptyList())
    val hasClipboard: StateFlow<Boolean> = _clipboard.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Undo/Redo 스택
    private val undoStack = ArrayDeque<WorkbenchLayout>(30)
    private val redoStack = ArrayDeque<WorkbenchLayout>(30)
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun pushUndo() {
        undoStack.addLast(_layout.value)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_layout.value)
        _layout.value = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _selectedId.value = null
        _selectedIds.value = emptySet()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_layout.value)
        _layout.value = redoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _selectedId.value = null
        _selectedIds.value = emptySet()
    }

    private val _workpieces = MutableStateFlow<List<Workpiece>>(emptyList())
    val workpieces: StateFlow<List<Workpiece>> = _workpieces.asStateFlow()

    private val _layoutNames = MutableStateFlow<List<String>>(emptyList())
    val layoutNames: StateFlow<List<String>> = _layoutNames.asStateFlow()

    private val _simulationMemory = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val simulationMemory: StateFlow<Map<String, Boolean>> = _simulationMemory.asStateFlow()

    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode.asStateFlow()

    // ── 래더 관련 상태
    private val _ladderRungs = MutableStateFlow<List<LadderRung>>(emptyList())
    val ladderRungs: StateFlow<List<LadderRung>> = _ladderRungs.asStateFlow()

    private val _ladderIoLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val ladderIoLabels: StateFlow<Map<String, String>> = _ladderIoLabels.asStateFlow()

    // 래더 뷰어 줌 (패널 숨기기/보이기해도 유지)
    private val _ladderZoom = MutableStateFlow(-1f) // -1 = 자동 fit
    val ladderZoom: StateFlow<Float> = _ladderZoom.asStateFlow()
    fun setLadderZoom(zoom: Float) { _ladderZoom.value = zoom }

    private val _timerValues = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val timerValues: StateFlow<Map<Int, Int>> = _timerValues.asStateFlow()

    private val _counterValues = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val counterValues: StateFlow<Map<Int, Int>> = _counterValues.asStateFlow()

    private val _dataRegisters = MutableStateFlow<Map<Int, Int>>(emptyMap())

    // 실린더 애니메이션 progress: widgetId → 0.0(후진)~1.0(전진)
    private val _cylinderProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val cylinderProgress: StateFlow<Map<String, Float>> = _cylinderProgress.asStateFlow()

    // 부저 ON 상태
    private val _buzzerOn = MutableStateFlow(false)
    val buzzerOn: StateFlow<Boolean> = _buzzerOn.asStateFlow()

    private var ladderScanJob: Job? = null

    val hasLadder: StateFlow<Boolean> = _ladderRungs.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refreshLayoutList()
        // 자동 복원 (앱 재시작 시)
        viewModelScope.launch {
            layoutRepository.load("__autosave__")?.let { saved ->
                // autosave 파일 내부의 name은 원래 레이아웃 이름이 유지됨
                _layout.value = saved
            }
        }
        // 레이아웃 변경 시 자동 저장 (500ms 디바운스)
        viewModelScope.launch {
            _layout.collect { layout ->
                kotlinx.coroutines.delay(500L)
                // __autosave__ 파일에 저장하되, 내부 name은 현재 이름 유지
                layoutRepository.saveAs("__autosave__", layout)
            }
        }
    }

    // 테스트 모드 진입 시 공급기 스택 백업 (종료 시 복구용)
    private var _supplierStackBackup = mapOf<String, List<WorkpieceType>>()

    fun toggleTestMode() {
        _isTestMode.value = !_isTestMode.value
        if (_isTestMode.value) {
            // 공급기 스택 백업
            _supplierStackBackup = _layout.value.placedWidgets
                .filter { it.widgetType == WidgetType.WORKPIECE_SUPPLIER }
                .associate { it.id to it.workpieceStack }
            initCylinderSensors()
            startLadderScan()
        } else {
            stopLadderScan()
            _simulationMemory.value = emptyMap()
            _workpieces.value = emptyList()
            _timerValues.value = emptyMap()
            _counterValues.value = emptyMap()
            _cylinderProgress.value = emptyMap()
            // 공급기 스택 복구 (테스트 전 수량으로)
            restoreSupplierStacks()
        }
    }

    /** 공급기 스택을 테스트 모드 진입 시 백업된 수량으로 복구 */
    private fun restoreSupplierStacks() {
        if (_supplierStackBackup.isEmpty()) return
        _layout.update { layout ->
            layout.copy(
                placedWidgets = layout.placedWidgets.map { w ->
                    if (w.widgetType == WidgetType.WORKPIECE_SUPPLIER) {
                        val backup = _supplierStackBackup[w.id]
                        if (backup != null) w.copy(workpieceStack = backup) else w
                    } else w
                }
            )
        }
    }

    /** 실린더 초기상태: 후진 LS ON */
    private fun initCylinderSensors() {
        val updates = mutableMapOf<String, Boolean>()
        _layout.value.placedWidgets
            .filter { it.widgetType == WidgetType.CYLINDER }
            .forEach { cyl ->
                // 후진 LS ON (기본 후진 위치)
                cyl.ioSlots.find { it.key == "IN2" }?.address?.takeIf { it.isNotBlank() }?.let {
                    updates[it] = true
                }
                // 전진 LS OFF
                cyl.ioSlots.find { it.key == "IN1" }?.address?.takeIf { it.isNotBlank() }?.let {
                    updates[it] = false
                }
            }
        _simulationMemory.update { it + updates }
    }

    /** 실린더 전진 시 LS 업데이트: 전진LS ON, 후진LS OFF */
    private fun updateCylinderLS(cylinder: PlacedWidgetState, extending: Boolean) {
        val in1 = cylinder.ioSlots.find { it.key == "IN1" }?.address?.takeIf { it.isNotBlank() }
        val in2 = cylinder.ioSlots.find { it.key == "IN2" }?.address?.takeIf { it.isNotBlank() }
        val updates = mutableMapOf<String, Boolean>()
        if (extending) {
            in1?.let { updates[it] = true }   // 전진 LS ON
            in2?.let { updates[it] = false }  // 후진 LS OFF
        } else {
            in1?.let { updates[it] = false }  // 전진 LS OFF
            in2?.let { updates[it] = true }   // 후진 LS ON
        }
        _simulationMemory.update { it + updates }
    }

    // ── 이전 실린더 상태 추적 (후진→전진 감지용)
    private val _prevCylinderState = mutableMapOf<String, Boolean>()

    /** 테스트 모드: 위젯 터치 다운 */
    fun testPressDown(widgetId: String) {
        val widget = _layout.value.placedWidgets.find { it.id == widgetId } ?: return
        when (widget.widgetType) {
            WidgetType.CYLINDER -> {
                if (widget.cylinderMode == CylinderMode.SINGLE) {
                    val out1 = widget.ioSlots.find { it.key == "OUT1" }?.address
                    val wasFwd = out1?.let { _simulationMemory.value[it] } ?: false
                    _prevCylinderState[widgetId] = wasFwd
                    if (out1 != null && out1.isNotBlank()) {
                        _simulationMemory.update { it + (out1 to true) }
                        updateCylinderLS(widget, true)
                        checkCylinderSupplyWorkpiece(widget, wasFwd = false)
                    }
                }
            }
            WidgetType.PUSH_BUTTON -> {
                // 푸시 스위치: 누르는 동안 ON
                if (widget.switchType == SwitchType.PUSH) {
                    val in1 = widget.ioSlots.find { it.key == "IN1" }?.address
                    if (in1 != null && in1.isNotBlank()) {
                        _simulationMemory.update { it + (in1 to true) }
                    }
                }
            }
            else -> {}
        }
    }

    /** 테스트 모드: 위젯 터치 업 */
    fun testPressUp(widgetId: String) {
        val widget = _layout.value.placedWidgets.find { it.id == widgetId } ?: return
        when (widget.widgetType) {
            WidgetType.CYLINDER -> {
                if (widget.cylinderMode == CylinderMode.SINGLE) {
                    val out1 = widget.ioSlots.find { it.key == "OUT1" }?.address
                    if (out1 != null && out1.isNotBlank()) {
                        _simulationMemory.update { it + (out1 to false) }
                        updateCylinderLS(widget, false)
                    }
                }
            }
            WidgetType.PUSH_BUTTON -> {
                // 푸시 스위치: 떼면 OFF
                if (widget.switchType == SwitchType.PUSH) {
                    val in1 = widget.ioSlots.find { it.key == "IN1" }?.address
                    if (in1 != null && in1.isNotBlank()) {
                        _simulationMemory.update { it + (in1 to false) }
                    }
                }
            }
            else -> {}
        }
    }

    /** 테스트 모드: 위젯 탭 (복동 실린더, 모터, 컨베이어 등) */
    fun testTapWidget(widgetId: String) {
        val widget = _layout.value.placedWidgets.find { it.id == widgetId } ?: return

        when (widget.widgetType) {
            WidgetType.CYLINDER -> {
                if (widget.cylinderMode == CylinderMode.DOUBLE) {
                    // 복동: 전진/후진 토글
                    val out1 = widget.ioSlots.find { it.key == "OUT1" }?.address
                    val out2 = widget.ioSlots.find { it.key == "OUT2" }?.address
                    val fwd = out1?.let { _simulationMemory.value[it] } ?: false
                    if (!fwd) {
                        // 후진→전진
                        out1?.let { _simulationMemory.update { m -> m + (it to true) } }
                        out2?.let { _simulationMemory.update { m -> m + (it to false) } }
                        updateCylinderLS(widget, true)
                        checkCylinderSupplyWorkpiece(widget, wasFwd = false)
                    } else {
                        // 전진→후진
                        out1?.let { _simulationMemory.update { m -> m + (it to false) } }
                        out2?.let { _simulationMemory.update { m -> m + (it to true) } }
                        updateCylinderLS(widget, false)
                    }
                }
                // 단동은 pressDown/Up에서 처리
            }
            WidgetType.LAMP, WidgetType.VALVE, WidgetType.BUZZER -> {
                val out1 = widget.ioSlots.find { it.key == "OUT1" }?.address
                if (out1 != null && out1.isNotBlank()) {
                    val cur = _simulationMemory.value[out1] ?: false
                    _simulationMemory.update { it + (out1 to !cur) }
                }
            }
            WidgetType.MOTOR, WidgetType.CONVEYOR -> {
                // 정지 → 정전 → 역전 → 정지
                val out1 = widget.ioSlots.find { it.key == "OUT1" }?.address
                val out2 = widget.ioSlots.find { it.key == "OUT2" }?.address
                val fwd = out1?.let { _simulationMemory.value[it] } ?: false
                val rev = out2?.let { _simulationMemory.value[it] } ?: false
                when {
                    !fwd && !rev -> out1?.let { _simulationMemory.update { m -> m + (it to true) } }
                    fwd && !rev -> {
                        out1?.let { _simulationMemory.update { m -> m + (it to false) } }
                        out2?.let { _simulationMemory.update { m -> m + (it to true) } }
                    }
                    else -> {
                        out1?.let { _simulationMemory.update { m -> m + (it to false) } }
                        out2?.let { _simulationMemory.update { m -> m + (it to false) } }
                    }
                }
            }
            WidgetType.PUSH_BUTTON -> {
                // 토글/선택 스위치: 탭으로 ON/OFF 전환
                if (widget.switchType == SwitchType.TOGGLE || widget.switchType == SwitchType.SELECT) {
                    val in1 = widget.ioSlots.find { it.key == "IN1" }?.address
                    if (in1 != null && in1.isNotBlank()) {
                        val cur = _simulationMemory.value[in1] ?: false
                        _simulationMemory.update { it + (in1 to !cur) }
                    }
                }
                // 푸시는 pressDown/Up에서 처리
            }
            // 센서는 자동
            else -> {}
        }
    }

    /** 실린더 후진→전진 시 연결된 공급기에서 공작물 투입 */
    private fun checkCylinderSupplyWorkpiece(cylinder: PlacedWidgetState, wasFwd: Boolean) {
        if (wasFwd) return  // 이미 전진 중이었으면 무시 (후진→전진만)

        val supplier = _layout.value.placedWidgets.find { w ->
            w.widgetType == WidgetType.WORKPIECE_SUPPLIER &&
                    w.linkedCylinderId == cylinder.id
        } ?: return
        if (supplier.workpieceStack.isEmpty()) return

        val wpType = supplier.workpieceStack.first()
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == supplier.id) w.copy(workpieceStack = w.workpieceStack.drop(1)) else w
        }) }

        // 실린더 끝 위치에 공작물 배치
        val cylSize = WidgetRenderer.getWidgetSize(cylinder)
        val tipX = cylinder.positionX + cylSize.width
        val tipY = cylinder.positionY + cylSize.height / 2f - 12f

        val nearConv = findNearestConveyor(tipX, tipY, _layout.value.placedWidgets)
        val wp = Workpiece(
            id = UUID.randomUUID().toString().take(8),
            type = wpType,
            positionX = tipX,
            positionY = tipY,
            onConveyorId = nearConv?.id,
            conveyorProgress = if (nearConv != null) calcConveyorProgress(tipX, tipY, nearConv) else 0f
        )
        _workpieces.update { it + wp }
    }

    // ── 위젯 추가 / 삭제
    fun addWidget(type: WidgetType, x: Float = 100f, y: Float = 100f) {
        pushUndo()
        val config = if (type == WidgetType.CONVEYOR) ConveyorConfig() else null
        val newWidget = PlacedWidgetState(
            id = UUID.randomUUID().toString().take(8),
            widgetType = type,
            positionX = x, positionY = y,
            label = type.label,
            ioSlots = type.defaultIOSlots(),
            conveyorConfig = config,
            zOrder = _layout.value.placedWidgets.size
        )
        _layout.update { it.copy(placedWidgets = it.placedWidgets + newWidget) }
        _selectedId.value = newWidget.id
    }

    fun removeWidget(id: String) {
        pushUndo()
        _layout.update { it.copy(placedWidgets = it.placedWidgets.filter { w -> w.id != id }) }
        if (_selectedId.value == id) _selectedId.value = null
        _selectedIds.update { it - id }
    }

    fun selectWidget(id: String?) {
        _selectedId.value = id
        _selectedIds.value = if (id != null) setOf(id) else emptySet()
    }

    /** 다중 선택: 기존 선택에 추가/제거 토글 */
    fun toggleSelectWidget(id: String) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
        // 단일 선택도 마지막 토글된 것으로
        _selectedId.value = if (id in _selectedIds.value) id else _selectedIds.value.lastOrNull()
    }

    /** 블록 선택: 사각 영역 안의 위젯 모두 선택 */
    fun blockSelect(left: Float, top: Float, right: Float, bottom: Float) {
        val hits = _layout.value.placedWidgets.filter { w ->
            val ws = WidgetRenderer.getWidgetSize(w)
            val wRight = w.positionX + ws.width
            val wBottom = w.positionY + ws.height
            // 사각형 교차 판정
            w.positionX < right && wRight > left &&
                    w.positionY < bottom && wBottom > top
        }.map { it.id }.toSet()
        _selectedIds.value = hits
        _selectedId.value = hits.lastOrNull()
    }

    fun clearSelection() {
        _selectedId.value = null
        _selectedIds.value = emptySet()
    }

    /** 선택된 위젯 삭제 */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        pushUndo()
        _layout.update { it.copy(placedWidgets = it.placedWidgets.filter { w -> w.id !in ids }) }
        clearSelection()
    }

    // ── 클립보드 복사
    fun copySelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        _clipboard.value = _layout.value.placedWidgets.filter { it.id in ids }
    }

    // ── 잘라내기
    fun cutSelected() {
        copySelected()
        deleteSelected()
    }

    // ── 붙여넣기 (30dp 오프셋으로 복제)
    fun paste() {
        val items = _clipboard.value
        if (items.isEmpty()) return
        pushUndo()
        val newWidgets = items.map { w ->
            w.copy(
                id = UUID.randomUUID().toString().take(8),
                positionX = w.positionX + 30f,
                positionY = w.positionY + 30f,
                zOrder = (_layout.value.placedWidgets.maxOfOrNull { it.zOrder } ?: 0) + 1
            )
        }
        _layout.update { it.copy(placedWidgets = it.placedWidgets + newWidgets) }
        _selectedIds.value = newWidgets.map { it.id }.toSet()
        _selectedId.value = newWidgets.lastOrNull()?.id
    }

    // ── 이동 / 회전 / 크기 (다중 선택 지원)
    fun moveWidget(id: String, dx: Float, dy: Float) {
        val ids = if (_selectedIds.value.contains(id)) _selectedIds.value else setOf(id)
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id in ids) w.copy(positionX = w.positionX + dx, positionY = w.positionY + dy) else w
        }) }
    }

    fun rotateWidget(id: String, deltaDeg: Float) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(rotationDeg = (w.rotationDeg + deltaDeg + 360f) % 360f) else w
        }) }
    }

    fun scaleWidget(id: String, factor: Float) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(scaleFactor = (w.scaleFactor * factor).coerceIn(0.3f, 3.0f)) else w
        }) }
    }

    fun setWidgetScale(id: String, absoluteScale: Float) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(scaleFactor = absoluteScale.coerceIn(0.3f, 3.0f)) else w
        }) }
    }

    // ── zOrder
    fun bringToFront(id: String) {
        val maxZ = _layout.value.placedWidgets.maxOfOrNull { it.zOrder } ?: 0
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(zOrder = maxZ + 1) else w
        }) }
    }
    fun sendToBack(id: String) {
        val minZ = _layout.value.placedWidgets.minOfOrNull { it.zOrder } ?: 0
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(zOrder = minZ - 1) else w
        }) }
    }

    // ── 속성 편집
    fun updateWidgetLabel(id: String, label: String) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(label = label) else w
        }) }
    }

    fun updateIOSlotAddress(id: String, slotKey: String, address: String) {
        val upperAddr = address.uppercase()
        _layout.update { layout ->
            val updatedWidgets = layout.placedWidgets.map { w ->
                if (w.id == id) w.copy(ioSlots = w.ioSlots.map { slot ->
                    if (slot.key == slotKey) slot.copy(address = upperAddr) else slot
                }) else w
            }
            val updatedMap = layout.ioLabelMap.toMutableMap()
            val widget = updatedWidgets.find { it.id == id }
            if (widget != null && upperAddr.isNotBlank()) {
                val slot = widget.ioSlots.find { it.key == slotKey }
                updatedMap[upperAddr] = "${widget.label} - ${slot?.label ?: slotKey}"
            }
            layout.copy(placedWidgets = updatedWidgets, ioLabelMap = updatedMap)
        }
    }

    fun setCylinderMode(id: String, mode: CylinderMode) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id && w.widgetType == WidgetType.CYLINDER) {
                val newSlots = cylinderIOSlots(mode).map { ns ->
                    val existing = w.ioSlots.find { it.key == ns.key }
                    ns.copy(address = existing?.address ?: "")
                }
                w.copy(cylinderMode = mode, ioSlots = newSlots)
            } else w
        }) }
    }

    fun setConveyorDriveMode(id: String, mode: ConveyorDriveMode) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id && w.widgetType == WidgetType.CONVEYOR) {
                val newSlots = conveyorIOSlots(mode).map { ns ->
                    val existing = w.ioSlots.find { it.key == ns.key }
                    ns.copy(address = existing?.address ?: "")
                }
                w.copy(conveyorDriveMode = mode, ioSlots = newSlots)
            } else w
        }) }
    }

    fun setSensorType(id: String, type: SensorType) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id && w.widgetType == WidgetType.SENSOR) w.copy(sensorType = type) else w
        }) }
    }

    fun setSwitchType(id: String, type: SwitchType) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id && w.widgetType == WidgetType.PUSH_BUTTON) w.copy(switchType = type) else w
        }) }
    }

    fun setSignalTowerTiers(id: String, tiers: SignalTowerTiers) {
        pushUndo()
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(
                signalTowerTiers = tiers,
                ioSlots = signalTowerIOSlots(tiers)
            ) else w
        }) }
    }

    fun setWidgetColor(id: String, color: Long) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(widgetColor = color) else w
        }) }
    }

    fun updateConveyorConfig(id: String, config: ConveyorConfig) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == id) w.copy(conveyorConfig = config) else w
        }) }
    }

    fun linkConveyorToMotor(conveyorId: String, motorId: String) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == conveyorId) w.copy(linkedMotorId = motorId) else w
        }) }
    }

    // ── 공급기 ↔ 실린더 연결
    fun linkSupplierToCylinder(supplierId: String, cylinderId: String) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == supplierId) w.copy(linkedCylinderId = cylinderId) else w
        }) }
    }

    // ── 공작물을 공급기 스택에 추가 (팔레트에서 선택)
    fun addWorkpieceToSupplier(supplierId: String, type: WorkpieceType) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == supplierId && w.widgetType == WidgetType.WORKPIECE_SUPPLIER) {
                w.copy(workpieceStack = w.workpieceStack + type)
            } else w
        }) }
    }

    // ── 공급기 스택에서 공작물 제거 (클리어)
    fun clearSupplierStack(supplierId: String) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
            if (w.id == supplierId) w.copy(workpieceStack = emptyList()) else w
        }) }
    }

    // ── 시뮬레이션: 실린더 전진 → 연결된 공급기에서 공작물을 실린더 끝 위치에 투입
    fun forceOutput(address: String, value: Boolean) {
        _simulationMemory.update { it + (address to value) }

        if (value) {
            val widgets = _layout.value.placedWidgets
            val cylinder = widgets.find { w ->
                w.widgetType == WidgetType.CYLINDER &&
                        w.ioSlots.find { it.key == "OUT1" }?.address == address
            }
            if (cylinder != null) {
                val supplier = widgets.find { w ->
                    w.widgetType == WidgetType.WORKPIECE_SUPPLIER &&
                            w.linkedCylinderId == cylinder.id
                }
                if (supplier != null && supplier.workpieceStack.isNotEmpty()) {
                    val wpType = supplier.workpieceStack.first()
                    _layout.update { it.copy(placedWidgets = it.placedWidgets.map { w ->
                        if (w.id == supplier.id) w.copy(workpieceStack = w.workpieceStack.drop(1))
                        else w
                    }) }

                    // 실린더 전진 끝 위치 계산
                    val cylSize = WidgetRenderer.getWidgetSize(cylinder)
                    val tipX = cylinder.positionX + cylSize.width  // 로드 끝
                    val tipY = cylinder.positionY + cylSize.height / 2f - 12f

                    // 가장 가까운 컨베이어 찾기 (실린더 끝 근처)
                    val nearConveyor = findNearestConveyor(tipX, tipY, widgets)

                    val wp = Workpiece(
                        id = UUID.randomUUID().toString().take(8),
                        type = wpType,
                        positionX = tipX,
                        positionY = tipY,
                        onConveyorId = nearConveyor?.id,
                        conveyorProgress = if (nearConveyor != null) {
                            calcConveyorProgress(tipX, tipY, nearConveyor)
                        } else 0f
                    )
                    _workpieces.update { it + wp }
                }
            }
        }
    }

    /** 좌표 근처의 컨베이어 찾기 (50dp 이내) */
    private fun findNearestConveyor(x: Float, y: Float, widgets: List<PlacedWidgetState>): PlacedWidgetState? {
        return widgets
            .filter { it.widgetType == WidgetType.CONVEYOR }
            .filter { conv ->
                val cs = WidgetRenderer.getWidgetSize(conv)
                val margin = 50f
                x >= conv.positionX - margin && x <= conv.positionX + cs.width + margin &&
                        y >= conv.positionY - margin && y <= conv.positionY + cs.height + margin
            }
            .minByOrNull { conv ->
                val cx = conv.positionX + WidgetRenderer.getWidgetSize(conv).width / 2f
                val cy = conv.positionY + WidgetRenderer.getWidgetSize(conv).height / 2f
                (x - cx) * (x - cx) + (y - cy) * (y - cy)
            }
    }

    /** 컨베이어 위 좌표를 0~1 progress로 변환 */
    private fun calcConveyorProgress(x: Float, y: Float, conv: PlacedWidgetState): Float {
        val cs = WidgetRenderer.getWidgetSize(conv)
        val dir = conv.conveyorConfig?.direction ?: ConveyorDirection.RIGHT
        return when (dir) {
            ConveyorDirection.RIGHT -> ((x - conv.positionX) / cs.width).coerceIn(0f, 1f)
            ConveyorDirection.LEFT -> (1f - (x - conv.positionX) / cs.width).coerceIn(0f, 1f)
            ConveyorDirection.DOWN -> ((y - conv.positionY) / cs.height).coerceIn(0f, 1f)
            ConveyorDirection.UP -> (1f - (y - conv.positionY) / cs.height).coerceIn(0f, 1f)
        }
    }

    // ── 시뮬레이션 틱 (20ms마다 호출) — 실린더LS + 컨베이어 이동 + 센서 감지
    fun tickSimulation() {
        val memory = _simulationMemory.value
        val widgets = _layout.value.placedWidgets

        // 0) 실린더 LS센서: progress 완료 시점에만 ON
        //    progress == 1.0 → 전진LS ON, 후진LS OFF
        //    progress == 0.0 → 전진LS OFF, 후진LS ON
        //    0 < progress < 1 → 둘 다 OFF (이동 중)
        val cylProgMap = _cylinderProgress.value
        val cylLsUpdates = mutableMapOf<String, Boolean>()
        widgets.filter { it.widgetType == WidgetType.CYLINDER }.forEach { cyl ->
            val in1Addr = cyl.ioSlots.find { it.key == "IN1" }?.address?.takeIf { it.isNotBlank() }
            val in2Addr = cyl.ioSlots.find { it.key == "IN2" }?.address?.takeIf { it.isNotBlank() }
            val prog = cylProgMap[cyl.id] ?: 0f

            val fwdComplete = prog >= 1f
            val revComplete = prog <= 0f

            in1Addr?.let { cylLsUpdates[it] = fwdComplete }   // 전진 LS: 완전 전진일 때만 ON
            in2Addr?.let { cylLsUpdates[it] = revComplete }   // 후진 LS: 완전 후진일 때만 ON
        }
        if (cylLsUpdates.isNotEmpty()) {
            _simulationMemory.update { it + cylLsUpdates }
        }

        // 0.5) 실린더 progress 애니메이션 (부드러운 전후진)
        //      50ms 틱, 전체 이동 ~500ms → speed = 50/500 = 0.1 per tick
        val cylSpeed = 0.06f  // 느리게: ~830ms 완전 이동
        _cylinderProgress.update { progMap ->
            val newMap = progMap.toMutableMap()
            widgets.filter { it.widgetType == WidgetType.CYLINDER }.forEach { cyl ->
                val out1Addr = cyl.ioSlots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
                val out2Addr = cyl.ioSlots.find { it.key == "OUT2" }?.address?.takeIf { it.isNotBlank() }
                val out1On = out1Addr?.let { memory[it] } ?: false
                val out2On = out2Addr?.let { memory[it] } ?: false

                val target = when (cyl.cylinderMode) {
                    CylinderMode.SINGLE -> if (out1On) 1f else 0f
                    CylinderMode.DOUBLE -> when {
                        out1On && !out2On -> 1f
                        out2On && !out1On -> 0f
                        else -> newMap[cyl.id] ?: 0f  // 유지
                    }
                }

                val current = newMap[cyl.id] ?: 0f
                val next = if (target > current) {
                    (current + cylSpeed).coerceAtMost(target)
                } else if (target < current) {
                    (current - cylSpeed).coerceAtLeast(target)
                } else current

                newMap[cyl.id] = next
            }
            newMap
        }

        // 1) 컨베이어 위 공작물 이동 — 벨트 애니메이션(800ms/cycle)과 동기화
        //    틱=50ms, 800ms에 줄무늬 1칸 이동, 줄무늬 간격=컨베이어폭*6%
        //    progress 이동: speed = (줄무늬간격/컨베이어길이) / (800ms/50ms)
        _workpieces.update { wps ->
            wps.map { wp ->
                val conv = wp.onConveyorId?.let { cid ->
                    widgets.find { it.id == cid && it.widgetType == WidgetType.CONVEYOR }
                }
                if (conv != null) {
                    val fwdAddr = conv.ioSlots.find { it.key == "OUT1" }?.address
                    val revAddr = conv.ioSlots.find { it.key == "OUT2" }?.address
                    val fwdOn = fwdAddr?.let { memory[it] } ?: false
                    val revOn = revAddr?.let { memory[it] } ?: false

                    val cs = WidgetRenderer.getWidgetSize(conv)
                    val convLength = if (conv.conveyorConfig?.direction == ConveyorDirection.UP ||
                        conv.conveyorConfig?.direction == ConveyorDirection.DOWN) cs.height else cs.width
                    // 줄무늬 간격 = convLength * 0.06f, 800ms에 1줄무늬 이동
                    // 50ms에 이동할 progress = (간격/길이) / (800/50) = 0.06 / 16 ≈ 0.00375
                    val speed = 0.06f / 16f

                    val delta = when {
                        fwdOn && !revOn -> speed
                        revOn && !fwdOn -> -speed
                        else -> 0f
                    }

                    if (delta != 0f) {
                        val newProgress = (wp.conveyorProgress + delta).coerceIn(0f, 1f)
                        val dir = conv.conveyorConfig?.direction ?: ConveyorDirection.RIGHT
                        val (nx, ny) = progressToPosition(newProgress, conv, cs, dir)
                        wp.copy(conveyorProgress = newProgress, positionX = nx, positionY = ny)
                    } else wp
                } else {
                    val nearConv = findNearestConveyor(wp.positionX, wp.positionY, widgets)
                    if (nearConv != null && wp.onConveyorId == null) {
                        val prog = calcConveyorProgress(wp.positionX, wp.positionY, nearConv)
                        wp.copy(onConveyorId = nearConv.id, conveyorProgress = prog)
                    } else wp
                }
            }.filter { it.conveyorProgress < 1f || it.onConveyorId == null }
        }

        // 2) 센서 감지 — 방향 무관, 컨베이어 위 공작물만 거리 기반 감지
        val sensors = widgets.filter { it.widgetType == WidgetType.SENSOR }
        val wps = _workpieces.value
        val sensorUpdates = mutableMapOf<String, Boolean>()
        val detectRange = 25f  // 감지 거리 (dp)

        sensors.forEach { sensor ->
            val inAddr = sensor.ioSlots.find { it.key == "IN1" }?.address
            if (inAddr != null && inAddr.isNotBlank()) {
                val ss = WidgetRenderer.getWidgetSize(sensor)
                val sensorCX = sensor.positionX + ss.width / 2f
                val sensorCY = sensor.positionY + ss.height / 2f

                val detected = wps.any { wp ->
                    // 컨베이어 위에 있는 공작물만 감지 대상
                    if (wp.onConveyorId == null) return@any false

                    // 센서 중심과 공작물 사이 거리 (방향 무관)
                    val dx = wp.positionX - sensorCX
                    val dy = wp.positionY - sensorCY
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist > detectRange) return@any false

                    // 센서 타입에 따라 공작물 종류 판별
                    when (sensor.sensorType) {
                        SensorType.PHOTO -> true               // 광전: 전체 감지
                        SensorType.PROXIMITY -> wp.type == WorkpieceType.METAL  // 근접: 금속만
                    }
                }
                sensorUpdates[inAddr] = detected
            }
        }

        if (sensorUpdates.isNotEmpty()) {
            _simulationMemory.update { it + sensorUpdates }
        }

        // 3) 부저 상태 업데이트
        val mem = _simulationMemory.value
        val anyBuzzerOn = widgets
            .filter { it.widgetType == WidgetType.BUZZER }
            .any { buzzer ->
                val addr = buzzer.ioSlots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
                addr?.let { mem[it] } ?: false
            }
        _buzzerOn.value = anyBuzzerOn
    }

    /** 컨베이어 progress(0~1) → 작업대 좌표 변환 */
    private fun progressToPosition(
        progress: Float,
        conv: PlacedWidgetState,
        cs: androidx.compose.ui.geometry.Size,
        dir: ConveyorDirection
    ): Pair<Float, Float> {
        return when (dir) {
            ConveyorDirection.RIGHT -> Pair(
                conv.positionX + cs.width * progress,
                conv.positionY + cs.height / 2f
            )
            ConveyorDirection.LEFT -> Pair(
                conv.positionX + cs.width * (1f - progress),
                conv.positionY + cs.height / 2f
            )
            ConveyorDirection.DOWN -> Pair(
                conv.positionX + cs.width / 2f,
                conv.positionY + cs.height * progress
            )
            ConveyorDirection.UP -> Pair(
                conv.positionX + cs.width / 2f,
                conv.positionY + cs.height * (1f - progress)
            )
        }
    }

    // ── 저장 / 불러오기
    fun saveLayout(name: String) = viewModelScope.launch {
        _layout.update { it.copy(name = name) }  // 현재 레이아웃 이름 갱신
        layoutRepository.save(_layout.value)
        refreshLayoutList()
    }
    fun loadLayout(name: String) = viewModelScope.launch {
        layoutRepository.load(name)?.let {
            _layout.value = it; _selectedId.value = null; _workpieces.value = it.workpieces
        }
    }
    fun deleteLayout(name: String) = viewModelScope.launch {
        layoutRepository.delete(name); refreshLayoutList()
    }
    private fun refreshLayoutList() = viewModelScope.launch {
        _layoutNames.value = layoutRepository.listLayouts()
    }
    fun exportLayoutJson(): String = Json { prettyPrint = true }.encodeToString(_layout.value)
    fun importLayoutJson(json: String) {
        try { _layout.value = Json { ignoreUnknownKeys = true }.decodeFromString<WorkbenchLayout>(json); _selectedId.value = null } catch (_: Exception) {}
    }
    fun newLayout() {
        _layout.value = WorkbenchLayout(); _selectedId.value = null
        _workpieces.value = emptyList(); _simulationMemory.value = emptyMap()
    }

    // ══════════════════════════════════════════════
    //  래더 연동
    // ══════════════════════════════════════════════

    /** PLCSimul JSON 프로젝트 파일 로드 */
    fun importLadderJson(jsonText: String) {
        try {
            val project = Json { ignoreUnknownKeys = true }
                .decodeFromString<LadderProject>(jsonText)
            _ladderRungs.value = project.rungs
            _ladderIoLabels.value = project.ioLabels
        } catch (_: Exception) { }
    }

    /** MELSEC CSV 파일 로드 (GX-Works2 형식) */
    fun importLadderCsv(csvText: String) {
        try {
            val result = GxWorks2CsvImporter.import(csvText)
            _ladderRungs.value = result.rungs
            _ladderIoLabels.value = result.ioLabels
        } catch (_: Exception) { }
    }

    /** 래더 제거 */
    fun clearLadder() {
        ladderScanJob?.cancel()
        _ladderRungs.value = emptyList()
        _ladderIoLabels.value = emptyMap()
        _timerValues.value = emptyMap()
        _counterValues.value = emptyMap()
        _dataRegisters.value = emptyMap()
    }

    /** 래더 스캔 루프 시작/정지 (테스트모드 진입 시 호출) */
    private fun startLadderScan() {
        ladderScanJob?.cancel()
        if (_ladderRungs.value.isEmpty()) return

        ladderScanJob = viewModelScope.launch {
            while (isActive) {
                // 1) 먼저 위젯 시뮬레이션 (센서 감지 등 → X 주소 업데이트)
                tickSimulation()

                // 2) 래더 스캔 (업데이트된 메모리 기반 → Y 주소 출력)
                val result = LadderSimulator.scan(
                    rungs = _ladderRungs.value,
                    memory = _simulationMemory.value,
                    timerCurrentValues = _timerValues.value,
                    counterCurrentValues = _counterValues.value,
                    dataRegisters = _dataRegisters.value,
                    scanTimeMs = 20L
                )
                _simulationMemory.value = result.memory
                _timerValues.value = result.timerValues
                _counterValues.value = result.counterValues
                _dataRegisters.value = result.dataRegisters
                delay(20L)
            }
        }
    }

    private fun stopLadderScan() {
        ladderScanJob?.cancel()
        ladderScanJob = null
    }
}
