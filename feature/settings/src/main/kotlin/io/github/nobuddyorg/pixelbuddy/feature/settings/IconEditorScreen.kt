package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CustomIconStyle
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconLook
import io.github.nobuddyorg.pixelbuddy.core.data.settings.PaletteChoice
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Editing one app's icon (#14) instead of all icons. */
internal class AppIconContext(
    val app: LauncherApp,
    /** The style of all other icons, to compare with. */
    val global: IconStyle,
    /** The app has its own icon, so it can go back to the global style. */
    val hasOverride: Boolean,
    /** Imports the picture the user picked (blocking work off the main thread is up to the caller). */
    val importImage: suspend (Uri) -> CustomIconStore.ImportResult,
    val onResetToGlobal: () -> Unit,
)

/**
 * The icon style editor (#13) with its state: [saved] is the stored icon, [onApply] stores a new one. Leaving with
 * unsaved changes asks first; [onLeave] goes back. Previews of [apps] come from [icons] (none without it). With
 * [forApp] it edits that app's own icon (#14): its source can be another layer or a picture from the photo picker.
 */
@Composable
internal fun IconEditorRoute(
    saved: IconChoice,
    icons: IconRepository?,
    apps: List<LauncherApp>,
    onApply: (IconChoice) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    forApp: AppIconContext? = null,
) {
    val apply by rememberUpdatedState(onApply)
    val editor =
        rememberSaveable(saver = IconStyleEditor.saver { apply(it) }) {
            IconStyleEditor(IconEditorState(saved)) { apply(it) }
        }
    val state by editor.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var importProblem by rememberSaveable { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(icons) { onDispose { icons?.endPreview() } }
    val leave = { if (state.dirty) confirmDiscard = true else onLeave() }
    // The system photo picker: no permission, only the picked picture, read once.
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && forApp != null) {
                scope.launch {
                    importProblem =
                        when (val result = forApp.importImage(uri)) {
                            is CustomIconStore.ImportResult.Imported -> {
                                editor.useImage(result.name)
                                null
                            }

                            CustomIconStore.ImportResult.TooLarge -> {
                                R.string.icons_image_too_large
                            }

                            CustomIconStore.ImportResult.Unreadable -> {
                                R.string.icons_image_unreadable
                            }

                            CustomIconStore.ImportResult.Empty -> {
                                R.string.icons_image_empty
                            }
                        }
                }
            }
        }

    BackHandler(onBack = { if (confirmDiscard) confirmDiscard = false else leave() })
    Box(modifier) {
        IconEditorScreen(
            state = state,
            apps = apps,
            icons = icons,
            forApp =
                forApp?.let {
                    AppIconUi(it.app, it.global, it.hasOverride, importProblem)
                },
            onAction = { action ->
                when (action) {
                    is IconEditorAction.Choose -> {
                        editor.choose(action.preset)
                    }

                    is IconEditorAction.Edit -> {
                        editor.edit(action.change)
                    }

                    is IconEditorAction.UseLayer -> {
                        importProblem = null
                        editor.useLayer(action.layer)
                    }

                    IconEditorAction.PickImage -> {
                        importProblem = null
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }

                    IconEditorAction.Apply -> {
                        editor.apply()
                    }

                    IconEditorAction.Reset -> {
                        importProblem = null
                        editor.reset()
                    }

                    IconEditorAction.ResetToGlobal -> {
                        forApp?.onResetToGlobal?.invoke()
                        onLeave()
                    }

                    IconEditorAction.Back -> {
                        leave()
                    }
                }
            },
        )
        if (confirmDiscard) {
            DiscardDialog(
                onDiscard = {
                    confirmDiscard = false
                    onLeave()
                },
                onKeep = { confirmDiscard = false },
            )
        }
    }
}

/** Something the user did in the icon editor. */
sealed interface IconEditorAction {
    data class Choose(
        val preset: IconPreset,
    ) : IconEditorAction

    class Edit(
        val change: (CustomIconStyle) -> CustomIconStyle,
    ) : IconEditorAction

    data class UseLayer(
        val layer: IconLayer,
    ) : IconEditorAction

    /** Opens the photo picker for a picture of the app's own. */
    data object PickImage : IconEditorAction

    data object Apply : IconEditorAction

    data object Reset : IconEditorAction

    /** Removes the app's own icon. */
    data object ResetToGlobal : IconEditorAction

    data object Back : IconEditorAction
}

/** How many of the user's apps the preview shows (favorites first), and the preset cards. */
internal const val PREVIEW_APPS = 8
private const val CARD_APPS = 3

/** One app's icon in the editor: [problem] says why the last picture couldn't be used. */
internal data class AppIconUi(
    val app: LauncherApp,
    val global: IconStyle,
    val hasOverride: Boolean,
    val problem: Int? = null,
)

