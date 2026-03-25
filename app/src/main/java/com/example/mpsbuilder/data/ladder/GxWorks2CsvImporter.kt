package com.example.mpsbuilder.data.ladder
import java.util.UUID

/**
 * GX-Works2 CSV 파일 → 래더 런그 변환
 *
 * 지원 형식:
 * 1) GX-Works2 실제 내보내기 (UTF-16 LE + BOM + 탭 구분 + 따옴표)
 *    "스텝번호"\t"코멘트"\t"명령"\t"디바이스"\t""\t""\t""
 *
 * 2) 간단 CSV (UTF-8, 쉼표 구분)
 *    STEP,INSTRUCTION,P1,P2,P3,COMMENT
 *
 * 3) 텍스트 니모닉
 *    LD X000
 *    AND X001
 *    OUT Y000
 *
 * 지원 명령어: LD/LDI/LDP/LDF AND/ANI/ANDP/ANDF OR/ORI ORB ANB
 *   OUT SET RST PLS PLF  MPS MPP MRD MEP  타이머/카운터
 *   MOV ADD SUB MUL DIV CMP INC DEC FROM TO 등
 */
object GxWorks2CsvImporter {

    data class ImportResult(
        val rungs: List<LadderRung>,
        val programName: String = "MAIN",
        val ioLabels: Map<String, String> = emptyMap()
    )

    /**
     * 바이트 배열에서 인코딩 자동 감지 후 파싱
     */
    fun importBytes(bytes: ByteArray): ImportResult {
        val text = decodeAutoDetect(bytes)
        return import(text)
    }

    /**
     * 문자열에서 파싱 (UTF-8 이미 디코딩된 경우)
     */
    fun import(csvText: String): ImportResult {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(listOf(LadderRung.empty()))

        // 프로그램명 추출
        var programName = "MAIN"
        val rungComments = mutableMapOf<Int, String>() // stepIdx → 코멘트

        // 니모닉 추출
        val mnemonics = mutableListOf<MnemonicLine>()

        for (line in lines) {
            val parsed = parseLine(line, rungComments) ?: continue
            if (parsed.instruction == "END" || parsed.instruction == "FEND") break
            if (parsed.instruction.isEmpty()) continue

            // 첫 줄에서 프로그램명 추출
            if (mnemonics.isEmpty() && programName == "MAIN") {
                val cleaned = line.replace("\"", "").trim()
                if (!cleaned.first().isDigit() && !cleaned.uppercase().startsWith("STEP")
                    && !cleaned.uppercase().startsWith("LD")) {
                    programName = cleaned.split("\t", ",").first().trim()
                    continue
                }
            }

            mnemonics.add(parsed)
        }

        if (mnemonics.isEmpty()) return ImportResult(listOf(LadderRung.empty()), programName)

        val rungs = convertToRungs(mnemonics)

        return ImportResult(
            rungs = rungs.ifEmpty { listOf(LadderRung.empty()) },
            programName = programName
        )
    }

    // ── 인코딩 자동 감지 ──

