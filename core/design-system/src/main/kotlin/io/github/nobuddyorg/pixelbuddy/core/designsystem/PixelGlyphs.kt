package io.github.nobuddyorg.pixelbuddy.core.designsystem

/**
 * Hand-made 5 × 7 pixel glyphs for the big clock. Drawn as squares, so they stay crisp at any size,
 * independent of font rendering.
 */
object PixelGlyphs {
    const val HEIGHT = 7

    private val table: Map<Char, List<String>> =
        mapOf(
            '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
            '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
            ':' to listOf(".", ".", "#", ".", "#", ".", "."),
            ' ' to listOf("..", "..", "..", "..", "..", "..", ".."),
        )

    val supported: Set<Char> get() = table.keys

    /** Glyph rows; each row is a string of `#` (on) and `.` (off). */
    fun glyph(char: Char): List<String> = table[char] ?: error("No pixel glyph for '$char'")

    /**
     * Lays out [text] as on-pixels `(column, row)` with one empty column between glyphs.
     * Returns the pixels and the total width in glyph pixels.
     */
    fun layout(text: String): Layout {
        val pixels = mutableListOf<Pair<Int, Int>>()
        var x = 0
        text.forEachIndexed { index, char ->
            val rows = glyph(char)
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { dx, cell -> if (cell == '#') pixels += (x + dx) to y }
            }
            x += rows.first().length
            if (index < text.lastIndex) x += 1
        }
        return Layout(pixels, width = x, height = HEIGHT)
    }

    data class Layout(
        val pixels: List<Pair<Int, Int>>,
        val width: Int,
        val height: Int,
    )
}
