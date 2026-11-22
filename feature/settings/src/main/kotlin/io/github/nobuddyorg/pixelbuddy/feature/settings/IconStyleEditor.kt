package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CustomIconStyle
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsSerializer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The editor's [draft] and the [saved] icon it started from; nothing applies before [IconStyleEditor.apply]. */
data class IconEditorState(
    val saved: IconChoice,
    val draft: IconChoice = saved,
) {
    /** The draft differs from what is stored. */
    val dirty: Boolean get() = draft != saved
}

/**
 * State holder of the icon style editor, for all icons (#13) or one app's (#14). Changes go to a draft; [apply]
 * hands it to [store] and makes it the saved one, [reset] goes back to the saved one. Kept across configuration
 * changes with [Saver].
 */
class IconStyleEditor(
    initial: IconEditorState,
    private val store: (IconChoice) -> Unit,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<IconEditorState> = _state

    /** Starts over from [preset]. */
    fun choose(preset: IconPreset) = update { it.copy(style = IconStyleSetting.of(preset)) }

    /** Changes one parameter; a preset becomes "Custom (based on …)". */
    fun edit(change: (CustomIconStyle) -> CustomIconStyle) = update { it.copy(style = it.style.edited(change)) }

    /** Draws the app's icon from [layer] (instead of a picture). */
    fun useLayer(layer: IconLayer) = update { it.copy(layer = layer, image = null) }

    /** Draws the imported picture [image] instead of the app's icon. */
    fun useImage(image: String) = update { it.copy(image = image) }

    private fun update(change: (IconChoice) -> IconChoice) = _state.update { it.copy(draft = change(it.draft)) }

    fun reset() = _state.update { it.copy(draft = it.saved) }

    fun apply() {
        val draft = _state.value.draft
        store(draft)
        _state.update { it.copy(saved = draft) }
    }

    companion object {
        /** Saves both as JSON. */
        fun saver(store: (IconChoice) -> Unit): Saver<IconStyleEditor, Any> =
            listSaver(
                save = { editor ->
                    val state = editor.state.value
                    listOf(SettingsSerializer.encode(state.saved), SettingsSerializer.encode(state.draft))
                },
                restore = { saved ->
                    val styles = saved.map { SettingsSerializer.decodeIconChoice(it) ?: IconChoice() }
                    IconStyleEditor(IconEditorState(styles[0], styles[1]), store)
                },
            )
    }
}
