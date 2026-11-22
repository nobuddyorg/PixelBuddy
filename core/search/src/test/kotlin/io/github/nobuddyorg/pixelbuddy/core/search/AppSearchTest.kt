package io.github.nobuddyorg.pixelbuddy.core.search

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppSearchTest {
    private val apps =
        listOf(
            "1Password",
            "Ärzte",
            "Calendar",
            "Firefox",
            "Mail",
            "Maps",
            "Media Archive",
            "Proton Mail",
            "Spotify",
            "WhatsApp",
            "Straße & Co",
        )

    private fun search(query: String) = AppSearch.search(apps, query) { it }.map { it.item }

    @ParameterizedTest(name = "{0} ~ {1} = {2}")
    @CsvSource(
        "Maps, maps, Exact",
        "Proton Mail, protonmail, Exact",
        "Proton Mail, PROTON-MAIL, Exact",
        "Maps, ma, Prefix",
        "Proton Mail, proton m, Words",
        "Proton Mail, protonm, Prefix",
        "Ärzte, arz, Prefix",
        "Ärzte, ÄRZ, Prefix",
        "Straße & Co, strass, Prefix",
        "Proton Mail, mail, WordStart",
        "WhatsApp, app, WordStart",
        "1Password, pass, WordStart",
        "Proton Mail, pro ma, Words",
        "Media Archive, m a, Words",
        "Proton Mail, pm, Initials",
        "Media Archive, ma, Initials",
        "1Password, 1p, Prefix",
        "WhatsApp, wa, Initials",
        "Firefox, ref, Contains",
        "Spotify, tif, Contains",
        "Firefox, re, Contains",
    )
    fun `labels match by kind`(
        label: String,
        query: String,
        kind: MatchKind,
    ) {
        assertEquals(kind, AppSearch.match(label, query)?.kind)
    }

    @ParameterizedTest(name = "{0} !~ {1}")
    @CsvSource(
        "Maps, x",
        "Maps, mapss",
        "Firefox, e",
        "Proton Mail, mail proton",
        "Proton Mail, pmx",
        "Proton Mail, p m m",
        "Maps, m a",
        "Proton Mail, mail p",
        "WhatsApp, wap",
        "Calendar, ' '",
        "Calendar, '.-'",
        "'...', a",
    )
    fun `non-matches`(
        label: String,
        query: String,
    ) {
        assertNull(AppSearch.match(label, query))
    }

    @Test
    fun `results are ranked by match kind, then position, then list order`() {
        assertEquals(listOf("Mail", "Maps", "Proton Mail", "Media Archive"), search("ma"))
        assertEquals(listOf("Mail", "Proton Mail"), search("mai"))
        assertEquals(listOf("Proton Mail", "Spotify"), search("pm").take(1) + search("ti").take(1))
        assertEquals(listOf("Media Archive"), search("m a"))
        assertEquals(listOf("Proton Mail"), search("proton mail"))
    }

    @Test
    fun `among equal matches the earlier position wins over list order`() {
        val results = AppSearch.search(listOf("Grab", "Cabin"), "ab") { it }

        assertEquals(listOf("Cabin", "Grab"), results.map { it.item })
        assertEquals(listOf(MatchKind.Contains, MatchKind.Contains), results.map { it.match.kind })
    }

    @Test
    fun `a blank query matches nothing`() {
        assertEquals(emptyList(), search(""))
        assertEquals(emptyList(), search("  "))
    }

    @Test
    fun `matched characters are given in the original label`() {
        assertEquals(listOf(0..1), AppSearch.match("Maps", "ma")!!.ranges)
        assertEquals(listOf(7..10), AppSearch.match("Proton Mail", "mail")!!.ranges)
        assertEquals(listOf(0..0, 7..7), AppSearch.match("Proton Mail", "pm")!!.ranges)
        assertEquals(listOf(0..2, 7..8), AppSearch.match("Proton Mail", "pro ma")!!.ranges)
        // "ss" comes from the single "ß".
        assertEquals(listOf(0..4), AppSearch.match("Straße", "strass")!!.ranges)
        assertEquals(
            listOf(0..7),
            AppSearch
                .match("Straße & Co", "strasse")!!
                .ranges
                .take(1)
                .map { 0..it.last + 2 },
        )
        assertEquals(listOf(2..4), AppSearch.match("Firefox", "ref")!!.ranges)
        // Separators between matched characters are not highlighted.
        assertEquals(listOf(0..5, 7..7), AppSearch.match("Proton Mail", "protonm")!!.ranges)
    }

    @Test
    fun `folding removes accents and case and spells out ligatures`() {
        assertEquals("arzte", AppSearch.fold("Ärzte"))
        assertEquals("strasse", AppSearch.fold("Straße"))
        assertEquals("aeoeolldi", AppSearch.fold("ÆŒØŁłĐı"))
        assertEquals("ss", AppSearch.fold("ẞ"))
        assertEquals("cafe", AppSearch.fold("Café"))
    }

    @Test
    fun `labels without letters or digits match nothing`() {
        assertNull(AppSearch.match("✈️", "a"))
        assertEquals(MatchKind.Exact, AppSearch.match("✈️ Flights", "flights")?.kind)
    }
}