    private fun decodeAutoDetect(bytes: ByteArray): String {
        // UTF-16 LE BOM: FF FE
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
        }
        // UTF-16 BE BOM: FE FF
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF")
        }
        // UTF-8 BOM: EF BB BF
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // UTF-16 LE without BOM (detect by null bytes pattern)
        if (bytes.size >= 4 && bytes[1] == 0x00.toByte() && bytes[3] == 0x00.toByte()) {
            return String(bytes, Charsets.UTF_16LE)
        }
        // Default: UTF-8
        return String(bytes, Charsets.UTF_8)
    }

    // ── 라인 파싱 ──

    private data class MnemonicLine(
        val instruction: String,
        val p1: String = "",
        val p2: String = "",
        val p3: String = "",
        val comment: String = ""
    )

    private fun parseLine(line: String, comments: MutableMap<Int, String>): MnemonicLine? {
        // 따옴표 제거 + 탭/쉼표 분리
        val cleaned = line.replace("\"", "")
        val parts = if (cleaned.contains("\t")) {
            cleaned.split("\t").map { it.trim() }
        } else if (cleaned.contains(",")) {
            cleaned.split(",").map { it.trim() }
        } else {
            cleaned.split("\\s+".toRegex()).map { it.trim() }
        }

        if (parts.isEmpty()) return null

        // 헤더/PLC 정보 행 스킵
        val first = parts[0].uppercase()
        if (first.startsWith("PLC") || first.startsWith("스텝") || first.startsWith("STEP")
            || first == "PROGRAM" || first.contains("정보")) return null

        // GX-Works2 형식: "스텝번호" "코멘트" "명령" "디바이스" "" "" ""
        if (parts[0].toIntOrNull() != null || parts[0].isEmpty()) {
            val stepNum = parts[0].toIntOrNull()
            val comment = parts.getOrNull(1) ?: ""
            val instruction = parts.getOrNull(2)?.uppercase()?.trim() ?: ""
            val device = parts.getOrNull(3)?.trim() ?: ""

            if (instruction.isEmpty()) {
                if (comment.isNotBlank() && stepNum != null) {
                    comments[stepNum] = comment
                }
                // K값만 있는 행 (스텝번호 없고, 명령 없고, 디바이스에 K값)
                // 예: ""  ""  ""  "K2"  ""  ""  ""
                if (stepNum == null && device.uppercase().startsWith("K")) {
                    return MnemonicLine(instruction = "_KVAL", p1 = device)
                }
                return null
            }

            if (instruction !in KNOWN_INSTRUCTIONS) return null

            return MnemonicLine(
                instruction = instruction, p1 = device,
                p2 = parts.getOrNull(4)?.trim() ?: "",
                comment = comment
            )
        }

        // 간단 형식: "LD X000"
        val cmd = parts[0].uppercase()
        if (cmd !in KNOWN_INSTRUCTIONS) return null

        return MnemonicLine(
            instruction = cmd,
            p1 = parts.getOrNull(1) ?: "",
            p2 = parts.getOrNull(2) ?: "",
            p3 = parts.getOrNull(3) ?: ""
        )
    }

    // ── 니모닉 → 런그 변환 (MPS/MRD/MPP 스택 분기 지원) ──

    /**
     * MPS/MRD/MPP:
     *   LD M2 → OUT M500 → MPS (M2 저장)
     *   AND M600 → OUT M601 → RST M2  (각 출력은 M2 기반 독립 런그)
     *   MPP (M2 복원, 스택 제거)
     *   AND SM412 → OUT M501 (M2·SM412 런그)
     *
     * 핵심: 출력 시 접점을 지우지 않고 MPS 스택에서 복원
     */
    private fun convertToRungs(mnemonics: List<MnemonicLine>): List<LadderRung> {
        val rungs = mutableListOf<LadderRung>()
        val contacts = mutableListOf<LadderElement>()
        val orBranches = mutableListOf<MutableList<LadderElement>>()
        val seriesAfterOr = mutableListOf<LadderElement>()
        var inOrBlock = false
        var currentComment = ""

        // MPS 스택: 분기점의 접점을 저장
        val mpsContactsStack = mutableListOf<List<LadderElement>>()
        var lastContactsBeforeEmit = listOf<LadderElement>()

        // MPS 분기 수집: MPS 활성 시 런그를 바로 생성하지 않고 행으로 수집
        data class MpsBranch(val contactElements: List<LadderElement>, val output: LadderElement)
        var mpsBranches: MutableList<MpsBranch>? = null  // null이면 MPS 비활성
        var mpsBaseContacts = listOf<LadderElement>()     // MPS 시점의 공통 접점

        /** MPS 분기들을 하나의 다중행 런그로 병합 */
        fun flushMpsBranches() {
            val branches = mpsBranches ?: return
            if (branches.isEmpty()) { mpsBranches = null; return }

            val base = mpsBaseContacts
            val grid = mutableListOf<List<LadderCell>>()

            branches.forEachIndexed { branchIdx, branch ->
                val row = MutableList(LadderRung.GRID_COLS) { LadderCell() }

                // 공통 접점 (첫 행만)
                var col = 0
                if (branchIdx == 0) {
                    base.forEach { el ->
                        if (col < LadderRung.CONTACT_COLS) {
                            row[col] = LadderCell(element = el); col++
                        }
                    }
                }

                // 비첫번째 행: base 영역(col 0 ~ base.size-1)은 비워둠 (null)
                // → 수직선으로만 상위 행 신호 전달, 수평선 표시 없음
                // (null cell + hasBottom = 수직선만 그려짐)

                // 분기별 추가 접점
                val startCol = if (branchIdx == 0) col else base.size
                var c = startCol
                branch.contactElements.forEach { el ->
                    if (c < LadderRung.CONTACT_COLS) {
                        row[c] = LadderCell(element = el); c++
                    }
                }

                // HLine 채움 (분기 접점 이후 ~ 출력까지)
                for (fc in c until LadderRung.OUTPUT_COL) {
                    if (row[fc].element == null)
                        row[fc] = LadderCell(element = LadderElement.HorizontalLine(id = uuid()))
                }

                // 출력
                row[LadderRung.OUTPUT_COL] = LadderCell(element = branch.output)

                // 수직선
                if (branchIdx < branches.size - 1) {
                    val vCol = (base.size - 1).coerceAtLeast(0)
                    row[vCol] = row[vCol].copy(hasBottom = true)
                }

                grid.add(row)
            }

            // 동일 접점은 병합하지 않음 — 각 행에 명시적으로 접점 표시
            // (사용자가 각 행의 조건을 바로 확인 가능)
            rungs.add(LadderRung(grid = grid, comment = currentComment))

            mpsBranches = null; mpsBaseContacts = emptyList()
            contacts.clear(); orBranches.clear(); seriesAfterOr.clear()
            inOrBlock = false; currentComment = ""
        }

        fun emitRung(output: LadderElement) {
            if (contacts.isNotEmpty()) {
                orBranches.add(ArrayList(contacts)); contacts.clear()
            }
            lastContactsBeforeEmit = orBranches.flatMap { it } + seriesAfterOr

            // MPS 활성이면 분기로 수집
            if (mpsBranches != null) {
                val allContacts = lastContactsBeforeEmit
                val extraContacts = if (allContacts.size > mpsBaseContacts.size) {
                    allContacts.subList(mpsBaseContacts.size, allContacts.size)
                } else emptyList()
                mpsBranches!!.add(MpsBranch(ArrayList(extraContacts), output))

                if (mpsContactsStack.isEmpty()) {
                    flushMpsBranches(); return
                }
                contacts.clear(); contacts.addAll(lastContactsBeforeEmit)
                orBranches.clear(); seriesAfterOr.clear(); inOrBlock = false
                return
            }

            val rung = buildGrid(
                ArrayList(orBranches), ArrayList(seriesAfterOr), output, currentComment
            )
            rungs.add(rung)
            contacts.clear(); orBranches.clear(); seriesAfterOr.clear()
            inOrBlock = false; currentComment = ""
        }

        fun makeOutput(m: MnemonicLine, idx: Int): LadderElement {
            val p1 = m.p1
            return when (m.instruction) {
                "OUT" -> when {
                    p1.uppercase().startsWith("T") -> {
                        val tNum = p1.removePrefix("T").removePrefix("t").toIntOrNull() ?: 0
                        val preset = findPresetValue(m.p2, mnemonics, idx)
                        LadderElement.Timer(uuid(), IOAddress(IOAddress.AddressType.T, tNum), timerNumber = tNum, preset = preset)
                    }
                    p1.uppercase().startsWith("C") -> {
                        val cNum = p1.removePrefix("C").removePrefix("c").toIntOrNull() ?: 0
                        val preset = findPresetValue(m.p2, mnemonics, idx)
                        LadderElement.Counter(uuid(), IOAddress(IOAddress.AddressType.C, cNum), counterNumber = cNum, preset = preset)
                    }
                    else -> LadderElement.OutputCoil(uuid(), parseAddr(p1))
                }
                "SET" -> LadderElement.SetCoil(uuid(), parseAddr(p1))
                "RST" -> LadderElement.ResetCoil(uuid(), parseAddr(p1))
                "PLS" -> LadderElement.RisingEdge(uuid(), parseAddr(p1))
                "PLF" -> LadderElement.FallingEdge(uuid(), parseAddr(p1))
                else -> LadderElement.FunctionBlock(id = uuid(), label = m.instruction, mnemonic = m.instruction, operand1 = m.p1, operand2 = m.p2, operand3 = m.p3)
            }
        }

        for ((idx, m) in mnemonics.withIndex()) {
            when (m.instruction) {
                "LD" -> {
                    if (contacts.isNotEmpty()) { orBranches.add(ArrayList(contacts)); contacts.clear() }
                    contacts.add(createContact(m.p1, false))
                    if (m.comment.isNotBlank()) currentComment = m.comment
                }
                "LDI" -> {
                    if (contacts.isNotEmpty()) { orBranches.add(ArrayList(contacts)); contacts.clear() }
                    contacts.add(createContact(m.p1, true))
                }
                "LDP" -> {
                    if (contacts.isNotEmpty()) { orBranches.add(ArrayList(contacts)); contacts.clear() }
                    contacts.add(createRisingEdge(m.p1))
                }
                "LDF" -> {
                    if (contacts.isNotEmpty()) { orBranches.add(ArrayList(contacts)); contacts.clear() }
                    contacts.add(createFallingEdge(m.p1))
                }
                "AND"  -> { if (inOrBlock) seriesAfterOr.add(createContact(m.p1, false)) else contacts.add(createContact(m.p1, false)) }
                "ANI"  -> { if (inOrBlock) seriesAfterOr.add(createContact(m.p1, true)) else contacts.add(createContact(m.p1, true)) }
                "ANDP" -> { if (inOrBlock) seriesAfterOr.add(createRisingEdge(m.p1)) else contacts.add(createRisingEdge(m.p1)) }
                "ANDF" -> { if (inOrBlock) seriesAfterOr.add(createFallingEdge(m.p1)) else contacts.add(createFallingEdge(m.p1)) }
                "OR"   -> { orBranches.add(ArrayList(contacts)); contacts.clear(); contacts.add(createContact(m.p1, false)); inOrBlock = true }
                "ORI"  -> { orBranches.add(ArrayList(contacts)); contacts.clear(); contacts.add(createContact(m.p1, true)); inOrBlock = true }
                "ORB"  -> { inOrBlock = true }
                "ANB"  -> { }

                // ── MPS/MRD/MPP ──
                "MPS" -> {
                    val allContacts = mutableListOf<LadderElement>()
                    orBranches.forEach { allContacts.addAll(it) }
                    allContacts.addAll(contacts)
                    allContacts.addAll(seriesAfterOr)

                    val toSave = if (allContacts.isEmpty() && lastContactsBeforeEmit.isNotEmpty()) {
                        ArrayList(lastContactsBeforeEmit)
                    } else {
                        ArrayList(allContacts)
                    }
                    mpsContactsStack.add(toSave)

                    // MPS 분기 수집 시작
                    mpsBaseContacts = ArrayList(toSave)
                    mpsBranches = mutableListOf()

                    // 직전 OUT이 있었으면 (방금 emitRung으로 별도 런그 생성됨)
                    // → 그 런그를 제거하고 첫 번째 분기로 편입
                    if (rungs.isNotEmpty()) {
                        val lastRung = rungs.last()
                        // 마지막 런그의 출력 추출
                        val lastOutput = lastRung.grid.firstOrNull()
                            ?.getOrNull(LadderRung.OUTPUT_COL)?.element
                        if (lastOutput != null) {
                            rungs.removeAt(rungs.lastIndex)
                            mpsBranches!!.add(MpsBranch(emptyList(), lastOutput))
                        }
                    }

                    // 접점 복원
                    if (contacts.isEmpty() && orBranches.isEmpty()) {
                        contacts.addAll(toSave)
                    }
                }
                "MRD" -> {
                    // 스택 읽기 → 접점 복원 (분기 계속)
                    if (mpsContactsStack.isNotEmpty()) {
                        contacts.clear(); contacts.addAll(mpsContactsStack.last())
                        orBranches.clear(); seriesAfterOr.clear(); inOrBlock = false
                    }
                }
                "MPP" -> {
                    // 스택 팝 → 접점 복원 (마지막 분기가 아직 남아있을 수 있음)
                    if (mpsContactsStack.isNotEmpty()) {
                        contacts.clear(); contacts.addAll(mpsContactsStack.removeLast())
                        orBranches.clear(); seriesAfterOr.clear(); inOrBlock = false
                    }
                    // flushMpsBranches는 다음 출력 후 호출됨
                }
                "MEP" -> {
                    // 연산 결과 상승 펄스화 — AND 위치에서 사용
                    // 래더에서는 "MEP" 표시용 특수 접점으로 처리
                    // 시뮬레이터에서는 이전 연산 결과의 상승에지 감지
                    contacts.add(LadderElement.RisingEdgeContact(
                        id = uuid(),
                        address = IOAddress(IOAddress.AddressType.SM, 9999),
                        label = "MEP"
                    ))
                }
                "MEF" -> {
                    // 연산 결과 하강 펄스화
                    contacts.add(LadderElement.FallingEdgeContact(
                        id = uuid(),
                        address = IOAddress(IOAddress.AddressType.SM, 9998),
                        label = "MEF"
                    ))
                }
                "INV" -> {
                    // 연산 결과 반전 — NormallyClosed처럼 동작
                    contacts.add(LadderElement.NormallyClosed(
                        id = uuid(),
                        address = IOAddress(IOAddress.AddressType.SM, 9997),
                        label = "INV"
                    ))
                }
                "_KVAL" -> { /* K값 행 — findPresetValue에서 처리됨, 스킵 */ }

                // ── 출력 명령어 → 런그 생성 ──
                "OUT", "SET", "RST", "PLS", "PLF" -> {
                    emitRung(makeOutput(m, idx))
                }

                // ── f(x) 명령어 ──
                "MOV","MOVP","DMOV","FMOV","BMOV",
                "CMP","DCMP","ADD","SUB","MUL","DIV","DADD","DSUB",
                "WAND","WOR","WXOR","CML","SHL","SHR","ROL","ROR",
                "BCD","BIN","DECO","ENCO","INC","DEC","DINC","DDEC",
                "FROM","TO" -> {
                    emitRung(LadderElement.FunctionBlock(id = uuid(), label = m.instruction, mnemonic = m.instruction, operand1 = m.p1, operand2 = m.p2, operand3 = m.p3))
                }
            }
        }

        // 남은 접점
        if (contacts.isNotEmpty() || orBranches.isNotEmpty()) {
            if (contacts.isNotEmpty()) orBranches.add(contacts)
            rungs.add(buildGrid(orBranches, seriesAfterOr, null, currentComment))
        }

        return rungs
    }

    /** T/C의 K값 찾기 — 같은 행 p2 또는 다음 행에 K값만 있는 경우 */
    private fun findPresetValue(p2: String, mnemonics: List<MnemonicLine>, currentIdx: Int): Int {
        // 같은 행에 K값이 있으면
        val fromP2 = p2.removePrefix("K").removePrefix("k").toIntOrNull()
        if (fromP2 != null) return fromP2

        // 다음 행들에서 K값 검색 (_KVAL 행 또는 K로 시작하는 p1)
        for (offset in 1..3) {
            val nextIdx = currentIdx + offset
            if (nextIdx >= mnemonics.size) break
            val next = mnemonics[nextIdx]
            // _KVAL 행 (K값만 있는 별도 행)
            if (next.instruction == "_KVAL") {
                return next.p1.removePrefix("K").removePrefix("k").toIntOrNull() ?: 100
            }
            // K로 시작하는 디바이스
            if (next.p1.uppercase().startsWith("K") && next.instruction.isEmpty()) {
                return next.p1.removePrefix("K").removePrefix("k").toIntOrNull() ?: 100
            }
            // 다른 명령어가 나오면 중단
            if (next.instruction.isNotEmpty() && next.instruction != "_KVAL") break
        }
        return 100 // 기본값
    }

    // ── 그리드 빌더 ──

    private fun buildGrid(
        orBranches: List<List<LadderElement>>,
        seriesAfterOr: List<LadderElement>,
        output: LadderElement?,
        comment: String
    ): LadderRung {
        val branches = orBranches.ifEmpty { listOf(emptyList()) }
        val maxBranchLen = branches.maxOfOrNull { it.size } ?: 0
        val mergeCol = (maxBranchLen - 1).coerceAtLeast(0)
        val rows = mutableListOf<MutableList<LadderCell>>()

        for ((branchIdx, branch) in branches.withIndex()) {
            val row = MutableList(LadderRung.GRID_COLS) { LadderCell() }

            for ((colIdx, element) in branch.withIndex()) {
                if (colIdx < LadderRung.CONTACT_COLS) row[colIdx] = LadderCell(element = element)
            }

            for (c in branch.size..mergeCol) {
                if (c < LadderRung.CONTACT_COLS && row[c].element == null)
                    row[c] = LadderCell(element = LadderElement.HorizontalLine(id = uuid()))
            }

            if (branchIdx == 0) {
                var seriesCol = mergeCol + 1
                for (el in seriesAfterOr) {
                    if (seriesCol < LadderRung.CONTACT_COLS) {
                        row[seriesCol] = LadderCell(element = el); seriesCol++
                    }
                }
                val lastFilledCol = (mergeCol + 1 + seriesAfterOr.size).coerceAtMost(LadderRung.CONTACT_COLS)
                for (c in lastFilledCol until LadderRung.OUTPUT_COL) {
                    if (row[c].element == null)
                        row[c] = LadderCell(element = LadderElement.HorizontalLine(id = uuid()))
                }
                if (output != null) row[LadderRung.OUTPUT_COL] = LadderCell(element = output)
            }

            if (branchIdx > 0 && rows.isNotEmpty()) {
                val prevRow = rows[branchIdx - 1]
                if (mergeCol < LadderRung.GRID_COLS)
                    prevRow[mergeCol] = prevRow[mergeCol].copy(hasBottom = true)
            }

            rows.add(row)
        }

        return LadderRung(grid = rows, comment = comment)
    }

    // ── 헬퍼 ──

    private fun uuid() = UUID.randomUUID().toString()

    private fun createContact(operand: String, negated: Boolean): LadderElement {
        val addr = parseAddr(operand)
        return if (addr?.type == IOAddress.AddressType.SM) {
            LadderElement.SpecialRelay(id = uuid(), address = addr)
        } else if (negated) {
            LadderElement.NormallyClosed(id = uuid(), address = addr)
        } else {
            LadderElement.NormallyOpen(id = uuid(), address = addr)
        }
    }

    private fun createRisingEdge(operand: String): LadderElement =
        LadderElement.RisingEdgeContact(id = uuid(), address = parseAddr(operand))

    private fun createFallingEdge(operand: String): LadderElement =
        LadderElement.FallingEdgeContact(id = uuid(), address = parseAddr(operand))

    private fun parseAddr(str: String): IOAddress? {
        val s = str.trim().uppercase()
        return try {
            when {
                s.startsWith("SM") -> IOAddress(IOAddress.AddressType.SM, s.removePrefix("SM").toInt())
                s.startsWith("SD") -> IOAddress(IOAddress.AddressType.SD, s.removePrefix("SD").toInt())
                s.startsWith("X") -> IOAddress(IOAddress.AddressType.X, s.removePrefix("X").toInt(16))
                s.startsWith("Y") -> IOAddress(IOAddress.AddressType.Y, s.removePrefix("Y").toInt(16))
                s.startsWith("M") -> IOAddress(IOAddress.AddressType.M, s.removePrefix("M").toInt())
                s.startsWith("T") -> IOAddress(IOAddress.AddressType.T, s.removePrefix("T").toInt())
                s.startsWith("C") -> IOAddress(IOAddress.AddressType.C, s.removePrefix("C").toInt())
                s.startsWith("D") -> IOAddress(IOAddress.AddressType.D, s.removePrefix("D").toInt())
                s.startsWith("S") -> IOAddress(IOAddress.AddressType.S, s.removePrefix("S").toInt())
                else -> null
            }
        } catch (_: NumberFormatException) { null }
    }

    private val KNOWN_INSTRUCTIONS = setOf(
        "LD", "LDI", "LDP", "LDF", "AND", "ANI", "ANDP", "ANDF",
        "OR", "ORI", "ORB", "ANB",
        "OUT", "SET", "RST", "PLS", "PLF",
        "MPS", "MRD", "MPP", "MEP", "MEF",  // 스택 분기 + 펄스화
        "INV",  // 연산 결과 반전
        "NOP",  // No Operation
        "MOV", "MOVP", "DMOV", "FMOV", "BMOV",
        "CMP", "DCMP", "ADD", "SUB", "MUL", "DIV", "DADD", "DSUB",
        "WAND", "WOR", "WXOR", "CML",
        "SHL", "SHR", "ROL", "ROR",
        "BCD", "BIN", "DECO", "ENCO",
        "INC", "DEC", "DINC", "DDEC",
        "FROM", "TO", "END", "FEND",
        "_KVAL"  // 내부용: K값만 있는 별도 행
    )
}
