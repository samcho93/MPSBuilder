package com.example.mpsbuilder.ui.workbench.model

import kotlinx.serialization.Serializable

@Serializable
data class ConveyorConfig(
    val size: ConveyorSize = ConveyorSize.MEDIUM,
    val direction: ConveyorDirection = ConveyorDirection.RIGHT,
    val beltColor: Long = 0xFF555555,
    val linkedMotorId: String? = null
)

@Serializable
enum class ConveyorSize(val lengthDp: Float, val widthDp: Float, val label: String) {
    SMALL(160f, 48f, "소"),
    MEDIUM(280f, 64f, "중"),
    LARGE(420f, 80f, "대")
}

@Serializable
enum class ConveyorDirection { LEFT, RIGHT, UP, DOWN }
