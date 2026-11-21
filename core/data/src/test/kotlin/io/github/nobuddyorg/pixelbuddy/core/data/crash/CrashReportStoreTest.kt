package io.github.nobuddyorg.pixelbuddy.core.data.crash

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "crash-reports")
    private val store by lazy { CrashReportStore(directory) }

    private fun report(summary: String) = "PixelBuddy crash report\nTime: x\n\n$summary\n\tat a.b.C.d(C.kt:1)\n"

    @Test
    fun `saved reports are listed newest first with their summary`() {
        store.save(Instant.ofEpochMilli(1_000), report("java.lang.Exception: first"))
        store.save(Instant.ofEpochMilli(2_000), report("java.lang.Exception: second"))

        val reports = store.list()

        assertEquals(listOf("java.lang.Exception: second", "java.lang.Exception: first"), reports.map { it.summary })
        assertEquals(Instant.ofEpochMilli(2_000), reports.first().time)
    }

    @Test
    fun `only the newest ten reports are kept`() {
        repeat(12) { store.save(Instant.ofEpochMilli(it * 1_000L), report("java.lang.Exception: $it")) }

        val reports = store.list()

        assertEquals(10, reports.size)
        assertEquals("java.lang.Exception: 11", reports.first().summary)
        assertEquals("java.lang.Exception: 2", reports.last().summary)
        assertEquals(10, directory.listFiles()!!.size)
    }

    @Test
    fun `reports at the same millisecond don't overwrite each other`() {
        val time = Instant.ofEpochMilli(5_000)
        val first = store.save(time, report("one"))
        val second = store.save(time, report("two"))

        assertTrue(first != second)
        assertEquals(2, store.list().size)
    }

    @Test
    fun `read returns the full text`() {
        val text = report("java.lang.Exception: boom")
        val id = store.save(Instant.ofEpochMilli(1), text)

        assertEquals(text, store.read(id))
    }

    @Test
    fun `delete removes one report and deleteAll removes the rest`() {
        val keep = store.save(Instant.ofEpochMilli(1), report("keep"))
        val drop = store.save(Instant.ofEpochMilli(2), report("drop"))

        store.delete(drop)
        assertEquals(listOf(keep), store.list().map { it.id })

        store.deleteAll()
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `ids that the store did not create are rejected`() {
        File(folder.root, "secret.txt").writeText("secret")

        assertNull(store.read("../secret.txt"))
        store.delete("../secret.txt")
        assertTrue(File(folder.root, "secret.txt").exists())
    }

    @Test
    fun `temporary and foreign files are ignored`() {
        directory.mkdirs()
        File(directory, "crash-1.txt.tmp").writeText("partial")
        File(directory, "notes.txt").writeText("other")

        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `empty directory lists nothing`() {
        assertEquals(emptyList(), store.list())
        assertNull(store.read("crash-1.txt"))
    }
}