/** The icon style editor; stateless. */
@Composable
internal fun IconEditorScreen(
    state: IconEditorState,
    apps: List<LauncherApp>,
    icons: IconRepository?,
    onAction: (IconEditorAction) -> Unit,
    modifier: Modifier = Modifier,
    forApp: AppIconUi? = null,
) {
    val choice = state.draft
    val draft = choice.style
    val look = IconLook(draft.style, choice.layer, choice.image)
    val style = draft.style
    val custom = draft.custom ?: CustomIconStyle.from(style)
    val edit: ((CustomIconStyle) -> CustomIconStyle) -> Unit = { onAction(IconEditorAction.Edit(it)) }
    var originals by rememberSaveable { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }

    SettingsFrame(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TextButton(stringResource(R.string.action_back), { onAction(IconEditorAction.Back) }, color = TextColor)
            Title(
                if (forApp == null) {
                    stringResource(R.string.settings_icons)
                } else {
                    stringResource(R.string.icons_for_app, forApp.app.label)
                },
            )
            BasicText(
                iconStyleName(draft),
                style = BodyStyle.copy(color = PixelBuddyTheme.style.accent),
                modifier = Modifier.padding(top = 4.dp),
            )

            SectionHeader(stringResource(R.string.preview))
            val previewDescription =
                stringResource(if (originals || holding) R.string.icons_preview_originals else R.string.icons_preview)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(2.dp, PixelBuddyColors.TextMuted)
                        .pointerInput(Unit) {
                            // Touch and hold: the originals, as long as the finger stays down.
                            detectTapGestures(
                                onPress = {
                                    holding = true
                                    tryAwaitRelease()
                                    holding = false
                                },
                            )
                        }.clearAndSetSemantics { contentDescription = previewDescription },
            ) {
                DitheredSky(Modifier.matchParentSize())
                if (forApp == null) {
                    IconGrid(apps.take(PREVIEW_APPS), icons, look, originals || holding, Modifier.padding(16.dp))
                } else {
                    Comparison(forApp, icons, look, originals || holding, Modifier.padding(16.dp))
                }
            }
            ToggleRow(
                title = stringResource(R.string.icons_show_originals),
                subtitle = stringResource(R.string.icons_show_originals_summary),
                checked = originals,
                onChange = { originals = it },
                on = stringResource(R.string.toggle_on),
                off = stringResource(R.string.toggle_off),
            )

            SectionHeader(stringResource(R.string.icons_presets))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconPreset.entries) { preset ->
                    PresetCard(
                        preset = preset,
                        selected = draft.custom == null && draft.basePreset == preset,
                        apps = apps.take(CARD_APPS),
                        icons = icons,
                        onClick = { onAction(IconEditorAction.Choose(preset)) },
                    )
                }
            }

            if (forApp != null) {
                SectionHeader(stringResource(R.string.icons_source))
                ChoiceRow(
                    label = stringResource(R.string.icons_source_label),
                    options = IconSource.entries,
                    selected = IconSource.of(choice),
                    text = { stringResource(it.label) },
                    onSelect = { source ->
                        onAction(source.layer?.let { IconEditorAction.UseLayer(it) } ?: IconEditorAction.PickImage)
                    },
                )
                forApp.problem?.let {
                    BasicText(
                        stringResource(it),
                        style = MutedStyle,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (choice.image != null) {
                    TextButton(
                        stringResource(R.string.icons_image_choose_other),
                        { onAction(IconEditorAction.PickImage) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.icons_adjust))
            ChoiceRow(
                label = stringResource(R.string.icons_grid),
                options = IconStyle.GRIDS,
                selected = style.grid,
                text = { "$it" },
                onSelect = { grid -> edit { it.copy(grid = grid) } },
            )
            ChoiceRow(
                label = stringResource(R.string.icons_palette),
                options = PaletteChoice.entries,
                selected = custom.palette,
                text = { stringResource(it.label) },
                onSelect = { palette -> edit { it.copy(palette = palette) } },
            )
            custom.palette.colorRange?.let { range ->
                val colors = custom.colors.coerceIn(range)
                PixelSlider(
                    label = stringResource(R.string.icons_colors),
                    value = colors,
                    range = range,
                    valueText = "$colors",
                    onChange = { value -> edit { it.copy(colors = value) } },
                )
            }
            val percent = (style.intensity * 10).roundToInt()
            PixelSlider(
                label = stringResource(R.string.icons_intensity),
                value = percent,
                range = 0..10,
                valueText = "${percent * 10} %",
                onChange = { value -> edit { it.copy(intensity = value / 10f) } },
            )
            ToggleRow(
                title = stringResource(R.string.dithering),
                subtitle = stringResource(R.string.icons_dithering_summary),
                checked = style.dithering,
                onChange = { on -> edit { it.copy(dithering = on) } },
                on = stringResource(R.string.toggle_on),
                off = stringResource(R.string.toggle_off),
            )
            ToggleRow(
                title = stringResource(R.string.icons_outline),
                subtitle = stringResource(R.string.icons_outline_summary),
                checked = style.outline,
                onChange = { on -> edit { it.copy(outline = on) } },
                on = stringResource(R.string.toggle_on),
                off = stringResource(R.string.toggle_off),
            )
            ToggleRow(
                title = stringResource(R.string.icons_masked),
                subtitle = stringResource(R.string.icons_masked_summary),
                checked = custom.masked,
                onChange = { on -> edit { it.copy(masked = on) } },
                on = stringResource(R.string.toggle_on),
                off = stringResource(R.string.toggle_off),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                stringResource(R.string.icons_apply),
                { onAction(IconEditorAction.Apply) },
                enabled = state.dirty,
            )
            TextButton(
                stringResource(R.string.icons_reset),
                { onAction(IconEditorAction.Reset) },
                color = TextColor,
                enabled = state.dirty,
            )
        }
        if (forApp?.hasOverride == true) {
            TextButton(stringResource(R.string.icons_reset_global), { onAction(IconEditorAction.ResetToGlobal) })
        }
    }
}

/** [apps] in rows of four, at home's icon size. */
@Composable
private fun IconGrid(
    apps: List<LauncherApp>,
    icons: IconRepository?,
    look: IconLook,
    originals: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        apps.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { app -> PreviewIcon(app, icons, look, originals, size = 48.dp) }
            }
        }
    }
}

