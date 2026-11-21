package io.github.nobuddyorg.pixelbuddy.core.data.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AppSorterTest {
    private val german = AppSorter(Locale.GERMANY)

    private fun AppSorter.labels(vararg labels: String) = sort(labels.map { app(it) }).map { it.app.label }

    @Test
    fun `umlauts sort like their base letter in German`() {
        // DIN 5007: Ü sorts as U, so "Übersetzer" (U-b…) comes before "Uhr" (U-h…).
        assertEquals(
            listOf("Apfel", "Ärzte", "Bahn", "Öffi", "Ost", "Übersetzer", "Uhr", "Zug"),
            german.labels("Zug", "Übersetzer", "Öffi", "Bahn", "Ärzte", "Uhr", "Ost", "Apfel"),
        )
    }

    @Test
    fun `sorting ignores case`() {
        assertEquals(listOf("alpha", "Beta", "gamma"), german.labels("gamma", "Beta", "alpha"))
    }

    @Test
    fun `sections fold diacritics`() {
        assertEquals("A", german.sectionOf("Ärzte"))
        assertEquals("O", german.sectionOf("Öffi"))
        assertEquals("U", german.sectionOf("übersetzer"))
        assertEquals("E", german.sectionOf("Éclair"))
        assertEquals("S", german.sectionOf("ß-Rechner"))
    }

    @Test
    fun `digits, symbols, emoji and other scripts go to the other section`() {
        listOf("1Password", "#hashtag", "😀 Smile", "Яндекс", "微信", "", "   ").forEach {
            assertEquals(AppSorter.OTHER_SECTION, german.sectionOf(it), "label '$it'")
        }
    }

    @Test
    fun `leading spaces are ignored for the section`() {
        assertEquals("M", german.sectionOf("  Maps"))
    }

    @Test
    fun `other section comes after Z`() {
        assertEquals(
            listOf("Amazon", "Zoom", "1Password", "😀 Smile"),
            german.labels("😀 Smile", "Zoom", "1Password", "Amazon"),
        )
    }

    @Test
    fun `apps with the same label keep a stable order`() {
        val work = app("Mail", packageName = "a.mail", userSerial = 10)
        val main = app("Mail", packageName = "a.mail", userSerial = 0)
        val other = app("Mail", packageName = "b.mail")

        val sorted = german.sort(listOf(other, work, main)).map { it.app }

        assertEquals(listOf(main, work, other), sorted)
    }

    @Test
    fun `key id combines package, activity and user`() {
        assertEquals("a.b/a.b.Main/10", AppKey("a.b", "a.b.Main", 10).id)
    }

    @Test
    fun `entries carry their section`() {
        val entries = german.sort(listOf(app("Öffi"), app("7-Zip")))

        assertEquals(listOf("O", "#"), entries.map { it.section })
    }
}
