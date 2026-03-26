package com.example.mpsbuilder.ui.ladder

import com.example.mpsbuilder.data.ladder.*
import com.example.mpsbuilder.domain.usecase.AutoNumberingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * 래더 편집기 상태 관리 (시뮬레이션 미포함)
 * WorkbenchBuilderViewModel과 별개로, 래더 편집 팝업에서 사용
 */
class LadderEditorViewModel {

    data class CellPosition(val rungIdx: Int, val row: Int, val col: Int)

    private val _rungs = MutableStateFlow<List<LadderRung>>(listOf(LadderRung.empty()))
    val rungs: StateFlow<List<LadderRung>> = _rungs.asStateFlow()

    private val _selectedCell = MutableStateFlow<CellPosition?>(null)
    val selectedCell: StateFlow<CellPosition?> = _selectedCell.asStateFlow()

    private val _selectedCells = MutableStateFlow<Set<CellPosition>>(emptySet())
    val selectedCells: StateFlow<Set<CellPosition>> = _selectedCells.asStateFlow()

    private val _isMultiSelect = MutableStateFlow(false)
    val isMultiSelect: StateFlow<Boolean> = _isMultiSelect.asStateFlow()

    private val _ioLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val ioLabels: StateFlow<Map<String, String>> = _ioLabels.asStateFlow()

    private val _projectName = MutableStateFlow("MAIN")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    // Undo/Redo
    private val undoStack = ArrayDeque<List<LadderRung>>(30)
    private val redoStack = ArrayDeque<List<LadderRung>>(30)
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val autoNumbering = AutoNumberingUseCase()

    // ══════════════════════════════════
    //  프로젝트 관리
    // ══════════════════════════════════

    fun newProject() {
        pushUndo()
        _rungs.value = listOf(LadderRung.empty())
        _ioLabels.value = emptyMap()
        _projectName.value = "MAIN"
        _selectedCell.value = null
        _selectedCells.value = emptySet()
        autoNumbering.reset()
        _isModified.value = false
    }

    fun setProjectName(name: String) {
        _projectName.value = name
        _isModified.value = true
    }

    fun loadRungs(rungs: List<LadderRung>, labels: Map<String, String> = emptyMap()) {
        pushUndo()
        _rungs.value = rungs.ifEmpty { listOf(LadderRung.empty()) }
        _ioLabels.value = labels
        _selectedCell.value = null
        _selectedCells.value = emptySet()
        _isModified.value = false
    }

    // ── JSON I/O
    fun exportProjectJson(): String {
        val project = LadderProject(
            name = _projectName.value,
            rungs = _rungs.value,
            ioLabels = _ioLabels.value
        )
        return kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(
            LadderProject.serializer(), project
        )
    }

    fun importProjectJson(jsonStr: String): Boolean {
        return try {
            val project = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(LadderProject.serializer(), jsonStr)
            pushUndo()
            _rungs.value = project.rungs.ifEmpty { listOf(LadderRung.empty()) }
            _ioLabels.value = project.ioLabels
            _projectName.value = project.name
            _selectedCell.value = null
            _isModified.value = false
            true
        } catch (_: Exception) { false }
    }

    // ── CSV I/O
    fun exportCsvString(): String = GxWorks2CsvExporter.exportString(_rungs.value, _projectName.value)
    fun exportCsvBytes(): ByteArray = GxWorks2CsvExporter.exportBytes(_rungs.value, _projectName.value)

    fun importCsv(csvText: String): Boolean {
        return try {
            val result = GxWorks2CsvImporter.import(csvText)
            pushUndo()
            _rungs.value = result.rungs.ifEmpty { listOf(LadderRung.empty()) }
            _ioLabels.value = result.ioLabels
            _projectName.value = result.programName
            _selectedCell.value = null
            _isModified.value = false
            true
        } catch (_: Exception) { false }
    }

