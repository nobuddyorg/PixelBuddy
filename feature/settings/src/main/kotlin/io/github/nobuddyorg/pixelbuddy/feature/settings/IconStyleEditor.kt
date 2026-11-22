package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CustomIconStyle
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsSerializer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The editor's [draft] and the [saved] style it started from; nothing applies before [IconStyleEditor.apply]. */
data class IconEditorState(
    val saved: IconStyleSetting,
    val draft: IconStyleSetting = saved,
) {
    /** The draft differs from what is stored. */
    val dirty: Boolean get() = draft != saved
}

/**
 * State holder of the icon style editor (#13). Changes go to a draft; [apply] hands it to [store] and makes it the
 * saved style, [reset] goes back to the saved one. Kept across configuration changes with [Saver].
 */
class IconStyleEditor(
    initial: IconEditorState,
    private val store: (IconStyleSetting) -> Unit,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<IconEditorState> = _state

    /** Starts over from [preset]. */
    fun choose(preset: IconPreset) = _state.update { it.copy(draft = IconStyleSetting.of(preset)) }

    /** Changes one parameter; a preset becomes "Custom (based on …)". */
    fun edit(change: (CustomIconStyle) -> CustomIconStyle) = _state.update { it.copy(draft = it.draft.edited(change)) }

    fun reset() = _state.update { it.copy(draft = it.saved) }

    fun apply() {
        val draft = _state.value.draft
        store(draft)
        _state.update { it.copy(saved = draft) }
    }

    companion object {
        /** Saves both styles as JSON. */
        fun saver(store: (IconStyleSetting) -> Unit): Saver<IconStyleEditor, Any> =
            listSaver(
                save = { editor ->
                    val state = editor.state.value
                    listOf(SettingsSerializer.encode(state.saved), SettingsSerializer.encode(state.draft))
                },
                restore = { saved ->
                    val styles = saved.map { SettingsSerializer.decodeIconStyle(it) ?: IconStyleSetting() }
                    IconStyleEditor(IconEditorState(styles[0], styles[1]), store)
                },
            )
    }
}
