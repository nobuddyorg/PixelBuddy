package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppListEntry
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import io.github.nobuddyorg.pixelbuddy.core.search.AppSearch

/** A search result row: [highlight] are the matched characters of the label, [top] marks the Enter target. */
internal typealias SearchEntry = @Composable (
    rowId: String,
    app: LauncherApp,
    highlight: List<IntRange>,
    top: Boolean,
) -> Unit

/**
 * Search: a field that takes the keyboard focus right away and the results below it, ranked by [AppSearch].
 * Without a query every app is listed under its section. Enter (Go) launches the top result; with no matching app
 * it searches the web, if [onWebSearch] is given, which also adds a line for that below the results.
 */
@Composable
internal fun SearchPanel(
    apps: List<AppListEntry>,
    onLaunch: (LauncherApp) -> Unit,
    entry: SearchEntry,
    modifier: Modifier = Modifier,
    onWebSearch: ((String) -> Unit)? = null,
) {
    val state = rememberTextFieldState()
    val query = state.text.toString()
    // Recomputed per keystroke only; ~200 labels take well under a millisecond.
    val results =
        remember(apps, query) { if (query.isBlank()) null else AppSearch.search(apps, query) { it.app.label } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val hint = stringResource(R.string.home_search_hint)

    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        BasicText(
            stringResource(R.string.home_search).uppercase(),
            style = PixelText.small.copy(color = PixelBuddyTheme.style.accent),
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp).semantics { heading() },
        )
        BasicTextField(
            state = state,
            textStyle = PixelText.body,
            cursorBrush = SolidColor(PixelBuddyTheme.style.accent),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
            onKeyboardAction = {
                val top = results?.firstOrNull()
                when {
                    top != null -> onLaunch(top.item.app)
                    results != null && onWebSearch != null -> onWebSearch(query.trim())
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = hint },
            decorator = { field ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                        Modifier
                            .drawBehind {
                                val offset = 4.dp.toPx()
                                drawRect(PixelBuddyColors.Shadow, topLeft = Offset(offset, offset), size = size)
                            }.background(FieldBackground)
                            .border(4.dp, PixelBuddyColors.Text)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp),
                ) {
                    if (state.text.isEmpty()) {
                        BasicText(
                            hint,
                            style = PixelText.body.copy(color = PixelBuddyColors.TextMuted),
                        )
                    }
                    field()
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                results == null -> {
                    apps.groupBy { it.section }.forEach { (section, group) ->
                        item(key = "section/$section") { SectionLabel(section) }
                        items(
                            group,
                            key = { "app/" + it.app.key.id },
                        ) { entry("app/" + it.app.key.id, it.app, emptyList(), false) }
                    }
                }

                results.isEmpty() -> {
                    item(key = "empty") {
                        BasicText(
                            stringResource(R.string.home_search_empty),
                            style = PixelText.small,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                else -> {
                    items(results, key = { "result/" + it.item.app.key.id }) { result ->
                        val app = result.item.app
                        entry("result/" + app.key.id, app, result.match.ranges, result === results.first())
                    }
                }
            }
            if (results != null && onWebSearch != null) {
                item(key = "web") { WebSearchRow(query.trim()) { onWebSearch(query.trim()) } }
            }
        }
    }
}

/** "► Search the web for "x"": hands the query to the phone's web search. */
@Composable
private fun WebSearchRow(
    query: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp),
    ) {
        BasicText(
            "► " + stringResource(R.string.home_search_web, query),
            style = PixelText.small.copy(color = PixelBuddyTheme.style.accent),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FieldBackground = Color(0xFF0D0F24)