    fun importCsvBytes(bytes: ByteArray): Boolean {
        return try {
            val result = GxWorks2CsvImporter.importBytes(bytes)
            pushUndo()
            _rungs.value = result.rungs.ifEmpty { listOf(LadderRung.empty()) }
            _ioLabels.value = result.ioLabels
            _projectName.value = result.programName
            _selectedCell.value = null
            _isModified.value = false
            true
        } catch (_: Exception) { false }
    }

    // ══════════════════════════════════
    //  셀 선택
    // ══════════════════════════════════

    fun selectCell(pos: CellPosition) {
        if (_isMultiSelect.value) {
            _selectedCells.update {
                if (pos in it) it - pos else it + pos
            }
            _selectedCell.value = pos
        } else {
            _selectedCell.value = pos
            _selectedCells.value = setOf(pos)
        }
    }

    fun clearSelection() {
        _selectedCell.value = null
        _selectedCells.value = emptySet()
    }

    fun toggleMultiSelect() {
        _isMultiSelect.update { !it }
        if (!_isMultiSelect.value) {
            // 다중선택 해제 시 단일 선택만 유지
            _selectedCell.value?.let { _selectedCells.value = setOf(it) }
        }
    }

    // ══════════════════════════════════
    //  요소 배치
    // ══════════════════════════════════

    fun placeElement(element: LadderElement): LadderElement {
        val pos = _selectedCell.value ?: return element
        pushUndo()

        // 주소 자동 할당
        val assigned = autoAssignAddress(element)

        _rungs.update { rungs ->
            val rungList = rungs.toMutableList()
            if (pos.rungIdx >= rungList.size) return@update rungs
            val rung = rungList[pos.rungIdx]
            if (pos.row >= rung.grid.size) return@update rungs

            val grid = rung.grid.map { it.toMutableList() }.toMutableList()
            val row = grid[pos.row]

            // 출력 요소는 OUTPUT_COL에만 배치
            val targetCol = if (isOutputElement(assigned)) LadderRung.OUTPUT_COL else pos.col
            if (targetCol < row.size) {
                row[targetCol] = row[targetCol].copy(element = assigned)

                // 출력 요소 배치 시 접점~출력 사이 수평선 자동 채움
                if (isOutputElement(assigned)) {
                    val lastContact = findLastContactCol(row)
                    for (c in (lastContact + 1) until LadderRung.OUTPUT_COL) {
                        if (row[c].element == null) {
                            row[c] = row[c].copy(element = LadderElement.HorizontalLine(id = uuid()))
                        }
                    }
                }
            }

            grid[pos.row] = row
            rungList[pos.rungIdx] = rung.copy(grid = grid)
            rungList
        }

        // IO 라벨 자동 생성
        assigned.address?.let { addr ->
            val label = assigned.label.ifBlank { generateAutoLabel(assigned) }
            if (label.isNotBlank()) {
                _ioLabels.update { it + (addr.toString() to label) }
            }
        }

        // 접점 배치 후 커서를 다음 열로 자동 이동
        if (!isOutputElement(assigned)) {
            val nextCol = pos.col + 1
            if (nextCol < LadderRung.OUTPUT_COL) {
                val newPos = CellPosition(pos.rungIdx, pos.row, nextCol)
                _selectedCell.value = newPos
                _selectedCells.value = setOf(newPos)
            }
        }

        _isModified.value = true
        return assigned
    }

    fun placeHorizontalLine() {
        val pos = _selectedCell.value ?: return
        if (pos.col >= LadderRung.OUTPUT_COL) return
        pushUndo()
        updateCell(pos) { it.copy(element = LadderElement.HorizontalLine(id = uuid())) }
        _isModified.value = true
    }

    fun toggleVerticalLine() {
        val pos = _selectedCell.value ?: return
        pushUndo()
        val rungs = _rungs.value
        val rung = rungs.getOrNull(pos.rungIdx) ?: return
        val cell = rung.grid.getOrNull(pos.row)?.getOrNull(pos.col) ?: return
        val newHasBottom = !cell.hasBottom

        _rungs.update { rungList ->
            val list = rungList.toMutableList()
            val grid = rung.grid.map { it.toMutableList() }.toMutableList()
            grid[pos.row][pos.col] = cell.copy(hasBottom = newHasBottom)

            // 수직선 ON → 아래 행이 없으면 추가
            if (newHasBottom && pos.row >= grid.size - 1) {
                grid.add(LadderRung.emptyRow().toMutableList())
            }

            list[pos.rungIdx] = rung.copy(grid = grid)
            list
        }
        _isModified.value = true
    }

