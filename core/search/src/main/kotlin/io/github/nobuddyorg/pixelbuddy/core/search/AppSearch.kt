package io.github.nobuddyorg.pixelbuddy.core.search

import java.text.Normalizer
import java.util.Locale

/** How a label matched a query, best first. */
public enum class MatchKind {
    /** The whole label, ignoring case, accents, spaces and punctuation. */
    Exact,

    /** The start of the label ("ma" → Maps). */
    Prefix,

    /** The start of a later word ("mail" → Proton Mail, "app" → WhatsApp). */
    WordStart,

    /** Every query word starts a label word, in order ("pro ma" → Proton Mail); for queries with spaces. */
    Words,

    /** The first letters of the label's words ("pm" → Proton Mail). */
    Initials,

    /** Anywhere inside the label (at least two characters). */
    Contains,
}

/** A match of [kind]; [ranges] are the matched characters of the original label, for highlighting. */
public data class Match(
    val kind: MatchKind,
    val ranges: List<IntRange>,
)

/** One search result. */
public data class SearchResult<T>(
    val item: T,
    val match: Match,
)

/**
 * App search: case- and accent-insensitive (`é` = `e`, `ß` = `ss`) matching of labels by prefix, word starts and
 * initials, ranked by how well they match.
 */
public object AppSearch {
    /**
     * [items] whose [label] matches [query], best first. Equal matches keep the order of [items] (already sorted),
     * except that an earlier match position wins. A blank query matches nothing.
     */
    public fun <T> search(
        items: List<T>,
        query: String,
        label: (T) -> String,
    ): List<SearchResult<T>> {
        val prepared = Query.of(query) ?: return emptyList()
        return items
            .mapNotNull { item -> match(FoldedLabel.of(label(item)), prepared)?.let { SearchResult(item, it) } }
            .sortedWith(
                compareBy({ it.match.kind }, {
                    it.match.ranges
                        .first()
                        .first
                }),
            )
    }

    /** How [label] matches [query], or null. */
    public fun match(
        label: String,
        query: String,
    ): Match? = Query.of(query)?.let { match(FoldedLabel.of(label), it) }

    /** [text] lower-cased, without accents and with ligatures spelled out, as compared by the search. */
    public fun fold(text: String): String = text.map(::foldChar).joinToString("")

    private fun match(
        label: FoldedLabel,
        query: Query,
    ): Match? {
        val text = label.text
        val joined = query.joined
        if (text.isEmpty()) return null
        if (text == joined) return label.match(MatchKind.Exact, 0, joined.length)
        // Spaces in the query mean words: "m a" is Media Archive, not Maps.
        if (query.words.size > 1) return words(label, query)
        return when {
            text.startsWith(joined) -> label.match(MatchKind.Prefix, 0, joined.length)
            else -> wordStart(label, joined) ?: initials(label, joined) ?: contains(label, joined)
        }
    }

    private fun wordStart(
        label: FoldedLabel,
        joined: String,
    ): Match? {
        val start = label.wordStarts.drop(1).firstOrNull { label.text.startsWith(joined, it) } ?: return null
        return label.match(MatchKind.WordStart, start, joined.length)
    }

    private fun words(
        label: FoldedLabel,
        query: Query,
    ): Match? {
        val indices = mutableListOf<Int>()
        var next = 0
        for (word in query.words) {
            val start = label.wordStarts.firstOrNull { it >= next && label.text.startsWith(word, it) } ?: return null
            indices += label.original(start, word.length)
            next = label.wordStarts.firstOrNull { it > start } ?: label.text.length
        }
        return Match(MatchKind.Words, merge(indices))
    }

    private fun initials(
        label: FoldedLabel,
        joined: String,
    ): Match? {
        if (joined.length < 2 || joined.length > label.wordStarts.size) return null
        val initials = label.wordStarts.map { label.text[it] }.joinToString("")
        if (!initials.startsWith(joined)) return null
        val indices = label.wordStarts.take(joined.length).map { label.origin[it] }
        return Match(MatchKind.Initials, merge(indices))
    }

    private fun contains(
        label: FoldedLabel,
        joined: String,
    ): Match? {
        if (joined.length < 2) return null
        val start = label.text.indexOf(joined)
        return if (start < 0) null else label.match(MatchKind.Contains, start, joined.length)
    }
}

/** A prepared query: its words, folded, and all of them joined without separators. */
private class Query(
    val words: List<String>,
) {
    val joined: String = words.joinToString("")

    companion object {
        fun of(query: String): Query? {
            val words =
                AppSearch
                    .fold(query)
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.isNotEmpty() }
            return if (words.isEmpty()) null else Query(words)
        }
    }
}

/**
 * A label folded for comparison: only letters and digits ([text]), where each character came from in the
 * original ([origin]) and where words start ([wordStarts], indices into [text]).
 */
private class FoldedLabel(
    val text: String,
    val origin: IntArray,
    val wordStarts: List<Int>,
) {
    /** The original characters of `text[start, start + length)`. */
    fun original(
        start: Int,
        length: Int,
    ): List<Int> = (start until start + length).map { origin[it] }

    fun match(
        kind: MatchKind,
        start: Int,
        length: Int,
    ) = Match(kind, merge(original(start, length)))

    companion object {
        fun of(label: String): FoldedLabel {
            val text = StringBuilder()
            val origin = ArrayList<Int>()
            val wordStarts = ArrayList<Int>()
            var previous: Char? = null
            label.forEachIndexed { index, char ->
                if (char.isLetterOrDigit()) {
                    val folded = foldChar(char).filter(Char::isLetterOrDigit)
                    if (folded.isNotEmpty()) {
                        if (startsWord(previous, char)) wordStarts += text.length
                        folded.forEach {
                            text.append(it)
                            origin += index
                        }
                    }
                }
                previous = char
            }
            return FoldedLabel(text.toString(), origin.toIntArray(), wordStarts)
        }

        /** After a separator, at a lower-to-upper case change ("WhatsApp") and between letters and digits. */
        private fun startsWord(
            previous: Char?,
            char: Char,
        ): Boolean =
            previous == null ||
                !previous.isLetterOrDigit() ||
                (char.isUpperCase() && previous.isLowerCase()) ||
                (char.isDigit() != previous.isDigit())
    }
}

private val combiningMarks = Regex("\\p{Mn}+")

private fun foldChar(char: Char): String =
    when (char) {
        'ß', 'ẞ' -> {
            "ss"
        }

        'æ', 'Æ' -> {
            "ae"
        }

        'œ', 'Œ' -> {
            "oe"
        }

        'ø', 'Ø' -> {
            "o"
        }

        'ł', 'Ł' -> {
            "l"
        }

        'đ', 'Đ' -> {
            "d"
        }

        'ı' -> {
            "i"
        }

        else -> {
            Normalizer
                .normalize(
                    char.toString(),
                    Normalizer.Form.NFD,
                ).replace(combiningMarks, "")
                .lowercase(Locale.ROOT)
        }
    }

/** Sorted indices as ranges of consecutive indices. */
private fun merge(indices: List<Int>): List<IntRange> {
    val result = mutableListOf<IntRange>()
    for (index in indices.distinct().sorted()) {
        val last = result.lastOrNull()
        if (last != null &&
            last.last + 1 == index
        ) {
            result[result.lastIndex] = last.first..index
        } else {
            result += index..index
        }
    }
    return result
}
