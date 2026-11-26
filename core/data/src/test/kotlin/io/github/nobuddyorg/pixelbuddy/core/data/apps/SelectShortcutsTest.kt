package io.github.nobuddyorg.pixelbuddy.core.data.apps

import org.junit.Test
import kotlin.test.assertEquals

class SelectShortcutsTest {
    private fun static(
        id: String,
        rank: Int,
    ) = ShortcutCandidate(id, "Static $id", fromManifest = true, rank = rank, enabled = true)

    private fun dynamic(
        id: String,
        rank: Int,
    ) = ShortcutCandidate(id, "Dynamic $id", fromManifest = false, rank = rank, enabled = true)

    private fun ids(candidates: List<ShortcutCandidate>) = selectShortcuts(candidates).map { it.id }

    @Test
    fun `static shortcuts come first, each kind by rank`() {
        assertEquals(listOf("s0", "s1", "d0"), ids(listOf(dynamic("d0", 0), static("s1", 1), static("s0", 0))))
    }

    @Test
    fun `with many of both, two of each`() {
        val all = (0..3).map { static("s$it", it) } + (0..3).map { dynamic("d$it", it) }

        assertEquals(listOf("s0", "s1", "d0", "d1"), ids(all))
    }

    @Test
    fun `slots one kind doesn't use go to the other`() {
        assertEquals(
            listOf("s0", "d0", "d1", "d2"),
            ids(listOf(static("s0", 0)) + (0..4).map { dynamic("d$it", it) }),
        )
        assertEquals(
            listOf("s0", "s1", "s2", "d0"),
            ids((0..4).map { static("s$it", it) } + dynamic("d0", 0)),
        )
    }

    @Test
    fun `disabled, unlabelled and repeated shortcuts are left out`() {
        val candidates =
            listOf(
                static("s0", 0).copy(enabled = false),
                static("s1", 1).copy(label = " "),
                dynamic("d0", 0),
                dynamic("d0", 1),
            )

        assertEquals(listOf("d0"), ids(candidates))
    }
}