/** One app in the style of all icons next to its own one, at home's icon size. */
@Composable
private fun Comparison(
    forApp: AppIconUi,
    icons: IconRepository?,
    look: IconLook,
    originals: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = modifier) {
        listOf(
            R.string.icons_compare_global to IconLook(forApp.global),
            R.string.icons_compare_app to look,
        ).forEach { (label, shown) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PreviewIcon(forApp.app, icons, shown, originals, size = 64.dp)
                Spacer(Modifier.height(8.dp))
                BasicText(stringResource(label), style = MutedStyle.copy(color = TextColor))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: IconPreset,
    selected: Boolean,
    apps: List<LauncherApp>,
    icons: IconRepository?,
    onClick: () -> Unit,
) {
    val name = stringResource(preset.label)
    val color = if (selected) PixelBuddyTheme.style.accent else PixelBuddyColors.TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(132.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .clearAndSetSemantics { contentDescription = name }
                .border(2.dp, color)
                .padding(bottom = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            DitheredSky(Modifier.matchParentSize())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.forEach { app -> PreviewIcon(app, icons, IconLook(preset.style), original = false, size = 32.dp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(name, style = BodyStyle.copy(color = if (selected) color else TextColor))
    }
}

/**
 * [app] as [look] says, or its [original] icon, rendered off the main thread. A new look cancels the render still
 * running for the old one; the old picture stays until the new one is ready.
 */
@Composable
private fun PreviewIcon(
    app: LauncherApp,
    icons: IconRepository?,
    look: IconLook,
    original: Boolean,
    size: Dp,
) {
    val style = look.style
    val density = LocalDensity.current
    val densityDpi = LocalConfiguration.current.densityDpi
    // Whole physical pixels per icon pixel, like on home.
    val sidePx = maxOf(1, (with(density) { size.toPx() } / style.grid).toInt()) * style.grid
    val bitmap by produceState<ImageBitmap?>(null, icons, app.key, look, original, densityDpi, sidePx) {
        value =
            icons
                ?.let { if (original) it.original(app, sidePx, densityDpi) else it.preview(app, look, densityDpi) }
                ?.asImageBitmap()
    }
    Box(Modifier.size(with(density) { sidePx.toDp() })) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                filterQuality = if (original) FilterQuality.Low else FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DiscardDialog(
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    val title = stringResource(R.string.icons_discard_title)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Shadow.copy(alpha = 0.8f))
                .pointerInput(Unit) { detectTapGestures { onKeep() } },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(32.dp)
                    .background(PixelBuddyColors.Navy)
                    .border(3.dp, TextColor)
                    .padding(24.dp)
                    .semantics { paneTitle = title },
        ) {
            BasicText(title, style = TitleStyle)
            Spacer(Modifier.height(16.dp))
            TextButton(stringResource(R.string.icons_discard), onDiscard)
            TextButton(stringResource(R.string.icons_keep), onKeep, color = TextColor)
        }
    }
}

private val PaletteChoice.label: Int
    get() =
        when (this) {
            PaletteChoice.Adaptive -> R.string.palette_adaptive
            PaletteChoice.Original -> R.string.palette_original
            PaletteChoice.Retro16 -> R.string.palette_retro16
            PaletteChoice.Pocket -> R.string.palette_pocket
            PaletteChoice.Neon -> R.string.palette_neon
            PaletteChoice.Mono -> R.string.palette_mono
        }

/** Where an app's own icon comes from. */
internal enum class IconSource(
    val layer: IconLayer?,
    val label: Int,
) {
    Whole(IconLayer.Full, R.string.icons_source_full),
    Foreground(IconLayer.Foreground, R.string.icons_source_foreground),
    Monochrome(IconLayer.Monochrome, R.string.icons_source_monochrome),
    Picture(null, R.string.icons_source_picture),
    ;

    companion object {
        fun of(choice: IconChoice): IconSource =
            if (choice.image != null) Picture else entries.first { it.layer == choice.layer }
    }
}
