package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import kotlin.test.fail

/**
 * Checks what TalkBack and touch need from everything on screen that can be tapped, dragged or set: a name to read
 * out, and at least 48 × 48 dp to touch (Android's minimum). The Accessibility Test Framework does nothing in
 * local Robolectric tests, so these checks stand in for it here; it runs in the emulator tests.
 */
internal fun SemanticsNodeInteractionsProvider.assertAccessible(minTouchPx: Float) {
    val problems = mutableListOf<String>()
    onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root -> collect(root, minTouchPx, problems) }
    if (problems.isNotEmpty()) fail("Accessibility problems:\n" + problems.joinToString("\n"))
}

private fun collect(
    node: SemanticsNode,
    minTouchPx: Float,
    problems: MutableList<String>,
) {
    val config = node.config
    val interactive =
        SemanticsActions.OnClick in config ||
            SemanticsActions.OnLongClick in config ||
            SemanticsActions.SetProgress in config
    if (interactive) {
        val label =
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
                config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() +
                config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val name = label.ifBlank { config.getOrNull(SemanticsProperties.PaneTitle).orEmpty() }
        if (name.isBlank()) problems += "Node #${node.id} can be used but has no name for TalkBack"
        val size = node.size
        if (size.width < minTouchPx - 1 || size.height < minTouchPx - 1) {
            problems += "\"$name\" is ${size.width} × ${size.height} px, less than the 48 dp touch target"
        }
    }
    node.children.forEach { collect(it, minTouchPx, problems) }
}
