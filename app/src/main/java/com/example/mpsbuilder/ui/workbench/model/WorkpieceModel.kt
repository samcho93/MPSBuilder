package com.example.mpsbuilder.ui.workbench.model

import kotlinx.serialization.Serializable

@Serializable
data class Workpiece(
    val id: String,
    val type: WorkpieceType,
    val positionX: Float,
    val positionY: Float,
    val onConveyorId: String? = null,
    val conveyorProgress: Float = 0f
)

@Serializable
enum class WorkpieceType(val label: String, val color: Long, val shape: WorkpieceShape) {
    METAL("금속", 0xFFB0BEC5, WorkpieceShape.RECTANGLE),
    NON_METAL("비금속", 0xFFFFCC80, WorkpieceShape.RECTANGLE)
}

@Serializable
enum class WorkpieceShape { RECTANGLE, CYLINDER_TOP, CIRCLE }
