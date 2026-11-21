package io.github.nobuddyorg.pixelbuddy.core.data.apps

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Locale-aware sorting and alphabet sections. Diacritics are folded for sections (Ä → A), and everything that
 * doesn't start with a Latin letter (digits, symbols, emoji, other scripts) goes to [OTHER_SECTION].
 */
class AppSorter(
    private val locale: Locale,
) {
    private val collator: Collator = Collator.getInstance(locale).apply { strength = Collator.TERTIARY }

    fun sort(apps: Collection<LauncherApp>): List<AppListEntry> =
        apps
            .map { AppListEntry(it, sectionOf(it.label)) }
            .sortedWith(
                compareBy<AppListEntry> { it.section == OTHER_SECTION }
                    .thenComparator { a, b -> collator.compare(a.app.label, b.app.label) }
                    .thenBy { it.app.key.packageName }
                    .thenBy { it.app.key.activityName }
                    .thenBy { it.app.key.userSerial },
            )

    fun sectionOf(label: String): String {
        val first = label.trimStart().firstOrNull() ?: return OTHER_SECTION
        val folded =
            Normalizer
                .normalize(first.toString(), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .uppercase(locale)
                .firstOrNull() ?: return OTHER_SECTION
        return if (folded in 'A'..'Z') folded.toString() else OTHER_SECTION
    }

    companion object {
        const val OTHER_SECTION = "#"
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
    }
}
