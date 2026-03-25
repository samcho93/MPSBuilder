package com.example.mpsbuilder.data.ladder

/**
 * 래더 → GX-Works2 CSV 내보내기
 * UTF-16 LE (파일용) 또는 UTF-8 (문자열용)
 */
object GxWorks2CsvExporter {

    fun exportString(rungs: List<LadderRung>, projectName: String = "MAIN"): String {
        val sb = StringBuilder()
        sb.appendLine("\"$projectName\"")
        sb.appendLine("\"PLC 정보:\"\t\"QCPU (Q mode)\"")
        sb.appendLine("\"스텝 번호\"\t\"행 간 스테이트먼트\"\t\"명령\"\t\"I/O(디바이스)\"\t\"\"\t\"\"\t\"\"")

        var step = 0
        for (rung in rungs) {
            val instructions = rungToMnemonics(rung)
            for (instr in instructions) {
                val comment = if (instr == instructions.first() && rung.comment.isNotBlank())
                    rung.comment else ""
                sb.appendLine("\"$step\"\t\"$comment\"\t\"${instr.cmd}\"\t\"${instr.operand}\"\t\"\"\t\"\"\t\"\"")
                step += instr.stepSize
                // K값 별도 행
                if (instr.kValue != null) {
                    sb.appendLine("\"\"\t\"\"\t\"\"\t\"K${instr.kValue}\"\t\"\"\t\"\"\t\"\"")
                }
            }
        }
        sb.appendLine("\"$step\"\t\"\"\t\"END\"\t\"\"\t\"\"\t\"\"\t\"\"")
        return sb.toString()
    }

    fun exportBytes(rungs: List<LadderRung>, projectName: String = "MAIN"): ByteArray {
        val csv = exportString(rungs, projectName)
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        return bom + csv.toByteArray(Charsets.UTF_16LE)
    }

    private data class Instruction(
        val cmd: String,
        val operand: String = "",
        val stepSize: Int = 1,
        val kValue: Int? = null
    )

    private fun rungToMnemonics(rung: LadderRung): List<Instruction> {
        val result = mutableListOf<Instruction>()
        val grid = rung.grid
        val rows = grid.size

        if (rows == 1) {
            // 단일 행
            singleRowToMnemonics(grid[0], result, isFirst = true)
        } else {
            // 다중 행 (OR 분기)
            for ((rowIdx, row) in grid.withIndex()) {
                singleRowToMnemonics(row, result, isFirst = (rowIdx == 0))
                if (rowIdx > 0) {
                    result.add(Instruction("ORB"))
                }
            }
        }
        return result
    }

    private fun singleRowToMnemonics(
        row: List<LadderCell>,
        result: MutableList<Instruction>,
        isFirst: Boolean
    ) {
        var firstContact = true
        // 접점 영역 (col 0 ~ CONTACT_COLS-1)
        for (col in 0 until LadderRung.CONTACT_COLS) {
            val el = row.getOrNull(col)?.element ?: continue
            if (el is LadderElement.HorizontalLine) continue

            val (cmd, addr) = elementToMnemonic(el, if (firstContact) "LD" else "AND")
            result.add(Instruction(cmd, addr))
            firstContact = false
        }

        // 출력 (col OUTPUT_COL)
        val outEl = row.getOrNull(LadderRung.OUTPUT_COL)?.element
        if (outEl != null) {
            val (cmd, addr, stepSize, kVal) = outputToMnemonic(outEl)
            result.add(Instruction(cmd, addr, stepSize, kVal))
        }
    }

    private fun elementToMnemonic(el: LadderElement, prefix: String): Pair<String, String> {
        return when (el) {
            is LadderElement.NormallyOpen -> prefix to (el.address?.toString() ?: "")
            is LadderElement.NormallyClosed -> "${prefix}I" to (el.address?.toString() ?: "")
            is LadderElement.RisingEdgeContact -> "${prefix}P" to (el.address?.toString() ?: "")
            is LadderElement.FallingEdgeContact -> "${prefix}F" to (el.address?.toString() ?: "")
            is LadderElement.SpecialRelay -> prefix to (el.address?.toString() ?: "")
            else -> prefix to ""
        }
    }

    private data class OutputMnemonic(
        val cmd: String, val operand: String,
        val stepSize: Int = 1, val kValue: Int? = null
    )

    private fun outputToMnemonic(el: LadderElement): OutputMnemonic {
        return when (el) {
            is LadderElement.OutputCoil -> OutputMnemonic("OUT", el.address?.toString() ?: "")
            is LadderElement.SetCoil -> OutputMnemonic("SET", el.address?.toString() ?: "")
            is LadderElement.ResetCoil -> OutputMnemonic("RST", el.address?.toString() ?: "")
            is LadderElement.RisingEdge -> OutputMnemonic("PLS", el.address?.toString() ?: "", 2)
            is LadderElement.FallingEdge -> OutputMnemonic("PLF", el.address?.toString() ?: "", 2)
            is LadderElement.Timer -> OutputMnemonic(
                "OUT", "T${el.timerNumber}", 1, el.preset
            )
            is LadderElement.Counter -> OutputMnemonic(
                "OUT", "C${el.counterNumber}", 1, el.preset
            )
            is LadderElement.FunctionBlock -> OutputMnemonic(
                el.mnemonic,
                listOf(el.operand1, el.operand2, el.operand3).filter { it.isNotBlank() }.joinToString(" "),
                3
            )
            else -> OutputMnemonic("NOP", "")
        }
    }
}
