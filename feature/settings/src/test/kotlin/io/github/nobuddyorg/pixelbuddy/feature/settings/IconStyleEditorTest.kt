package io.github.nobuddyorg.pixelbuddy.feature.settings

import app.cash.turbine.test
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.PaletteChoice
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconStyleEditorTest {
    private val stored = mutableListOf<IconChoice>()

    private fun editor(saved: IconStyleSetting = IconStyleSetting()) =
        IconStyleEditor(IconEditorState(IconChoice(saved))) { stored += it }

    @Test
    fun `changes stay in the draft until applied`() =
        runTest {
            val editor = editor()

            editor.state.test {
                assertFalse(awaitItem().dirty)

                editor.edit { it.copy(grid = 12) }
                val edited = awaitItem()
                assertTrue(edited.dirty)
                assertEquals(IconPreset.SixteenBit.style.copy(grid = 12), edited.draft.style.style)
                assertEquals(emptyList(), stored)

                editor.apply()
                val applied = awaitItem()
                assertFalse(applied.dirty)
                assertEquals(listOf(edited.draft), stored)
                assertEquals(edited.draft, applied.saved)
            }
        }

    @Test
    fun `reset goes back to the saved style`() =
        runTest {
            val saved = IconStyleSetting.of(IconPreset.Pocket)
            val editor = editor(saved)

            editor.state.test {
                skipItems(1)
                editor.choose(IconPreset.Neon)
                assertEquals(IconStyleSetting.of(IconPreset.Neon), awaitItem().draft.style)
                editor.edit { it.copy(palette = PaletteChoice.Mono, colors = 3) }
                assertEquals("Neon", awaitItem().draft.style.preset)

                editor.reset()
                val reset = awaitItem()
                assertEquals(IconChoice(saved), reset.draft)
                assertFalse(reset.dirty)
            }
            assertEquals(emptyList(), stored)
        }

    @Test
    fun `choosing the saved preset again is no change`() {
        val editor = editor(IconStyleSetting.of(IconPreset.Mono))

        editor.choose(IconPreset.Neon)
        editor.choose(IconPreset.Mono)

        assertFalse(editor.state.value.dirty)
    }

    @Test
    fun `an app's icon can come from a layer or a picture`() {
        val editor = editor()

        editor.useImage("a".repeat(64))
        assertEquals(IconChoice(image = "a".repeat(64)), editor.state.value.draft)
        editor.useLayer(IconLayer.Monochrome)
        assertEquals(IconChoice(layer = IconLayer.Monochrome), editor.state.value.draft)
        editor.apply()

        assertEquals(listOf(IconChoice(layer = IconLayer.Monochrome)), stored)
    }

    @Test
    fun `the draft survives a configuration change, a discarded one is gone`() {
        val editor = editor()
        editor.edit { it.copy(outline = false, dithering = true) }

        val saved =
            with(
                IconStyleEditor.saver {
                    stored += it
                },
            ) {
                androidx.compose.runtime.saveable
                    .SaverScope { true }
                    .save(editor)
            }!!
        val restored = IconStyleEditor.saver { stored += it }.restore(saved)!!

        assertEquals(editor.state.value, restored.state.value)
        // Leaving without applying (discard) stores nothing.
        assertEquals(emptyList(), stored)
    }
}
