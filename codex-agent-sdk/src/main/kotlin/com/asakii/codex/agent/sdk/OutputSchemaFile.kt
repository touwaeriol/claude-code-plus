package com.asakii.codex.agent.sdk

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal data class OutputSchemaHandle(
    val schemaPath: Path?,
    val cleanup: suspend () -> Unit,
)

internal suspend fun createOutputSchemaFile(schema: JsonObject?): OutputSchemaHandle {
    if (schema == null) {
        return OutputSchemaHandle(schemaPath = null) {}
    }

    val dir = Files.createTempDirectory("codex-output-schema")
    val schemaPath = dir.resolve("schema.json")
    val json = Json.encodeToString(JsonObject.serializer(), schema)

    try {
        Files.writeString(schemaPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    } catch (error: IOException) {
        Files.deleteIfExists(schemaPath)
        Files.deleteIfExists(dir)
        throw error
    }

    return OutputSchemaHandle(
        schemaPath = schemaPath,
        cleanup = {
            runCatching { Files.deleteIfExists(schemaPath) }
            runCatching { Files.deleteIfExists(dir) }
        },
    )
}

