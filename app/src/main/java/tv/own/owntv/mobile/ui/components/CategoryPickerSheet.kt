package tv.own.owntv.mobile.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R

/**
 * Find a category by typing, instead of dragging the chip strip past four hundred of them.
 *
 * A playlist with hundreds of categories makes the horizontal rail useless on a phone — the one on
 * the television at least has a rail tall enough to page through. Picking one closes the sheet.
 *
 * Drawn as the player's lists are: parsed names, their tags, the open category filled.
 */
@Composable
fun CategoryPickerSheet(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Whether picking closes the sheet.
     *
     * True for the Live screen, where a category *is* the destination. False where the category is
     * only the first of two steps — the Multiview picker and the player's channel button — because
     * there the dismiss ran straight after the select and tore down the very state the select had
     * just set, so choosing a category made the whole picker vanish.
     */
    dismissOnSelect: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(labels, query) {
        labels.withIndex().filter { (_, label) -> label.contains(query.trim(), ignoreCase = true) }
    }
    val title = stringResource(R.string.content_category_browser_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(title = title, extra = stringResource(R.string.common_nav_live_tv))
        tv.own.owntv.mobile.ui.stage.StageSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.content_search_categories),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            itemsIndexed(matches, key = { _, (index, _) -> index }) { _, (index, label) ->
                val name = remember(label) { tv.own.owntv.core.live.ProviderTags.parse(label) }
                tv.own.owntv.mobile.ui.stage.StageMenuItem(
                    label = tv.own.owntv.core.live.ProviderTags.groupLabel(name, headingCountry = null),
                    onClick = {
                        onSelect(index)
                        if (dismissOnSelect) onDismiss()
                    },
                    filled = index == selectedIndex,
                    tags = if (name.tags.isEmpty()) null else { { name.tags.forEach { tv.own.owntv.mobile.ui.stage.StageTag(it) } } },
                )
            }
        }
    }
}
