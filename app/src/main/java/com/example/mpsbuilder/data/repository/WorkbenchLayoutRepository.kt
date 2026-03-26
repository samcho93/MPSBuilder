package com.example.mpsbuilder.data.repository

import android.content.Context
import com.example.mpsbuilder.ui.workbench.model.WorkbenchLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface WorkbenchLayoutRepository {
    /** 저장 — withLadder=true면 .mpsx, false면 .mps */
    suspend fun save(layout: WorkbenchLayout, withLadder: Boolean = false)
    /** autosave 전용 — 파일명 지정 */
    suspend fun saveAs(fileName: String, layout: WorkbenchLayout)
    suspend fun load(name: String): WorkbenchLayout?
    /** 저장된 레이아웃 목록 (이름 + 확장자) */
    suspend fun listLayouts(): List<LayoutEntry>
    suspend fun delete(name: String)
}

/** 목록 항목 — 이름과 래더 포함 여부 */
data class LayoutEntry(
    val name: String,
    val hasLadder: Boolean  // .mpsx면 true
)

class WorkbenchLayoutRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkbenchLayoutRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun mpsDir() = context.filesDir.resolve("mpsbuilder").also { it.mkdirs() }

    override suspend fun save(layout: WorkbenchLayout, withLadder: Boolean) {
        val ext = if (withLadder) "mpsx" else "mps"
        val saveData = if (!withLadder) {
            // .mps — 래더 데이터 제거
            layout.copy(ladderRungs = emptyList(), ladderIoLabels = emptyMap())
        } else {
            layout
        }
        // 이전 확장자 파일 삭제 (mps↔mpsx 전환 시)
        mpsDir().resolve("${layout.name}.mps").delete()
        mpsDir().resolve("${layout.name}.mpsx").delete()
        mpsDir().resolve("${layout.name}.$ext")
            .writeText(json.encodeToString(saveData))
    }

    override suspend fun saveAs(fileName: String, layout: WorkbenchLayout) {
        // autosave용 — 항상 .mps 확장자, 래더 포함
        mpsDir().resolve("$fileName.mps")
            .writeText(json.encodeToString(layout))
    }

    override suspend fun load(name: String): WorkbenchLayout? {
        // .mpsx 우선, 없으면 .mps, 없으면 .json 폴백
        listOf("mpsx", "mps", "json").forEach { ext ->
            val file = mpsDir().resolve("$name.$ext")
            if (file.exists()) {
                return try {
                    json.decodeFromString<WorkbenchLayout>(file.readText())
                } catch (_: Exception) { null }
            }
        }
        return null
    }

    override suspend fun listLayouts(): List<LayoutEntry> {
        val dir = mpsDir()
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter {
                it.extension in listOf("mps", "mpsx", "json") &&
                        it.nameWithoutExtension != "__autosave__"
            }
            .map { file ->
                LayoutEntry(
                    name = file.nameWithoutExtension,
                    hasLadder = file.extension == "mpsx"
                )
            }
            .distinctBy { it.name }
    }

    override suspend fun delete(name: String) {
        mpsDir().resolve("$name.mps").delete()
        mpsDir().resolve("$name.mpsx").delete()
        mpsDir().resolve("$name.json").delete()
    }
}