    // ══════════════════════════════════
    //  요소/행/런그 삭제
    // ══════════════════════════════════

    fun deleteSelectedElements() {
        val cells = _selectedCells.value
        if (cells.isEmpty()) return
        pushUndo()

        _rungs.update { rungs ->
            val list = rungs.map { it }.toMutableList()
            cells.forEach { pos ->
                if (pos.rungIdx < list.size) {
                    val rung = list[pos.rungIdx]
                    if (pos.row < rung.grid.size && pos.col < rung.grid[pos.row].size) {
                        val grid = rung.grid.map { it.toMutableList() }.toMutableList()
                        grid[pos.row][pos.col] = LadderCell()
                        list[pos.rungIdx] = rung.copy(grid = grid)
                    }
                }
            }
            list
        }
        _selectedCells.value = emptySet()
        _isModified.value = true
    }

    fun deleteRow() {
        val pos = _selectedCell.value ?: return
        pushUndo()
        _rungs.update { rungs ->
            val list = rungs.toMutableList()
            if (pos.rungIdx >= list.size) return@update rungs
            val rung = list[pos.rungIdx]

            if (rung.grid.size <= 1) {
                // 행이 1개뿐이면 런그 전체 삭제 (마지막 런그가 아닐 때만)
                if (list.size > 1) {
                    list.removeAt(pos.rungIdx)
                } else {
                    list[0] = LadderRung.empty()
                }
            } else {
                val grid = rung.grid.toMutableList()
                grid.removeAt(pos.row)
                list[pos.rungIdx] = rung.copy(grid = grid)
            }
            list
        }
        _selectedCell.value = null
        _isModified.value = true
    }

    fun addRow() {
        val pos = _selectedCell.value
        pushUndo()
        if (pos != null) {
            // 선택된 행 아래에 새 행 추가
            _rungs.update { rungs ->
                val list = rungs.toMutableList()
                if (pos.rungIdx < list.size) {
                    val rung = list[pos.rungIdx]
                    val grid = rung.grid.toMutableList()
                    grid.add(pos.row + 1, LadderRung.emptyRow())
                    list[pos.rungIdx] = rung.copy(grid = grid)
                }
                list
            }
            // 커서를 새 행의 첫 열로 이동
            val newPos = CellPosition(pos.rungIdx, pos.row + 1, 0)
            _selectedCell.value = newPos
            _selectedCells.value = setOf(newPos)
        } else {
            // 선택 없으면 새 런그 추가
            val newRungIdx = _rungs.value.size
            _rungs.update { it + LadderRung.empty() }
            // 커서를 새 런그의 첫 셀로
            val newPos = CellPosition(newRungIdx, 0, 0)
            _selectedCell.value = newPos
            _selectedCells.value = setOf(newPos)
        }
        _isModified.value = true
    }

    // ══════════════════════════════════
    //  요소 속성 편집
    // ══════════════════════════════════

    fun replaceElement(rungIdx: Int, row: Int, col: Int, element: LadderElement) {
        pushUndo()
        updateCell(CellPosition(rungIdx, row, col)) { it.copy(element = element) }
        element.address?.let { addr ->
            val label = element.label.ifBlank { generateAutoLabel(element) }
            if (label.isNotBlank()) {
                _ioLabels.update { it + (addr.toString() to label) }
            }
        }
        _isModified.value = true
    }

    fun setRungComment(rungIdx: Int, comment: String) {
        pushUndo()
        _rungs.update { rungs ->
            val list = rungs.toMutableList()
            if (rungIdx < list.size) {
                list[rungIdx] = list[rungIdx].copy(comment = comment)
            }
            list
        }
        _isModified.value = true
    }

