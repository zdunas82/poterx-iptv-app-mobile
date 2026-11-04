package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.content.VodFilter
import tv.own.owntv.core.customize.CategoryRestorePlan
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.home.groupedCountText
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.TwoPane
import tv.own.owntv.mobile.ui.nav.isExpandedWidth
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.screens.home.durationText
import tv.own.owntv.mobile.ui.screens.home.formatRating
import tv.own.owntv.mobile.ui.screens.home.formatYear
import tv.own.owntv.mobile.ui.screens.live.CategoryRestoreConfirm
import tv.own.owntv.mobile.ui.screens.live.LiveCategoryMenu
import tv.own.owntv.mobile.ui.shell.ProvidePageCrumb
import tv.own.owntv.mobile.ui.shell.PageCrumb
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageChoiceRow
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StagePlaylistMark
import tv.own.owntv.mobile.ui.stage.StagePoster
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSegmented
import org.koin.compose.koinInject
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.nav.NavVisibility
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStepper
import tv.own.owntv.mobile.ui.stage.StageSizeBar
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.LocalAnimations

/**
 * Movies and Series: each its own place on the bar, the same screen locked to its half.
 *
 * The header's crumb names the open category and its count ("‹ **Disney+ Kids Movies** · 136 titles"); a tap
 * opens the categories sheet. Under it the tool row — search inside the category, the sort order, the
 * categories, Grid | List — then three posters a row (rating, playlist mark or ✓, progress) or the list rows.
 *
 * The grid decides its own column count from the width it was given, until the user pinches or moves Poster
 * size: from then on their number is kept and travels with their backup, like the TV's fixed grid setting.
 * On a tablet the title opens in a pane beside the grid.
 */
