package com.example.mpsbuilder.data.ladder

/**
 * 래더 시뮬레이터 엔진 — 순수 함수, Android 의존 없음
 * GX-Works2 그리드 기반 런그 평가 + 데이터 레지스터(D) 연산
 */
object LadderSimulator {

    fun scan(
        rungs: List<LadderRung>,
        memory: Map<String, Boolean>,
        timerCurrentValues: Map<Int, Int> = emptyMap(),
        counterCurrentValues: Map<Int, Int> = emptyMap(),
        dataRegisters: Map<Int, Int> = emptyMap(),
        // bufferMemory removed — no intelligent modules in workbench builder
        scanTimeMs: Long = 20L
    ): ScanResult {
        val mem = memory.toMutableMap()
        val timers = timerCurrentValues.toMutableMap()
        val counters = counterCurrentValues.toMutableMap()
        val dregs = dataRegisters.toMutableMap()
        // bufMem removed

        mem["SM400"] = true
        mem["SM401"] = false

        rungs.forEach { rung ->
            val conditionResults = evaluateGrid(rung, mem)

            rung.grid.forEachIndexed { rowIdx, row ->
                val outputCell = row[LadderRung.OUTPUT_COL]
                val conditionMet = conditionResults[rowIdx]
                val output = outputCell.element ?: return@forEachIndexed

                when (output) {
                    is LadderElement.OutputCoil -> {
                        val addr = output.address?.toString() ?: return@forEachIndexed
                        mem[addr] = conditionMet
                    }
                    is LadderElement.SetCoil -> {
                        val addr = output.address?.toString() ?: return@forEachIndexed
                        if (conditionMet) mem[addr] = true
                    }
                    is LadderElement.ResetCoil -> {
                        val addr = output.address?.toString() ?: return@forEachIndexed
                        if (conditionMet) mem[addr] = false
                    }
                    is LadderElement.RisingEdge -> {
                        val addr = output.address?.toString() ?: return@forEachIndexed
                        val prevKey = "_PLS_PREV_$addr"
                        val prev = mem[prevKey] ?: false
                        mem[prevKey] = conditionMet
                        mem[addr] = conditionMet && !prev
                    }
                    is LadderElement.FallingEdge -> {
                        val addr = output.address?.toString() ?: return@forEachIndexed
                        val prevKey = "_PLF_PREV_$addr"
                        val prev = mem[prevKey] ?: false
                        mem[prevKey] = conditionMet
                        mem[addr] = !conditionMet && prev
                    }
                    is LadderElement.Timer -> {
                        val tNum = output.timerNumber
                        val tAddr = "T$tNum"
                        if (conditionMet) {
                            val current = timers.getOrDefault(tNum, 0)
                            val newVal = current + scanTimeMs.toInt()
                            timers[tNum] = newVal
                            mem[tAddr] = newVal >= output.preset * 100
                        } else {
                            timers[tNum] = 0
                            mem[tAddr] = false
                        }
                    }
                    is LadderElement.Counter -> {
                        val cNum = output.counterNumber
                        val cAddr = "C$cNum"
                        val prevKey = "_CNT_PREV_$cNum"
                        val prev = mem[prevKey] ?: false
                        mem[prevKey] = conditionMet
                        if (conditionMet && !prev) {
                            val current = counters.getOrDefault(cNum, 0) + 1
                            counters[cNum] = current
                            mem[cAddr] = current >= output.preset
                        }
                    }
                    is LadderElement.FunctionBlock -> {
                        if (conditionMet) executeFunctionBlock(output, mem, dregs)
                    }
                    else -> {}
                }
            }
        }

        return ScanResult(mem, timers, counters, dregs)
    }

