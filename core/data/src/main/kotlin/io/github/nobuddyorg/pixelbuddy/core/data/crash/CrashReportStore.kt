package io.github.nobuddyorg.pixelbuddy.core.data.crash

import java.io.File
import java.time.Instant

/** A stored crash report. [id] is the file name. */
data class CrashReport(
    val id: String,
    val time: Instant,
    /** First line of the stack trace, e.g. `java.lang.IllegalStateException: boom`. */
    val summary: String,
)

/**
 * Keeps the newest [maxReports] crash reports as text files in [directory] (app-private storage).
 * Nothing here touches the network.
 */
class CrashReportStore(
    private val directory: File,
    private val maxReports: Int = DEFAULT_MAX_REPORTS,
) {
    /** Writes a report and removes the oldest ones beyond [maxReports]. Returns the new report's id. */
    @Synchronized
    fun save(
        time: Instant,
        text: String,
    ): String {
        directory.mkdirs()
        var millis = time.toEpochMilli()
        while (File(directory, fileName(millis)).exists()) millis++
        val name = fileName(millis)
        val temp = File(directory, "$name.tmp")
        temp.writeText(text)
        check(temp.renameTo(File(directory, name))) { "Could not store crash report" }
        reportFiles().drop(maxReports).forEach { it.delete() }
        return name
    }

    /** All reports, newest first. */
    @Synchronized
    fun list(): List<CrashReport> =
        reportFiles().map { file ->
            CrashReport(
                id = file.name,
                time = Instant.ofEpochMilli(millisOf(file.name)!!),
                summary = summaryOf(file),
            )
        }

    @Synchronized
    fun read(id: String): String? = fileFor(id)?.takeIf { it.isFile }?.readText()

    @Synchronized
    fun delete(id: String) {
        fileFor(id)?.delete()
    }

    @Synchronized
    fun deleteAll() {
        reportFiles().forEach { it.delete() }
    }

    /** Report files sorted newest first; ignores temporary and foreign files. */
    private fun reportFiles(): List<File> =
        directory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && millisOf(it.name) != null }
            .sortedByDescending { millisOf(it.name) }

    /** Only accepts names this store created, so an id can't point outside [directory]. */
    private fun fileFor(id: String): File? = if (millisOf(id) != null) File(directory, id) else null

    private fun summaryOf(file: File): String =
        file.useLines { lines ->
            lines.dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }.orEmpty()
        }

    companion object {
        const val DEFAULT_MAX_REPORTS = 10
        private const val PREFIX = "crash-"
        private const val SUFFIX = ".txt"
        private val NAME = Regex("""crash-(\d{1,19})\.txt""")

        private fun fileName(millis: Long) = "$PREFIX$millis$SUFFIX"

        private fun millisOf(name: String): Long? =
            NAME
                .matchEntire(name)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
    }
}
