package com.example.mpsbuilder.data.ladder

import kotlinx.serialization.Serializable

@Serializable
data class LadderProject(
    val name: String = "NewProject",
    val rungs: List<LadderRung> = listOf(LadderRung.empty()),
    val ioLabels: Map<String, String> = emptyMap()
)
