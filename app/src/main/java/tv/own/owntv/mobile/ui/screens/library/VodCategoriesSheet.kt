package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.home.formatCount
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag

/**
 * The categories sheet of Movies and Series: "Categories · Movies", search, the fixed
 * entries with their icons — Favourites, History, All movies — then PROVIDERS and the provider folders by their
 * parsed names, with tags, counts (Show counts) and playlist marks. The open category is filled. A hold on a
 * folder opens its menu ([onHold]).
 */
@Composable
internal fun VodCategoriesSheet(
    tab: LibraryTab,
    categories: List<VodCategory>,
    selected: LiveKey,
    counts: Map<LiveKey, Int>,
    showCounts: Boolean,
    marks: Map<Long, PlaylistMark>,
    onSelect: (LiveKey) -> Unit,
    onHold: (VodCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val fixed = listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All)
        .mapNotNull { key -> categories.firstOrNull { it.key == key } }
    val groups = categories.filter { it.builtIn == null }
    val parsed = remember(groups) { groups.associateWith { ProviderTags.parse(it.title.orEmpty()) } }
    val labels = fixed.associateWith { it.label(tab) }
    val q = query.trim()
    val shownFixed = fixed.filter { q.isEmpty() || labels.getValue(it).contains(q, ignoreCase = true) }
    val shownGroups = groups.filter { q.isEmpty() || ProviderTags.groupLabel(parsed.getValue(it), headingCountry = null).contains(q, ignoreCase = true) }
    fun pick(key: LiveKey) {
        onSelect(key)
        onDismiss()
    }
    fun countOf(key: LiveKey) = counts[key]?.takeIf { showCounts }?.let(::formatCount)

    val title = stringResource(R.string.content_category_browser_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(
            title = title,
            extra = stringResource(if (tab == LibraryTab.MOVIES) R.string.common_nav_movies else R.string.common_nav_series),
        )
        StageSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.content_search_categories),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        )
        // `max-height: 700px` of an 887 dp frame, less the header and the search above.
        val listMax = (LocalConfiguration.current.screenHeightDp * 0.79f).dp - 141.dp
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax)) {
            items(shownFixed, key = { it.key.toString() }) { c ->
                StageMenuItem(
                    label = labels.getValue(c),
                    onClick = { pick(c.key) },
                    icon = when (c.builtIn) {
                        VodCategory.BuiltIn.FAVORITES -> MobileIcons.FavoriteBorder
                        VodCategory.BuiltIn.HISTORY -> MobileIcons.History
                        else -> if (tab == LibraryTab.MOVIES) MobileIcons.Movie else MobileIcons.Series
                    },
                    filled = c.key == selected,
                    count = countOf(c.key),
                )
            }
            if (shownGroups.isNotEmpty()) item(key = "heading") { StageGroupLabel(stringResource(R.string.content_vod_providers)) }
            items(shownGroups, key = { it.key.toString() }) { c ->
                val name = parsed.getValue(c)
                StageMenuItem(
                    label = ProviderTags.groupLabel(name, headingCountry = null),
                    onClick = { pick(c.key) },
                    onLongClick = { onHold(c) },
                    filled = c.key == selected,
                    count = countOf(c.key),
                    tags = if (name.tags.isEmpty()) null else { { name.tags.forEach { StageTag(it) } } },
                    mark = c.sourceId?.let(marks::get),
                )
            }
        }
    }
}