@Composable
fun LibraryScreen(
    scrollToTop: SharedFlow<String>,
    route: String,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    fixedTab: LibraryTab? = null,
    vm: LibraryViewModel = koinViewModel(),
) {
    LaunchedEffect(fixedTab) { fixedTab?.let { vm.select(it) } }

    val tab by vm.tab.collectAsStateWithLifecycle()
    // Library (no fixed tab) offers Movies | Series — only the halves the playlist has; with one, it is that one.
    val navVisibility: NavVisibility = koinInject()
    val sections by remember(navVisibility) { navVisibility.visibleSections() }
        .collectAsStateWithLifecycle(MainSection.allBrowse)
    val switchTabs = if (fixedTab != null) emptyList() else buildList {
        if (MainSection.MOVIES in sections) add(LibraryTab.MOVIES)
        if (MainSection.SERIES in sections) add(LibraryTab.SERIES)
    }
    LaunchedEffect(switchTabs) { switchTabs.singleOrNull()?.let { vm.select(it) } }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val chosenColumns by vm.gridColumns.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val progress by vm.movieProgress.collectAsStateWithLifecycle()
    val seriesProgress by vm.seriesProgress.collectAsStateWithLifecycle()
    val watched by vm.watchedIds.collectAsStateWithLifecycle()
    val marks by vm.playlistMarks.collectAsStateWithLifecycle()
    val seasonCounts by vm.seasonCounts.collectAsStateWithLifecycle()
    val movies = tab == LibraryTab.MOVIES
    // The resume bar of one tile or row: a film's own position, a show's latest episode. None once watched.
    fun progressOf(id: Long): Float? = if (id in watched) null else when (tab) {
        LibraryTab.MOVIES -> progress[id]?.takeIf { it.durationMs > 1L }?.let { it.positionMs.toFloat() / it.durationMs }
        LibraryTab.SERIES -> seriesProgress[id]
    }

    val items = (if (movies) vm.movies else vm.series).collectAsLazyPagingItems()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    gridState.ObeyGridScrollToTop(route, scrollToTop)
    listState.ObeyScrollToTop(route, scrollToTop)

    var menuFor by remember { mutableStateOf<VodItem?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var categoryMenuFor by remember { mutableStateOf<VodCategory?>(null) }
    var restoreFor by remember { mutableStateOf<VodCategory?>(null) }

    val twoPane = isExpandedWidth()
    // Beside the grid, something is always open: the pane is a preview of the grid, so it falls back
    // to the first tile until the user taps another. Derived rather than stored, so a category whose
    // first title happens to be the one already showing still fills the pane instead of emptying it.
    var tappedItem by rememberSaveable { mutableStateOf<Long?>(null) }
    val openItem = tappedItem ?: if (twoPane) items.peekFirstId() else null
    // The title last opened keeps its marker when you come back to the list (`.selbar`, `.pc.sel`).
    var lastOpened by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = { item: VodItem ->
        lastOpened = item.id
        // Wide: a tap picks the title for the card, a second tap on it opens its page.
        if (twoPane && item.id != openItem) tappedItem = item.id else onOpenItem(tab, item.id)
    }

    // A new selection's scroll position has nothing to do with the old one's, and neither does the
    // title that was open in the pane — it is not in this category any more.
    // Only on a real change: coming back from a title page re-enters this screen with the same selection,
    // and the saved scroll position must survive that.
    var shownSelection by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, selected) {
        val selection = "$tab|$selected"
        if (shownSelection == selection) return@LaunchedEffect
        val first = shownSelection == null
        shownSelection = selection
        if (first) return@LaunchedEffect
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
        tappedItem = null
    }
    // A new order or filter starts the list at its top as well: the old position could sit past the end
    // of a list the year filter has just shortened several times over.
    var shownOrder by remember { mutableStateOf(sort to filter) }
    LaunchedEffect(sort, filter) {
        if (shownOrder == (sort to filter)) return@LaunchedEffect
        shownOrder = sort to filter
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    // The header's crumb: "‹ Disney+ Kids Movies · 136 titles", a tap opens the categories.
    val categoryLabel = categories.firstOrNull { it.key == selected }?.label(tab).orEmpty()
    val countText = groupedCountText(if (movies) R.plurals.content_vod_title_count else R.plurals.content_vod_series_count, count)
    ProvidePageCrumb { owner -> PageCrumb(categoryLabel, countText, owner = owner) { categoriesOpen = true } }

    // A show's list row says how many seasons it has — read for the rows on screen only.
    if (!movies && viewMode == SettingsRepository.VodViewMode.LIST) {
        LaunchedEffect(listState, items) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { indices -> vm.loadSeasonCounts(indices.mapNotNull { items.peek(it)?.id }) }
        }
    }

    val listPane = @Composable {
    // The column count comes from the width this pane actually got, not the window's: beside a
    // detail pane, or next to the rail, the grid has less room than the screen is wide.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Roughly one poster per 110 dp of width, which is three on a phone and eight on a tablet in
    // landscape. The user's own number wins whenever they have set one.
    // Beside the card the posters are six across 520 dp.
    val columns = chosenColumns.takeIf { it > 0 }
        ?: (maxWidth.value.toInt() / (if (twoPane) WIDE_COLUMN_WIDTH_DP else COLUMN_WIDTH_DP)).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    Column(Modifier.fillMaxSize()) {
        if (switchTabs.size > 1) {
            StageSegmented(
                options = switchTabs.map { stringResource(if (it == LibraryTab.MOVIES) R.string.common_nav_movies else R.string.common_nav_series) },
                selected = switchTabs.indexOf(tab).coerceAtLeast(0),
                onSelect = { vm.select(switchTabs[it]) },
                fill = true,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = TOOLS_TOP),
            )
        }
        // `.tools`: the search field takes the room, then the order and Grid | List at the right. No categories
        // tool: the crumb under the title opens the same sheet.
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = TOOLS_TOP, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageSearchField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = stringResource(R.string.common_search),
                inline = true,
                modifier = Modifier.weight(1f),
            )
            // The order and Grid | List sit at the right edge and the search takes all the rest; a long order
            // ("Hinzugefügt am" at a large font) shortens at 150 dp rather than squeezing the field to its icon.
            StageTool(
                onClick = { sortOpen = true },
                icon = MobileIcons.Sort,
                value = stringResource(sort.labelRes()),
                on = sortOpen,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            // The year filter (Poterx) lives in the same sheet; its tool shows the range while one is set.
            StageTool(
                onClick = { sortOpen = true },
                icon = MobileIcons.CalendarMonth,
                value = filter.takeIf { it.active }?.let { vodFilterLabel(it) },
                on = filter.active,
                contentDescription = stringResource(if (movies) R.string.poterx_filter_movies_title else R.string.poterx_filter_series_title),
                modifier = Modifier.widthIn(max = 120.dp),
            )
            val views = SettingsRepository.VodViewMode.entries
            StageSegmented(
                options = views.map { stringResource(it.labelRes()) },
                selected = views.indexOf(viewMode),
                onSelect = { vm.setViewMode(views[it]) },
                icons = listOf(MobileIcons.GridView, MobileIcons.FormatListBulleted),
                iconOnly = true,
            )
        }
        // Empty only once the first page has actually come back — otherwise every category change
        // flashes "No movies here" for as long as the query takes on a 170k-title catalogue.
        if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
            EmptyLibrary(tab, query)
        } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { item ->
                        VodListRow(
                            item = item,
                            seasons = seasonCounts[item.id]?.takeIf { !movies && it > 0 },
                            progress = progressOf(item.id),
                            watched = item.id in watched,
                            mark = marks[item.sourceId],
                            selected = item.id == lastOpened,
                            onClick = { open(item) },
                            onLongClick = { menuFor = item },
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToResize(columns) { vm.setGridColumns(it) },
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { item ->
                        StagePoster(
                            imageUrl = item.posterUrl,
                            title = item.name,
                            rating = item.rating?.takeIf { it > 0 }?.let(::formatRating),
                            mark = marks[item.sourceId],
                            watched = item.id in watched,
                            progress = progressOf(item.id),
                            selected = item.id == (if (twoPane) openItem else lastOpened),
                            onClick = { open(item) },
                            onLongClick = { menuFor = item },
                        )
                    }
                }
            }
        }
    }

    if (sortOpen) {
        VodSortSheet(
            tab = tab,
            sort = sort,
            viewMode = viewMode,
            columns = columns,
            filter = filter,
            onSort = vm::setSort,
            onFilter = vm::setFilter,
            onViewMode = vm::setViewMode,
            onColumns = vm::setGridColumns,
            onDismiss = { sortOpen = false },
        )
    }
    }
    }

    if (twoPane) {
        TwoPane(
            list = listPane,
            detail = {
                openItem?.let { id ->
                    LibraryTitleCard(tab = tab, itemId = id, onPlay = onPlay, onOpen = { onOpenItem(tab, id) })
                }
            },
            modifier = modifier,
            // The grid 520 of 791 dp.
            listShare = WIDE_GRID_SHARE,
        )
    } else {
        Box(modifier) { listPane() }
    }

    if (categoriesOpen) {
        val counts by vm.categoryCounts.collectAsStateWithLifecycle()
        val showCounts by vm.sheetCounts.collectAsStateWithLifecycle()
        VodCategoriesSheet(
            tab = tab,
            categories = categories,
            selected = selected,
            counts = counts,
            showCounts = showCounts,
            marks = marks,
            onSelect = vm::select,
            onHold = { categoryMenuFor = it },
            onDismiss = { categoriesOpen = false },
        )
    }

    menuFor?.let { item ->
        VodMenu(
            item = item,
            tab = tab,
            selected = selected,
            isFavorite = item.id in favorites,
            originName = categoryLabel,
            categories = categories,
            vm = vm,
            onDismiss = { menuFor = null },
        )
    }

    categoryMenuFor?.let { category ->
        val name = category.label(tab)
        LiveCategoryMenu(
            title = name,
            custom = category.key is LiveKey.Custom,
            onMove = { kind -> vm.moveCategory(category.key, kind) },
            onHide = { vm.hideCategory(category.key) },
            onRestore = { categoryMenuFor = null; restoreFor = category },
            onDismiss = { categoryMenuFor = null },
        )
    }
    restoreFor?.let { category ->
        val plan by produceState<CategoryRestorePlan?>(null, category.key) { value = vm.categoryRestorePlan(category.key) }
        CategoryRestoreConfirm(
            title = category.label(tab),
            custom = category.key is LiveKey.Custom,
            plan = plan,
            onRestore = { vm.restoreCategory(category.key) },
            onDismiss = { restoreFor = null },
        )
    }
}

