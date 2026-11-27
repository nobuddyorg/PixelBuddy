package io.github.nobuddyorg.pixelbuddy.core.data.settings

import androidx.datastore.core.CorruptionException
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * All settings in one `.zip` (#21), with the pictures the apps' own icons use, to move them to another phone:
 * `pixelbuddy-settings.json` (the settings file as stored) and `icon-images/<sha-256>.png`.
 *
 * Reading trusts nothing: entry count and sizes are limited, entry names with path tricks refuse the whole archive,
 * settings from a newer version are refused, and pictures are decoded again with [CustomIconStore]'s limits. Entry
 * names are never used as file paths.
 */
object SettingsArchive {
    const val SETTINGS_ENTRY: String = "pixelbuddy-settings.json"
    private const val IMAGES_DIR = "icon-images/"

    const val MAX_SETTINGS_BYTES: Int = 1024 * 1024
    const val MAX_IMAGE_BYTES: Int = 2 * 1024 * 1024
    const val MAX_ENTRIES: Int = 512
    const val MAX_TOTAL_BYTES: Long = 64L * 1024 * 1024

    /** A read archive, not applied yet. [images] are the raw PNGs by their name in the archive. */
    class Archive(
        val settings: Settings,
        val images: Map<String, ByteArray>,
    )

    /** Why an archive was refused. */
    enum class Problem {
        /** Not a `.zip` at all. */
        NotAnArchive,

        /** A `.zip`, but without PixelBuddy's settings. */
        NoSettings,

        /** Made by a newer PixelBuddy; it may hold settings this version would lose. */
        NewerVersion,

        /** Too many entries or an entry too large. */
        TooLarge,

        /** An entry name that tries to leave the archive (`../`, absolute paths). */
        UnsafeEntry,

        /** The settings in it can't be read. */
        Unreadable,
    }

    sealed interface ReadResult {
        data class Read(
            val archive: Archive,
        ) : ReadResult

        data class Refused(
            val problem: Problem,
        ) : ReadResult
    }

    /** Writes [settings] and the pictures they use from [images] to [output] (left open). Blocking. */
    fun write(
        settings: Settings,
        images: CustomIconStore,
        output: OutputStream,
    ) {
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
        zip.write(
            SettingsSerializer.json
                .encodeToString(Settings.serializer(), settings.copy(version = SettingsSchema.CURRENT_VERSION))
                .encodeToByteArray(),
        )
        zip.closeEntry()
        for (name in settings.iconImages.sorted()) {
            val bytes = images.bytes(name) ?: continue
            zip.putNextEntry(ZipEntry("$IMAGES_DIR$name.png"))
            zip.write(bytes)
            zip.closeEntry()
        }
        zip.finish()
    }

    /** Reads an archive from [input] (left open) without changing anything. Blocking. */
    suspend fun read(input: InputStream): ReadResult {
        var settingsBytes: ByteArray? = null
        val images = HashMap<String, ByteArray>()
        var entries = 0
        var total = 0L
        try {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!isSafe(name)) return refused(Problem.UnsafeEntry)
                if (++entries > MAX_ENTRIES) return refused(Problem.TooLarge)
                if (entry.isDirectory) continue
                val limit =
                    when {
                        name == SETTINGS_ENTRY -> MAX_SETTINGS_BYTES

                        name.startsWith(IMAGES_DIR) -> MAX_IMAGE_BYTES

                        // Something a newer version added, or junk: skipped, never extracted.
                        else -> continue
                    }
                val bytes = zip.readAtMost(limit) ?: return refused(Problem.TooLarge)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) return refused(Problem.TooLarge)
                if (name == SETTINGS_ENTRY) {
                    settingsBytes = bytes
                } else {
                    val image = name.removePrefix(IMAGES_DIR).removeSuffix(".png")
                    if (CustomIconStore.isValidName(image)) images[image] = bytes
                }
            }
        } catch (_: IOException) {
            // ZipException for a broken archive, EOFException for a cut-off one.
            return refused(Problem.NotAnArchive)
        }
        if (entries == 0) return refused(Problem.NotAnArchive)
        val raw = settingsBytes ?: return refused(Problem.NoSettings)
        val version =
            try {
                SettingsSerializer.json
                    .parseToJsonElement(raw.decodeToString())
                    .jsonObject["version"]
                    ?.jsonPrimitive
                    ?.intOrNull ?: 1
            } catch (_: IllegalArgumentException) {
                return refused(Problem.Unreadable)
            }
        if (version > SettingsSchema.CURRENT_VERSION) return refused(Problem.NewerVersion)
        val settings =
            try {
                SettingsSerializer().readFrom(ByteArrayInputStream(raw))
            } catch (_: CorruptionException) {
                return refused(Problem.Unreadable)
            }
        return ReadResult.Read(Archive(settings, images.filterKeys { it in settings.iconImages }))
    }

    /**
     * The settings to store for [archive], replacing [current]: the archive's pictures go into [images] (decoded
     * again, so they get this phone's names), apps it refers to wait for their install, and the setup stays as it
     * is. Overrides whose picture is missing or broken use the app's icon. Blocking.
     */
    fun restore(
        archive: Archive,
        current: Settings,
        images: CustomIconStore,
    ): Settings {
        val renamed =
            archive.images
                .mapNotNull { (name, bytes) ->
                    val result = images.import(ByteArrayInputStream(bytes))
                    (result as? CustomIconStore.ImportResult.Imported)?.let { name to it.name }
                }.toMap()
        val imported = archive.settings
        return imported.copy(
            iconOverrides = imported.iconOverrides.map { it.copy(image = it.image?.let(renamed::get)) },
            setup = current.setup,
            waitingApps = imported.appKeys,
            // A widget only exists on the phone it was added on: this phone keeps its own, in the imported place.
            widget = current.widget.copy(below = imported.widget.below),
        )
    }

    private fun refused(problem: Problem) = ReadResult.Refused(problem)

    /** Plain relative names only: no parent references, absolute paths, drive letters or backslashes. */
    internal fun isSafe(name: String): Boolean =
        name.isNotEmpty() &&
            !name.startsWith("/") &&
            '\\' !in name &&
            ':' !in name &&
            '\u0000' !in name &&
            name.split('/').none { it == ".." || it == "." }

    /** The rest of the current entry, or null if it is longer than [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }
}