    // ── 그리드 평가 (변경 없음) ──
    private fun evaluateGrid(
        rung: LadderRung,
        memory: Map<String, Boolean>
    ): List<Boolean> {
        val rows = rung.rowCount
        val cols = LadderRung.GRID_COLS
        val powered = Array(rows) { BooleanArray(cols) }

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val cell = rung.grid[row][col]
                val inputPower = if (col == 0) true else powered[row][col - 1]
                val element = cell.element
                val passThrough = when (element) {
                    null -> false
                    is LadderElement.HorizontalLine -> inputPower
                    is LadderElement.NormallyOpen -> {
                        val bit = memory[element.address?.toString()] ?: false
                        inputPower && bit
                    }
                    is LadderElement.NormallyClosed -> {
                        val bit = memory[element.address?.toString()] ?: false
                        inputPower && !bit
                    }
                    is LadderElement.RisingEdgeContact -> {
                        val addr = element.address?.toString() ?: ""
                        val bit = memory[addr] ?: false
                        val prev = memory["_CONT_PREV_$addr"] ?: false
                        inputPower && bit && !prev
                    }
                    is LadderElement.FallingEdgeContact -> {
                        val addr = element.address?.toString() ?: ""
                        val bit = memory[addr] ?: false
                        val prev = memory["_CONT_PREV_$addr"] ?: false
                        inputPower && !bit && prev
                    }
                    is LadderElement.SpecialRelay -> {
                        val bit = memory[element.address?.toString()] ?: false
                        inputPower && bit
                    }
                    else -> inputPower
                }
                powered[row][col] = passThrough
            }