/**
 * `.chrow` of the list view: 92 dp — the poster 52 × 78 (radius 9), the title (14.5 / 700, two lines
 * allowed), "2026 · ★ 8.4 · 1 h 30 min" (12 muted, the rating in gold) or a show's seasons, the progress
 * under it (140 wide); at the end ✓ when watched, else the playlist mark. [selected] = the title last opened.
 */
@Composable
private fun VodListRow(
    item: VodItem,
    seasons: Int?,
    progress: Float?,
    watched: Boolean,
    mark: PlaylistMark?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val separator = stringResource(R.string.content_epg_bits_separator)
    val length = item.durationSecs?.takeIf { it >= 60 }?.let { durationText(it / 60L) }
        ?: seasons?.let { pluralStringResource(R.plurals.home_trending_seasons, it, it) }
    val line = buildAnnotatedString {
        val parts = listOfNotNull(
            item.year?.takeIf { it > 0 }?.let { { append(formatYear(it)) } },
            item.rating?.takeIf { it > 0 }?.let { { withStyle(SpanStyle(color = RatingGold)) { append("★ " + formatRating(it)) } } },
            length?.let { { append(it) } },
        )
        parts.forEachIndexed { i, part ->
            if (i > 0) append(separator)
            part()
        }
    }
    StageRow(onClick = onClick, onLongClick = onLongClick, selected = selected, height = 92.dp) {
        AsyncImage(
            item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp, 78.dp).clip(RoundedCornerShape(9.dp)).background(palette.cardSolid),
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, style = stageText(14.5f, 700), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line.isNotEmpty()) {
                Text(line, style = stageText(12f, 400), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            progress?.let { StageProgress(it, Modifier.padding(top = 6.dp).width(140.dp), thin = true) }
        }
        when {
            watched -> StageIcon(MobileIcons.Check, 14.dp, stageAccent.accent)
            mark != null -> StagePlaylistMark(mark)
        }
    }
}

