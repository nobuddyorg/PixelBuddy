package io.github.nobuddyorg.pixelbuddy.core.designsystem

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelGlyphsTest {
    @Test
    fun `every digit and the colon have seven rows of equal width`() {
        ("0123456789: ").forEach { char ->
            val rows = PixelGlyphs.glyph(char)
            assertEquals(PixelGlyphs.HEIGHT, rows.size, "rows of '$char'")
            assertEquals(1, rows.map { it.length }.distinct().size, "row widths of '$char'")
            assertTrue(rows.all { row -> row.all { it == '#' || it == '.' } }, "cells of '$char'")
        }
    }

    @Test
    fun `digits are five pixels wide and all different`() {
        val digits = ('0'..'9').map { PixelGlyphs.glyph(it) }
        assertTrue(digits.all { it.first().length == 5 })
        assertEquals(10, digits.distinct().size)
    }

    @Test
    fun `layout puts one empty column between glyphs`() {
        val layout = PixelGlyphs.layout("12:45")

        // 5 + 1 + 5 + 1 + 1 + 1 + 5 + 1 + 5
        assertEquals(25, layout.width)
        assertEquals(7, layout.height)
        assertTrue(layout.pixels.all { (x, y) -> x in 0 until 25 && y in 0 until 7 })
        // The colon's dots sit in column 12, rows 2 and 4.
        assertTrue((12 to 2) in layout.pixels && (12 to 4) in layout.pixels)
    }

    @Test
    fun `unknown characters are rejected`() {
        assertFailsWith<IllegalStateException> { PixelGlyphs.glyph('x') }
    }
}