            var changed = true
            while (changed) {
                changed = false
                for (row in 0 until rows) {
                    val cell = rung.grid[row][col]
                    if (cell.hasBottom && row + 1 < rows) {
                        if (powered[row][col] && !powered[row + 1][col]) {
                            powered[row + 1][col] = true; changed = true
                        }
                        if (powered[row + 1][col] && !powered[row][col]) {
                            powered[row][col] = true; changed = true
                        }
                    }
                }
            }
        }

        return List(rows) { row -> powered[row][LadderRung.OUTPUT_COL] }
    }

    // ── FunctionBlock 실행 ──

    /** 오퍼랜드 값 읽기: K100→100, D0→dregs[0], C0→counters 등 */
    private fun readOperand(op: String, dregs: Map<Int, Int>, mem: Map<String, Boolean>): Int {
        val s = op.trim().uppercase()
        return when {
            s.startsWith("K") -> s.removePrefix("K").toIntOrNull() ?: 0
            s.startsWith("H") -> s.removePrefix("H").toIntOrNull(16) ?: 0
            s.startsWith("D") -> dregs[s.removePrefix("D").toIntOrNull() ?: 0] ?: 0
            s.startsWith("T") -> 0 // 타이머 현재값은 별도
            s.startsWith("C") -> 0 // 카운터 현재값은 별도
            else -> s.toIntOrNull() ?: 0
        }
    }

    /** 오퍼랜드에 값 쓰기: D0→dregs[0] */
    private fun writeOperand(op: String, value: Int, dregs: MutableMap<Int, Int>) {
        val s = op.trim().uppercase()
        if (s.startsWith("D")) {
            val idx = s.removePrefix("D").toIntOrNull() ?: return
            dregs[idx] = value.coerceIn(-32768, 32767) // 16비트 범위
        }
    }

    private fun executeFunctionBlock(
        fb: LadderElement.FunctionBlock,
        mem: MutableMap<String, Boolean>,
        dregs: MutableMap<Int, Int>,
    ) {
        val op1 = fb.operand1
        val op2 = fb.operand2
        val op3 = fb.operand3

        when (fb.mnemonic.uppercase()) {
            // ── 전송 ──
            "MOV", "MOVP" -> {
                // MOV src dst : src → dst
                val src = readOperand(op1, dregs, mem)
                writeOperand(op2, src, dregs)
            }
            "DMOV" -> {
                val src = readOperand(op1, dregs, mem)
                writeOperand(op2, src, dregs)
            }
            "FMOV" -> {
                // FMOV src dst n : src 값을 dst~dst+(n-1)에 채움
                val src = readOperand(op1, dregs, mem)
                val dstBase = op2.trim().uppercase().removePrefix("D").toIntOrNull() ?: return
                val n = readOperand(op3, dregs, mem)
                for (i in 0 until n) {
                    dregs[dstBase + i] = src.coerceIn(-32768, 32767)
                }
            }

            // ── 비교 ──
            "CMP", "DCMP" -> {
                // CMP src1 src2 dst : 비교 결과를 dst, dst+1, dst+2 비트에 저장
                // dst: <, =, > 순서로 M 비트 세트
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                val dstBase = op3.trim().uppercase()
                if (dstBase.startsWith("M")) {
                    val idx = dstBase.removePrefix("M").toIntOrNull() ?: return
                    mem["M$idx"] = v1 < v2       // M(n): src1 < src2
                    mem["M${idx + 1}"] = v1 == v2 // M(n+1): src1 = src2
                    mem["M${idx + 2}"] = v1 > v2  // M(n+2): src1 > src2
                }
            }

            // ── 사칙연산 ──
            "ADD", "DADD" -> {
                // ADD src1 src2 dst
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 + v2, dregs)
            }
            "SUB", "DSUB" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 - v2, dregs)
            }
            "MUL" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 * v2, dregs)
            }
            "DIV" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                if (v2 != 0) {
                    writeOperand(op3, v1 / v2, dregs)
                }
            }

            // ── 증감 ──
            "INC", "DINC" -> {
                // INC dst
                val cur = readOperand(op1, dregs, mem)
                writeOperand(op1, cur + 1, dregs)
            }
            "DEC", "DDEC" -> {
                val cur = readOperand(op1, dregs, mem)
                writeOperand(op1, cur - 1, dregs)
            }

            // ── 비트연산 ──
            "WAND" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 and v2, dregs)
            }
            "WOR" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 or v2, dregs)
            }
            "WXOR" -> {
                val v1 = readOperand(op1, dregs, mem)
                val v2 = readOperand(op2, dregs, mem)
                writeOperand(op3, v1 xor v2, dregs)
            }
            "CML" -> {
                // CML src dst: 보수
                val v = readOperand(op1, dregs, mem)
                writeOperand(op2, v.inv() and 0xFFFF, dregs)
            }

            // ── 시프트 ──
            "SHL" -> {
                val v = readOperand(op1, dregs, mem)
                val n = readOperand(op2, dregs, mem)
                writeOperand(op3, (v shl n) and 0xFFFF, dregs)
            }
            "SHR" -> {
                val v = readOperand(op1, dregs, mem)
                val n = readOperand(op2, dregs, mem)
                writeOperand(op3, (v shr n) and 0xFFFF, dregs)
            }
            "ROL" -> {
                val v = readOperand(op1, dregs, mem) and 0xFFFF
                val n = readOperand(op2, dregs, mem) % 16
                writeOperand(op3, ((v shl n) or (v shr (16 - n))) and 0xFFFF, dregs)
            }
            "ROR" -> {
                val v = readOperand(op1, dregs, mem) and 0xFFFF
                val n = readOperand(op2, dregs, mem) % 16
                writeOperand(op3, ((v shr n) or (v shl (16 - n))) and 0xFFFF, dregs)
            }

            // ── 변환 ──
            "BCD" -> {
                // BIN→BCD 변환
                val v = readOperand(op1, dregs, mem)
                val bcd = ((v / 1000) % 10 shl 12) or ((v / 100) % 10 shl 8) or
                        ((v / 10) % 10 shl 4) or (v % 10)
                writeOperand(op2, bcd, dregs)
            }
            "BIN" -> {
                // BCD→BIN 변환
                val v = readOperand(op1, dregs, mem)
                val bin = ((v shr 12) and 0xF) * 1000 + ((v shr 8) and 0xF) * 100 +
                        ((v shr 4) and 0xF) * 10 + (v and 0xF)
                writeOperand(op2, bin, dregs)
            }

            // ── BMOV (블록 전송) ──
            "BMOV" -> {
                val srcBase = op1.trim().uppercase().removePrefix("D").toIntOrNull() ?: return
                val dstBase = op2.trim().uppercase().removePrefix("D").toIntOrNull() ?: return
                val n = readOperand(op3, dregs, mem)
                for (i in 0 until n) {
                    dregs[dstBase + i] = dregs[srcBase + i] ?: 0
                }
            }

            // ── FROM (인텔리전트 모듈 버퍼메모리 읽기) ──
        }
    }

    data class ScanResult(
        val memory: Map<String, Boolean>,
        val timerValues: Map<Int, Int>,
        val counterValues: Map<Int, Int>,
        val dataRegisters: Map<Int, Int> = emptyMap()
    )
}
