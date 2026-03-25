package com.example.mpsbuilder.data.repository

import android.content.Context
import com.example.mpsbuilder.ui.workbench.model.WorkbenchLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface WorkbenchLayoutRepository {
    suspend fun save(layout: WorkbenchLayout)
    suspend fun saveAs(fileName: String, layout: WorkbenchLayout)
    suspend fun load(name: String): WorkbenchLayout?
    suspend fun listLayouts(): List<String>
    suspend fun delete(name: String)
}

class WorkbenchLayoutRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkbenchLayoutRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val EXT = "mps"  // 확장자

    private fun mpsDir() = context.filesDir.resolve("mpsbuilder").also { it.mkdirs() }

    override suspend fun save(layout: WorkbenchLayout) {
        mpsDir().resolve("${layout.name}.$EXT")
            .writeText(json.encodeToString(layout))
    }

    override suspend fun saveAs(fileName: String, layout: WorkbenchLayout) {
        mpsDir().resolve("$fileName.$EXT")
            .writeText(json.encodeToString(layout))
    }

    override suspend fun load(name: String): WorkbenchLayout? {
        // .mps 먼저, 없으면 .json으로 폴백
        val mpsFile = mpsDir().resolve("$name.$EXT")
        if (mpsFile.exists()) {
            return json.decodeFromString(mpsFile.readText())
        }
        val jsonFile = mpsDir().resolve("$name.json")
        if (jsonFile.exists()) {
            return json.decodeFromString(jsonFile.readText())
        }
        return null
    }

    override suspend fun listLayouts(): List<String> =
        mpsDir().listFiles()
            ?.filter {
                (it.extension == EXT || it.extension == "json") &&
                        it.nameWithoutExtension != "__autosave__"
            }
            ?.map { it.nameWithoutExtension }
            ?.distinct()
            ?: emptyList()

    override suspend fun delete(name: String) {
        mpsDir().resolve("$name.$EXT").delete()
        mpsDir().resolve("$name.json").delete()  // 레거시 json도 정리
    }
}
