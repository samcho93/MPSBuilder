package com.example.mpsbuilder.data.ladder

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LadderRung(
    val id: String = UUID.randomUUID().toString(),
    val grid: List<List<LadderCell>> = listOf(emptyRow()),
    val comment: String = ""
) {
    val rowCount: Int get() = grid.size

    companion object {
        const val GRID_COLS = 12
        const val CONTACT_COLS = 11
        const val OUTPUT_COL = 11

        fun emptyRow(): List<LadderCell> = List(GRID_COLS) { LadderCell() }
        fun empty(): LadderRung = LadderRung(grid = listOf(emptyRow()))
    }
}

@Serializable
data class LadderCell(
    val element: LadderElement? = null,
    val hasBottom: Boolean = false
)