/**
 * Sort: "Sort movies · Applies to every category in this section", the four orders — each with its
 * line while it is not the current one, the current filled and ticked — then VIEW Grid | List and, on the
 * grid, POSTER SIZE (the number the pinch sets; to the right is bigger posters, fewer a row).
 */
@Composable
private fun VodSortSheet(
    tab: LibraryTab,
    sort: SettingsRepository.SortMode,
    viewMode: SettingsRepository.VodViewMode,
    columns: Int,
    filter: VodFilter,
    onSort: (SettingsRepository.SortMode) -> Unit,
    onFilter: (VodFilter) -> Unit,
    onViewMode: (SettingsRepository.VodViewMode) -> Unit,
    onColumns: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(if (tab == LibraryTab.MOVIES) R.string.content_sort_movies_title else R.string.content_sort_series_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.content_sort_applies_hint))
        listOf(
            Triple(SettingsRepository.SortMode.PLAYLIST, MobileIcons.Layers, R.string.content_sort_provider_order_hint),
            Triple(SettingsRepository.SortMode.ALPHA, MobileIcons.FormatListBulleted, null),
            Triple(SettingsRepository.SortMode.RATING, MobileIcons.StarBorder, R.string.content_sort_rating_hint),
            Triple(SettingsRepository.SortMode.DATE_ADDED, MobileIcons.Schedule, R.string.content_sort_date_added_hint),
            Triple(SettingsRepository.SortMode.YEAR, MobileIcons.CalendarMonth, R.string.poterx_sort_year_hint),
        ).forEach { (mode, icon, hint) ->
            val current = mode == sort
            StageMenuItem(
                label = stringResource(mode.labelRes()),
                onClick = { onSort(mode) },
                icon = icon,
                value = hint?.takeIf { !current }?.let { stringResource(it) },
                filled = current,
                checked = current,
            )
        }
        VodYearFilter(tab, filter, onFilter)
        StageGroupLabel(stringResource(R.string.content_menu_group_view))
        val views = SettingsRepository.VodViewMode.entries
        StageChoiceRow(
            options = views.map { stringResource(it.labelRes()) },
            selected = views.indexOf(viewMode),
            onSelect = { onViewMode(views[it]) },
        )
        if (viewMode == SettingsRepository.VodViewMode.GRID) {
            StageGroupLabel(stringResource(R.string.content_poster_size))
            StageSizeBar(
                fraction = (MAX_COLUMNS - columns).toFloat() / (MAX_COLUMNS - MIN_COLUMNS),
                onChange = { onColumns(MAX_COLUMNS - kotlin.math.round(it * (MAX_COLUMNS - MIN_COLUMNS)).toInt()) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** The oldest year the filter steps down to; the newest is next year (a provider lists titles early). */
private const val FILTER_MIN_YEAR = 1900
private fun filterMaxYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1

/** The filter as a short range: "2010–2019", "from 2020", "to 1989"; "Year" when off. */
@Composable
internal fun vodFilterLabel(filter: VodFilter): String {
    val from = filter.yearFrom
    val to = filter.yearTo
    return when {
        from != null && to != null -> if (from == to) from.toString() else stringResource(R.string.poterx_filter_range, from, to)
        from != null -> stringResource(R.string.poterx_filter_from_only, from)
        to != null -> stringResource(R.string.poterx_filter_to_only, to)
        else -> stringResource(R.string.poterx_filter)
    }
}

/** The decades as quick picks: this decade on, then three before it, then everything older. */
private fun vodFilterPresets(): List<VodFilter> {
    val decade = (filterMaxYear() - 1) / 10 * 10
    return listOf(
        VodFilter(yearFrom = decade),
        VodFilter(yearFrom = decade - 10, yearTo = decade - 1),
        VodFilter(yearFrom = decade - 20, yearTo = decade - 11),
        VodFilter(yearFrom = decade - 30, yearTo = decade - 21),
        VodFilter(yearTo = decade - 31),
    )
}

/**
 * The sort sheet's YEAR section (Poterx): the decades as a choice row (tap the chosen one again to
 * clear), then From / To steppers — a tap on a row turns its bound on or off. Every change applies at
 * once, so the list behind the sheet re-queries as the user steps.
 */
@Composable
private fun VodYearFilter(tab: LibraryTab, filter: VodFilter, onFilter: (VodFilter) -> Unit) {
    val max = filterMaxYear()
    val any = stringResource(R.string.poterx_filter_any)
    fun apply(from: Int?, to: Int?) {
        var f = from?.coerceIn(FILTER_MIN_YEAR, max)
        var t = to?.coerceIn(FILTER_MIN_YEAR, max)
        // Keep the range well-formed as either bound steps past the other.
        if (f != null && t != null && f > t) { if (from != filter.yearFrom) t = f else f = t }
        onFilter(VodFilter(f, t))
    }
    StageGroupLabel(stringResource(if (tab == LibraryTab.MOVIES) R.string.poterx_filter_movies_title else R.string.poterx_filter_series_title))
    // Six picks over two rows of three: one row would squeeze "2010–2019" past a phone's width.
    val presets = listOf(VodFilter.NONE) + vodFilterPresets()
    val labels = listOf(any) + presets.drop(1).map { vodFilterLabel(it) }
    val chosen = if (!filter.active) 0 else presets.indexOf(filter)
    presets.chunked(3).forEachIndexed { row, chunk ->
        StageChoiceRow(
            options = chunk.indices.map { labels[row * 3 + it] },
            selected = chosen - row * 3,
            onSelect = { onFilter(presets[row * 3 + it]) },
        )
    }
    StageSettingRow(
        title = stringResource(R.string.poterx_filter_year_from),
        icon = MobileIcons.CalendarMonth,
        minHeight = 48.dp,
        onClick = { apply(if (filter.yearFrom == null) (filter.yearTo ?: max - 1) - 10 else null, filter.yearTo) },
    ) {
        StageStepper(
            filter.yearFrom?.toString() ?: any,
            onMinus = { apply((filter.yearFrom ?: (filter.yearTo ?: max - 1)) - 1, filter.yearTo) },
            onPlus = { apply((filter.yearFrom ?: (filter.yearTo ?: max - 1)) + 1, filter.yearTo) },
            minusEnabled = (filter.yearFrom ?: max) > FILTER_MIN_YEAR,
            plusEnabled = (filter.yearFrom ?: FILTER_MIN_YEAR) < max,
        )
    }
    StageSettingRow(
        title = stringResource(R.string.poterx_filter_year_to),
        icon = MobileIcons.CalendarMonth,
        minHeight = 48.dp,
        onClick = { apply(filter.yearFrom, if (filter.yearTo == null) max - 1 else null) },
    ) {
        StageStepper(
            filter.yearTo?.toString() ?: any,
            onMinus = { apply(filter.yearFrom, (filter.yearTo ?: (filter.yearFrom ?: max - 1)) - 1) },
            onPlus = { apply(filter.yearFrom, (filter.yearTo ?: (filter.yearFrom ?: max - 1)) + 1) },
            minusEnabled = (filter.yearTo ?: max) > FILTER_MIN_YEAR,
            plusEnabled = (filter.yearTo ?: FILTER_MIN_YEAR) < max,
        )
    }
}

/** Nothing in the category, or nothing matching the search — core's sentences, in the Stage type. */
@Composable
private fun EmptyLibrary(tab: LibraryTab, query: String) {
    val movies = tab == LibraryTab.MOVIES
    val q = query.trim()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = when {
                q.isNotEmpty() -> stringResource(if (movies) R.string.content_no_movies_found else R.string.content_no_series_found, q)
                else -> stringResource(if (movies) R.string.content_no_movies_here else R.string.content_no_series_here)
            },
            style = stageText(13.5f, 500),
            color = LocalStagePalette.current.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The entry's name: a translated label for the three fixed ones; a provider folder without its tags. */
@Composable
internal fun VodCategory.label(tab: LibraryTab): String = when (builtIn) {
    VodCategory.BuiltIn.ALL -> stringResource(
        if (tab == LibraryTab.MOVIES) R.string.content_category_all_movies else R.string.content_category_all_series,
    )
    VodCategory.BuiltIn.FAVORITES -> stringResource(R.string.content_category_favorites)
    VodCategory.BuiltIn.HISTORY -> stringResource(R.string.content_category_history)
    null -> remember(title) { ProviderTags.parse(title.orEmpty()).name }
}

private fun SettingsRepository.SortMode.labelRes() = when (this) {
    SettingsRepository.SortMode.PLAYLIST -> R.string.content_sort_provider_order
    SettingsRepository.SortMode.ALPHA -> R.string.settings_sort_alpha
    SettingsRepository.SortMode.RATING -> R.string.settings_sort_rating
    SettingsRepository.SortMode.DATE_ADDED -> R.string.settings_sort_date_added
    SettingsRepository.SortMode.YEAR -> R.string.poterx_sort_year
}

private fun SettingsRepository.VodViewMode.labelRes() = when (this) {
    SettingsRepository.VodViewMode.GRID -> R.string.settings_view_grid
    SettingsRepository.VodViewMode.LIST -> R.string.settings_view_list
}

/** [tv.own.owntv.mobile.ui.screens.ObeyScrollToTop] for a grid. */
@Composable
private fun LazyGridState.ObeyGridScrollToTop(route: String, scrollToTop: SharedFlow<String>) {
    val instant = LocalAnimations.current == AnimationLevel.OFF
    LaunchedEffect(route, instant) {
        scrollToTop.collect { requested ->
            if (requested != route) return@collect
            if (instant) scrollToItem(0) else animateScrollToItem(0)
        }
    }
}

/**
 * Pinch the posters bigger or smaller.
 *
 * It watches for a second finger and only then takes the gesture, so an ordinary one-finger scroll
 * is never stolen from the grid underneath. One step per pinch: the count changes once and the
 * gesture then does nothing until the fingers come up, which stops a slow squeeze from running the
 * whole way from eight columns to two.
 */
private fun Modifier.pinchToResize(columns: Int, onChange: (Int) -> Unit): Modifier =
    pointerInput(columns) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var startDistance = 0f
            var changed = false
            var down = true
            while (down) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (startDistance == 0f) {
                        startDistance = distance
                    } else if (!changed) {
                        val ratio = distance / startDistance
                        if (ratio > PINCH_OUT || ratio < PINCH_IN) {
                            // Fingers apart means bigger posters, which means fewer of them.
                            val next = if (ratio > 1f) columns - 1 else columns + 1
                            onChange(next.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
                            changed = true
                        }
                    }
                    pressed.forEach { it.consume() }
                }
                down = event.changes.any { it.pressed }
            }
        }
    }

/**
 * The first title in the grid, without asking the pager to load a page for it — the detail pane
 * follows it, and a preview must never be the reason a page is fetched.
 */
private fun LazyPagingItems<VodItem>.peekFirstId(): Long? = itemSnapshotList.firstOrNull()?.id

/** The room between the crumb and the tools (`.tools` at 128 under a 102 dp header), as on Live TV. */
private val TOOLS_TOP = 26.dp

/** The rating's gold, `#ffcc4d` (the same on the poster chip). */
private val RatingGold = Color(0xFFFFCC4D)

private const val COLUMN_WIDTH_DP = 110
private const val WIDE_COLUMN_WIDTH_DP = 86
private const val WIDE_GRID_SHARE = 0.66f
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 8
private const val PINCH_OUT = 1.25f
private const val PINCH_IN = 0.8f
