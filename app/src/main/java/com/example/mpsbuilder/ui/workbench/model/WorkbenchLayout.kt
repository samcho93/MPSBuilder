package com.example.mpsbuilder.ui.workbench.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkbenchLayout(
    val version: Int = 1,
    val name: String = "NewLayout",
    val workbenchWidthDp: Float = 1200f,
    val workbenchHeightDp: Float = 800f,
    val placedWidgets: List<PlacedWidgetState> = emptyList(),
    val workpieces: List<Workpiece> = emptyList(),
    val ioLabelMap: Map<String, String> = emptyMap()
)
