package io.github.nobuddyorg.pixelbuddy.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/** One upgrade of the raw settings JSON from version `n` to `n + 1`. */
fun interface SchemaStep {
    fun upgrade(settings: JsonObject): JsonObject
}

/** Version history of the settings file. */
object SettingsSchema {
    const val CURRENT_VERSION: Int = 1

    /** Upgrade steps keyed by the version they upgrade from; version 1 is the first, so there are none yet. */
    val steps: Map<Int, SchemaStep> = emptyMap()

    /**
     * Brings [settings] to [currentVersion]. Files from a newer app version (e.g. after restoring a backup) are
     * read as they are: unknown properties are ignored and missing ones get their defaults.
     */
    fun upgrade(
        settings: JsonObject,
        steps: Map<Int, SchemaStep> = this.steps,
        currentVersion: Int = CURRENT_VERSION,
    ): JsonObject {
        var json = settings
        var version = json["version"]?.jsonPrimitive?.intOrNull ?: 1
        while (version < currentVersion) {
            val step = steps[version] ?: throw SerializationException("no upgrade from settings version $version")
            json = JsonObject(step.upgrade(json) + ("version" to JsonPrimitive(version + 1)))
            version = json.getValue("version").jsonPrimitive.int
        }
        return json
    }
}

/** Reads and writes [Settings] as JSON. Unreadable files raise [CorruptionException], see [SettingsRepository]. */
class SettingsSerializer(
    private val steps: Map<Int, SchemaStep> = SettingsSchema.steps,
    private val currentVersion: Int = SettingsSchema.CURRENT_VERSION,
) : Serializer<Settings> {
    override val defaultValue: Settings = Settings(version = currentVersion)

    override suspend fun readFrom(input: InputStream): Settings =
        try {
            val raw = json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
            val upgraded = SettingsSchema.upgrade(raw, steps, currentVersion)
            json.decodeFromJsonElement(Settings.serializer(), upgraded).copy(version = currentVersion)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException; so is a JSON value that isn't an object.
            throw CorruptionException("Unreadable settings", e)
        }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(Settings.serializer(), t.copy(version = currentVersion)).encodeToByteArray())
    }

    companion object {
        internal val json =
            Json {
                // Forward compatible: properties and enum values from newer versions don't make the file unreadable.
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            }
    }
}
