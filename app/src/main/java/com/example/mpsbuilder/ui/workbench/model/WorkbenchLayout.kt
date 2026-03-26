package com.example.mpsbuilder.ui.workbench.model

import com.example.mpsbuilder.data.ladder.LadderRung
import kotlinx.serialization.Serializable

@Serializable
data class WorkbenchLayout(
    val version: Int = 1,
    val name: String = "NewLayout",
    val workbenchWidthDp: Float = 1200f,
    val workbenchHeightDp: Float = 800f,
    val placedWidgets: List<PlacedWidgetState> = emptyList(),
    val workpieces: List<Workpiece> = emptyList(),
    val ioLabelMap: Map<String, String> = emptyMap(),
    // 래더 데이터 (래더가 있으면 함께 저장)
    val ladderRungs: List<LadderRung> = emptyList(),
    val ladderIoLabels: Map<String, String> = emptyMap()
)