    fun setLabel(address: IOAddress, label: String) {
        _ioLabels.update { it + (address.toString() to label) }
        _isModified.value = true
    }

    // ══════════════════════════════════
    //  Undo / Redo
    // ══════════════════════════════════

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_rungs.value)
        _rungs.value = undoStack.removeLast()
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_rungs.value)
        _rungs.value = redoStack.removeLast()
        updateUndoRedoState()
    }

    private fun pushUndo() {
        undoStack.addLast(_rungs.value)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // ══════════════════════════════════
    //  유틸리티
    // ══════════════════════════════════

    fun getUsedAddresses(): List<IOAddress> {
        return _rungs.value.flatMap { rung ->
            rung.grid.flatMap { row ->
                row.mapNotNull { cell -> cell.element?.address }
            }
        }.distinct().sortedWith(compareBy({ it.type.ordinal }, { it.number }))
    }

    fun getAutoNumbering() = autoNumbering

    private fun autoAssignAddress(element: LadderElement): LadderElement {
        if (element.address != null) return element
        return when (element) {
            is LadderElement.NormallyOpen -> element.copy(address = autoNumbering.nextInput())
            is LadderElement.NormallyClosed -> element.copy(address = autoNumbering.nextInput())
            is LadderElement.RisingEdgeContact -> element.copy(address = autoNumbering.nextInput())
            is LadderElement.FallingEdgeContact -> element.copy(address = autoNumbering.nextInput())
            is LadderElement.OutputCoil -> element.copy(address = autoNumbering.nextOutput())
            is LadderElement.SetCoil -> element.copy(address = autoNumbering.nextOutput())
            is LadderElement.ResetCoil -> element.copy(address = autoNumbering.nextOutput())
            is LadderElement.RisingEdge -> element.copy(address = autoNumbering.nextOutput())
            is LadderElement.FallingEdge -> element.copy(address = autoNumbering.nextOutput())
            is LadderElement.Timer -> {
                val addr = autoNumbering.nextTimer()
                element.copy(address = addr, timerNumber = addr.number)
            }
            is LadderElement.Counter -> {
                val addr = autoNumbering.nextCounter()
                element.copy(address = addr, counterNumber = addr.number)
            }
            else -> element
        }
    }

    private fun generateAutoLabel(element: LadderElement): String = when (element) {
        is LadderElement.NormallyOpen -> element.address?.toString() ?: ""
        is LadderElement.NormallyClosed -> element.address?.toString() ?: ""
        is LadderElement.OutputCoil -> element.address?.toString() ?: ""
        is LadderElement.Timer -> "T${element.timerNumber}"
        is LadderElement.Counter -> "C${element.counterNumber}"
        else -> ""
    }

    private fun isOutputElement(el: LadderElement): Boolean = when (el) {
        is LadderElement.OutputCoil, is LadderElement.SetCoil, is LadderElement.ResetCoil,
        is LadderElement.RisingEdge, is LadderElement.FallingEdge,
        is LadderElement.Timer, is LadderElement.Counter, is LadderElement.FunctionBlock -> true
        else -> false
    }

    private fun findLastContactCol(row: List<LadderCell>): Int {
        for (c in (LadderRung.CONTACT_COLS - 1) downTo 0) {
            val el = row[c].element
            if (el != null && el !is LadderElement.HorizontalLine) return c
        }
        return -1
    }

    private fun updateCell(pos: CellPosition, transform: (LadderCell) -> LadderCell) {
        _rungs.update { rungs ->
            val list = rungs.toMutableList()
            if (pos.rungIdx >= list.size) return@update rungs
            val rung = list[pos.rungIdx]
            if (pos.row >= rung.grid.size) return@update rungs
            val grid = rung.grid.map { it.toMutableList() }.toMutableList()
            if (pos.col < grid[pos.row].size) {
                grid[pos.row][pos.col] = transform(grid[pos.row][pos.col])
            }
            list[pos.rungIdx] = rung.copy(grid = grid)
            list
        }
    }

    private fun uuid() = UUID.randomUUID().toString()
}
