package tv.own.owntv.mobile.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.content.EpisodeOrder
import tv.own.owntv.core.content.EpisodeTitles
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.metadata.HeadlineTitle
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.TitleNormalizer
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.trending.ProviderVariantParser
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ContentMenuSheet
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.SheetAction
import tv.own.owntv.mobile.ui.components.TmdbDetailsSheet
import tv.own.owntv.mobile.ui.components.downloadActionFor
import tv.own.owntv.mobile.ui.components.episodeDetails
import tv.own.owntv.mobile.ui.components.icon
import tv.own.owntv.mobile.ui.components.jsonList
import tv.own.owntv.mobile.ui.components.label
import tv.own.owntv.mobile.ui.components.onTap
import tv.own.owntv.mobile.ui.components.rememberAirDateLabel
import tv.own.owntv.mobile.ui.player.rememberResumeGate
import tv.own.owntv.mobile.ui.screens.home.durationText
import tv.own.owntv.mobile.ui.screens.home.formatRating
import tv.own.owntv.mobile.ui.screens.home.formatYear
import tv.own.owntv.mobile.ui.shell.LocalTitleChrome
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageChoiceRow
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStill
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.drawOuterRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.NumberFormat

/**
 * The series page: the backdrop across the top (lent to the shell), "Series · **category**",
 * the 36 / 800 title, the meta line (year · genres · N seasons · ★ rating, then the quality tags), two lines
 * of plot, Resume S1 · E3 · 12 min left · ♥ · ⬇ season · ⋯, the season tabs with their episode counts
 * (Specials after the numbered seasons), Grid | List and the order tool, then the episodes — rows with a
 * still, "3. Title", "date · length · Watched / time left" and the plot, or stills two a row.
 *
 * A tap plays an episode through the Resume playback setting; a hold opens its options. The main button
 * already says what it does (Resume at the saved place, or Play from the start), so it does not ask again.
 * Titles are the provider's without the show-and-code prefix, TMDB's where that leaves nothing (or first,
 * in TMDB-first mode), as on the TV.
 */
@Composable
internal fun SeriesPage(
    vm: DetailViewModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val show by vm.show.collectAsStateWithLifecycle()
    val page by vm.titlePage.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val seasons by vm.seasons.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val episodesFailure by vm.episodesFailure.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val episodeProgress by vm.episodeProgress.collectAsStateWithLifecycle()
    val completedIds by vm.completedIds.collectAsStateWithLifecycle()
    val seasonMeta by vm.seasonMeta.collectAsStateWithLifecycle()
    val nextUpId by vm.nextUpId.collectAsStateWithLifecycle()
    val hideWatched by vm.hideWatched.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val viewMode by vm.episodeViewMode.collectAsStateWithLifecycle()
    val seriesDownloads by vm.seriesDownloads.collectAsStateWithLifecycle()
    val episodeDownloads by vm.episodeDownloadStates.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val density = LocalDensity.current
    val meta = page.meta

    // The backdrop is the page's, drawn by the shell behind the header and the status bar (`.bdrop` 380).
    val titleChrome = LocalTitleChrome.current
    DisposableEffect(titleChrome) { onDispose { titleChrome?.backdropUrl = null } }
    SideEffect {
        titleChrome?.backdropUrl = show?.let { MetadataImages.backdrop(meta?.backdropPath, size = "w1280") ?: it.backdropUrl ?: it.posterUrl }
        titleChrome?.backdropHeight = 380.dp
        titleChrome?.backdropAlignment = BiasAlignment(0.4f, -0.6f)
    }
    val series = show ?: return

    val estimatedTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp
    var contentTop by remember { mutableStateOf<Dp?>(null) }
    val topGap = (CRUMB_TOP - (contentTop ?: estimatedTop)).coerceAtLeast(0.dp)
    val listState = rememberLazyListState()
    // The picture fades as the page scrolls up, so the episodes never sit over it.
    val fadePx = with(density) { 320.dp.toPx() }
    LaunchedEffect(titleChrome, listState) {
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) 0f else 1f - listState.firstVisibleItemScrollOffset / fadePx
        }.collect { titleChrome?.backdropAlpha = it.coerceIn(0f, 1f) }
    }

    // ⋯ is the grid's own series menu: the library's view model, pinned to All (nothing to move in here).
    val library: LibraryViewModel = koinViewModel()
    DisposableEffect(library) {
        library.select(LibraryTab.SERIES)
        library.lock(LiveKey.All)
        onDispose { library.unlock() }
    }
    val categories by library.categories.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var orderOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<EpisodeEntity?>(null) }
    val resumeGate = rememberResumeGate()

    val activeSeason = season ?: seasons.firstOrNull()
    val seasonEpisodes = remember(episodes, activeSeason, order) {
        episodes.filter { it.seasonNumber == activeSeason }
            .sortedBy { it.episodeNumber }
            .let { if (order.episodesDescending) it.reversed() else it }
    }
    val shown = remember(seasonEpisodes, hideWatched, completedIds) {
        if (hideWatched) seasonEpisodes.filterNot { it.id in completedIds } else seasonEpisodes
    }
    val counts = remember(episodes) { episodes.groupingBy { it.seasonNumber }.eachCount() }
    // What the main button plays: the next-up episode (resumed when started), else the first one.
    val target = remember(episodes, nextUpId) {
        nextUpId?.let { id -> episodes.firstOrNull { it.id == id } } ?: episodes.minWithOrNull(EpisodeOrder.episodes)
    }
    val targetProgress = target?.let { episodeProgress[it.id] }
        ?.takeIf { it.positionMs > 0 && it.durationMs > 0 && target.id !in completedIds }

    val resources = LocalContext.current.resources
    // The provider's show name often carries its own prefix ("4K-NF - Minerva Academy (2026) (IT)") while the
    // episodes repeat only the plain name ("Minerva Academy - S01E01 - …"), so the plain one is stripped too.
    val plainShow = remember(series.name) { TitleNormalizer.normalize(series.name).query }
    val titleOf: (EpisodeEntity, MetadataCacheEntity?) -> String = { ep, epMeta ->
        val tmdb = epMeta?.title?.takeIf { it.isNotBlank() }
        val own = EpisodeTitles.clean(ep.name, series.name)?.let { EpisodeTitles.clean(it, plainShow) }
        (if (page.tmdbWins) tmdb ?: own else own ?: tmdb) ?: resources.getString(R.string.player_episode_number, ep.episodeNumber)
    }
    val secondsLeft: (EpisodeEntity) -> Long? = { ep ->
        episodeProgress[ep.id]?.takeIf { it.positionMs > 0 && it.durationMs > 0 && ep.id !in completedIds }
            ?.let { ((it.durationMs - it.positionMs) / 1000).coerceAtLeast(60) }
    }
    val durationSecs: (EpisodeEntity) -> Long? = { ep ->
        ep.durationSecs?.takeIf { it > 0 }?.toLong() ?: episodeProgress[ep.id]?.durationMs?.takeIf { it > 0 }?.let { it / 1000 }
    }
    val progressOf: (EpisodeEntity) -> Float? = { ep ->
        episodeProgress[ep.id]?.takeIf { it.positionMs > 0 && it.durationMs > 1 && ep.id !in completedIds }
            ?.let { it.positionMs.toFloat() / it.durationMs }
    }
    val play: (EpisodeEntity) -> Unit = { ep ->
        resumeGate(episodeProgress[ep.id]?.positionMs ?: 0L) { vm.playEpisode(ep.id, it, onPlay) }
    }
    val grid = viewMode == SettingsRepository.VodViewMode.GRID

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    contentTop = with(density) { coords.positionInWindow().y.toDp() }
                }
            },
    ) {
        item(key = "header", contentType = "header") {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = topGap)) {
                SeriesHeader(series, page, seasons)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (target != null) {
                        val code = stringResource(R.string.content_season_episode, target.seasonNumber, target.episodeNumber)
                        StageButton(
                            onClick = { vm.playEpisode(target.id, targetProgress?.positionMs ?: 0L, onPlay) },
                            text = stringResource(if (targetProgress != null) R.string.content_resume_episode else R.string.content_play_episode, code),
                            trailing = secondsLeft(target)?.let {
                                stringResource(R.string.content_epg_bits_separator).trimStart() + stringResource(R.string.home_time_left, durationText(it / 60))
                            },
                            icon = MobileIcons.PlayArrow,
                            style = StageButtonStyle.PRIMARY,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    val favourite = stringResource(if (favorite) R.string.content_remove_favourite else R.string.content_add_favourite)
                    StageButton(
                        onClick = vm::toggleFavorite,
                        icon = if (favorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
                        modifier = Modifier.semantics { contentDescription = favourite },
                    )
                    // ⬇ the listed season, as the TV's "Download season"; once its episodes are queued it says
                    // what is left to do with them (retry a failure, delete what is saved).
                    val seasonIds = remember(seasonEpisodes) { seasonEpisodes.map { it.id }.toSet() }
                    val seasonRows = remember(seriesDownloads, seasonIds) { seriesDownloads.filter { it.itemId in seasonIds } }
                    val download = downloadActionFor(seasonRows)
                    val downloadLabel = download.label(stringResource(R.string.content_download_season))
                    StageButton(
                        onClick = download.onTap(
                            onDownload = { activeSeason?.let(vm::downloadSeason) },
                            onRetry = { vm.retryDownloads(seasonRows) },
                            onDelete = { vm.deleteDownloads(seasonRows) },
                        ),
                        icon = download.icon(),
                        style = if (download.deletes) StageButtonStyle.DANGER else StageButtonStyle.SECONDARY,
                        modifier = Modifier.semantics { contentDescription = downloadLabel },
                    )
                    val more = stringResource(R.string.content_key_options)
                    StageButton(onClick = { menuOpen = true }, icon = MobileIcons.MoreHoriz, modifier = Modifier.semantics { contentDescription = more })
                }
                SeasonBar(
                    seasons = seasons,
                    counts = counts,
                    selected = activeSeason,
                    onSelect = vm::selectSeason,
                    grid = grid,
                    onView = vm::setEpisodeViewMode,
                    orderOpen = orderOpen,
                    onOrder = { orderOpen = true },
                )
                if (loading && episodes.isEmpty()) {
                    // A provider hands its episodes over on open: the spinner until the first ones arrive.
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (shown.isEmpty()) {
                    // Only a show with nothing stored can carry a failure; "hide watched" emptying a season cannot.
                    val failure = episodesFailure?.takeIf { episodes.isEmpty() }
                    Text(
                        if (failure != null) stringResource(R.string.content_episodes_load_failed, failure)
                        else stringResource(R.string.content_no_episodes),
                        style = stageText(13f, 500), color = palette.muted, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
        if (grid) {
            items(shown.chunked(2), key = { row -> "g_" + row.first().id }, contentType = { "grid" }) { row ->
                Row(
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { ep ->
                        val epMeta = seasonMeta[ep.id]
                        EpisodeTile(
                            ep = ep,
                            title = titleOf(ep, epMeta),
                            line = episodeLine(rememberAirDateLabel(ep, epMeta), durationSecs(ep), secondsLeft(ep), watched = false),
                            still = episodeStillUrl(epMeta, series),
                            watched = ep.id in completedIds,
                            progress = progressOf(ep),
                            current = ep.id == target?.id,
                            onClick = { play(ep) },
                            onLongClick = { optionsFor = ep },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            item(key = "list_top") { Spacer(Modifier.height(8.dp)) }
            items(shown, key = { it.id }, contentType = { "row" }) { ep ->
                val epMeta = seasonMeta[ep.id]
                val watched = ep.id in completedIds
                EpisodeRow(
                    number = episodeNumber(ep.episodeNumber),
                    title = titleOf(ep, epMeta),
                    line = episodeLine(rememberAirDateLabel(ep, epMeta), durationSecs(ep), secondsLeft(ep), watched),
                    plot = (if (page.tmdbWins) epMeta?.overview ?: ep.plot else ep.plot ?: epMeta?.overview)?.takeIf { it.isNotBlank() },
                    still = episodeStillUrl(epMeta, series),
                    watched = watched,
                    progress = progressOf(ep),
                    current = ep.id == target?.id,
                    onClick = { play(ep) },
                    onLongClick = { optionsFor = ep },
                )
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(22.dp)) }
    }

    if (menuOpen) {
        VodMenu(
            item = VodItem(
                id = series.id,
                name = series.name,
                posterUrl = series.posterUrl,
                year = series.year,
                rating = series.rating,
                sourceId = series.sourceId,
                categoryId = series.categoryId,
            ),
            tab = LibraryTab.SERIES,
            selected = LiveKey.All,
            isFavorite = favorite,
            originName = "",
            categories = categories,
            vm = library,
            onDismiss = { menuOpen = false },
        )
    }
    if (orderOpen) {
        EpisodeOrderSheet(
            order = order,
            hideWatched = hideWatched,
            onHideWatched = { vm.setHideWatched(!hideWatched) },
            onChange = vm::setOrder,
            onDismiss = { orderOpen = false },
        )
    }
    optionsFor?.let { ep ->
        val epMeta = seasonMeta[ep.id]
        EpisodeOptions(
            episode = ep,
            title = stringResource(R.string.content_episode_numbered, episodeNumber(ep.episodeNumber), titleOf(ep, epMeta)),
            subtitle = HeadlineTitle.of(series.name) + stringResource(R.string.content_epg_bits_separator) +
                if (ep.seasonNumber == 0) stringResource(R.string.content_season_specials) else stringResource(R.string.content_season, ep.seasonNumber),
            still = episodeStillUrl(epMeta, series),
            watched = ep.id in completedIds,
            downloadRows = listOfNotNull(episodeDownloads[ep.id]),
            vm = vm,
            onPlay = onPlay,
            onDismiss = { optionsFor = null },
        )
    }
}

/** Crumb, title, meta line and plot — the part of the header above the buttons. */
@Composable
private fun SeriesHeader(series: SeriesEntity, page: DetailViewModel.TitlePage, seasons: List<Int>) {
    val palette = LocalStagePalette.current
    val meta = page.meta
    val tmdbWins = page.tmdbWins
    // "Series · Crunchyroll" (`.crumb`): where it sits, the category in the accent.
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.common_nav_series))
            page.categoryName?.let {
                append(stringResource(R.string.content_epg_bits_separator))
                withStyle(SpanStyle(color = stageAccent.accent, fontWeight = FontWeight.Bold)) { append(it) }
            }
        },
        style = stageText(13f, 500),
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        HeadlineTitle.of(series.name),
        style = stageText(36f, 800, (-1f / 36f).em, lineHeight = 1.02f),
        color = palette.text,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
    // Specials are not counted as a season, as on Home and the TV.
    val numbered = seasons.count { it > 0 }
    Box(Modifier.padding(top = 8.dp)) {
        MovieMeta(
            parts = listOfNotNull(
                (series.year ?: meta?.year)?.takeIf { it > 0 }?.let(::formatYear),
                jsonList(meta?.genresJson).take(3).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.content_epg_bits_separator)),
                numbered.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.home_trending_seasons, it, it) },
            ),
            rating = listOf(meta?.rating, series.rating).let { if (tmdbWins) it else it.reversed() }
                .firstOrNull { it != null && it > 0 }?.let(::formatRating),
            tags = ProviderVariantParser.displaySignals(series.name).let { listOfNotNull(it.quality.label) + it.capabilities },
            size = 12.5f,
        )
    }
    listOf(meta?.overview, series.plot).let { if (tmdbWins) it else it.reversed() }.firstOrNull { !it.isNullOrBlank() }?.let {
        Text(
            it,
            style = stageText(12.5f, 500, lineHeight = 1.5f),
            color = palette.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The season tabs (13 / 800 and the 3 dp accent line under the chosen one, 13 / 700 muted otherwise, the
 * episode count 11 dim after each), then Grid | List and the order tool, over a 1 dp line.
 */
@Composable
private fun SeasonBar(
    seasons: List<Int>,
    counts: Map<Int, Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    grid: Boolean,
    onView: (SettingsRepository.VodViewMode) -> Unit,
    orderOpen: Boolean,
    onOrder: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Box(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawRect(palette.ink.copy(alpha = 0.1f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                seasons.forEach { number ->
                    val on = number == selected
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .stageClickable(interaction, { onSelect(number) }, null, Role.Tab)
                            .width(androidx.compose.foundation.layout.IntrinsicSize.Max),
                    ) {
                        Text(
                            buildAnnotatedString {
                                append(if (number == 0) stringResource(R.string.content_season_specials) else stringResource(R.string.content_season, number))
                                counts[number]?.let {
                                    append(" ")
                                    withStyle(SpanStyle(color = palette.dim, fontSize = stageText(11f, 700).fontSize)) { append(NumberFormat.getIntegerInstance().format(it)) }
                                }
                            },
                            style = stageText(13f, if (on) 800 else 700),
                            color = if (on) palette.text else palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) accent else Color.Transparent))
                    }
                }
            }
            Row(
                Modifier.padding(start = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageSegmented(
                    options = listOf(stringResource(R.string.settings_view_grid), stringResource(R.string.settings_view_list)),
                    selected = if (grid) 0 else 1,
                    onSelect = { onView(if (it == 0) SettingsRepository.VodViewMode.GRID else SettingsRepository.VodViewMode.LIST) },
                    icons = listOf(MobileIcons.GridView, MobileIcons.FormatListBulleted),
                    iconOnly = true,
                )
                StageTool(
                    onClick = onOrder,
                    icon = MobileIcons.Sort,
                    on = orderOpen,
                    contentDescription = stringResource(R.string.content_episode_order),
                )
            }
        }
    }
}

/** `.chrow` 98 high: the 120 dp still (10 corners), "3. Title" 13.5 / 700, the line, two lines of plot 11 dim. */
@Composable
private fun EpisodeRow(
    number: String,
    title: String,
    line: String,
    plot: String?,
    still: String?,
    watched: Boolean,
    progress: Float?,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalStagePalette.current
    StageRow(
        modifier = Modifier.padding(horizontal = 12.dp),
        onClick = onClick,
        onLongClick = onLongClick,
        current = current,
        height = 98.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                StageStill(still, Modifier.width(120.dp), progress = progress, radius = 10.dp, dimmed = watched)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.content_episode_numbered, number, title),
                        style = stageText(13.5f, 700),
                        color = palette.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(line, style = stageText(12f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                    plot?.let {
                        Text(it, style = stageText(11f, 500), color = palette.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                    }
                }
            }
        }
    }
}

/** A grid still: the number tag top left, ✓ top right when watched, the 2 dp focus ring on the current one. */
@Composable
private fun EpisodeTile(
    ep: EpisodeEntity,
    title: String,
    line: String,
    still: String?,
    watched: Boolean,
    progress: Float?,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    val focus = stageAccent.focus
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.stageClickable(interaction, onClick, onLongClick)) {
        StageStill(
            still,
            if (current) Modifier.drawWithContent { drawContent(); drawOuterRing(focus, 2.dp.toPx(), 12.dp.toPx()) } else Modifier,
            progress = progress,
            dimmed = watched,
        ) {
            Text(
                episodeNumber(ep.episodeNumber),
                style = stageText(9.5f, 800, 0.05.em),
                color = Color.White,
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xD9080C0E))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (watched) StageIcon(MobileIcons.Check, 16.dp, Color.White, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(title, style = stageText(12.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(line, style = stageText(10.5f, 500), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Jan 21, 2024 · 24 min · 12 min left", or "… · Watched" in the list: only the parts that are known. */
@Composable
private fun episodeLine(aired: String?, durationSecs: Long?, secondsLeft: Long?, watched: Boolean): String = listOfNotNull(
    aired,
    durationSecs?.takeIf { it >= 60 }?.let { durationText(it / 60) },
    secondsLeft?.let { stringResource(R.string.home_time_left, durationText(it / 60)) },
    if (watched) stringResource(R.string.content_episode_watched) else null,
).joinToString(stringResource(R.string.content_epg_bits_separator))

/** Seasons and episodes each oldest or newest first, remembered for this series, and Hide watched. */
@Composable
private fun EpisodeOrderSheet(
    order: DetailViewModel.SeriesOrder,
    hideWatched: Boolean,
    onHideWatched: () -> Unit,
    onChange: (seasonsDescending: Boolean, episodesDescending: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.content_episode_order)
    val choices = listOf(stringResource(R.string.content_oldest_first), stringResource(R.string.content_newest_first))
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.content_episode_order_hint))
        StageGroupLabel(stringResource(R.string.content_seasons))
        StageChoiceRow(choices, if (order.seasonsDescending) 1 else 0, { onChange(it == 1, order.episodesDescending) })
        StageGroupLabel(stringResource(R.string.content_episodes))
        StageChoiceRow(choices, if (order.episodesDescending) 1 else 0, { onChange(order.seasonsDescending, it == 1) })
        StageGroupLabel(stringResource(R.string.content_menu_group_show))
        StageSettingRow(
            title = stringResource(R.string.content_hide_watched_episodes),
            icon = MobileIcons.VisibilityOff,
            onClick = onHideWatched,
            role = Role.Switch,
            minHeight = 48.dp,
        ) { StageSwitch(hideWatched) }
    }
}

/** Which follow-up the episode sheet handed off to. Only ever one at a time. */
private enum class EpisodeDialog { DETAILS, SUBTITLES }

/**
 * WATCH (Play from start, Play in external player), LIBRARY (Download, Mark as
 * watched), DETAILS (Episode details, Update details, Delete OpenSubtitles subtitles) — in the order of
 * Settings › Long-press menus, the keys the TV's. Details without a TMDB match and Delete subtitles with
 * none downloaded stay in their place, dimmed. The still and "3. Title" / "Show · Season 1" on top.
 */
@Composable
private fun EpisodeOptions(
    episode: EpisodeEntity,
    title: String,
    subtitle: String,
    still: String?,
    watched: Boolean,
    downloadRows: List<tv.own.owntv.core.database.entity.DownloadEntity>,
    vm: DetailViewModel,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sheetOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<EpisodeDialog?>(null) }
    LaunchedEffect(sheetOpen, dialog) { if (!sheetOpen && dialog == null) onDismiss() }

    var meta by remember { mutableStateOf<MetadataCacheEntity?>(null) }
    LaunchedEffect(episode.id) { if (mode.enrich) meta = vm.episodeMeta(episode) }
    var hasSubtitles by remember { mutableStateOf(false) }
    LaunchedEffect(episode.id) { hasSubtitles = vm.downloadedSubtitles(episode).isNotEmpty() }
    val download = downloadActionFor(downloadRows)

    if (sheetOpen) {
        val actions = buildList {
            add(SheetAction("play_start", stringResource(R.string.content_play_from_start), MobileIcons.PlayOutline, group = GROUP_WATCH) {
                vm.playEpisode(episode.id, 0L, onPlay)
            })
            add(SheetAction("play_external", stringResource(R.string.content_play_external_short), MobileIcons.OpenInNew, group = GROUP_WATCH) {
                vm.playExternal(episode) {}
            })
            add(
                SheetAction(
                    key = "download",
                    label = download.label(stringResource(R.string.content_download)),
                    icon = download.icon(),
                    destructive = download.deletes,
                    group = GROUP_LIBRARY,
                    onClick = download.onTap(
                        onDownload = { vm.download(episode) },
                        onRetry = { vm.retryDownloads(downloadRows) },
                        onDelete = { vm.deleteDownloads(downloadRows) },
                    ),
                ),
            )
            add(
                SheetAction(
                    key = "mark_watched",
                    label = stringResource(if (watched) R.string.content_mark_unwatched else R.string.content_mark_watched),
                    icon = MobileIcons.Check,
                    group = GROUP_LIBRARY,
                    onClick = { vm.setEpisodeWatched(episode, !watched) },
                ),
            )
            add(
                SheetAction(
                    key = "tmdb_details",
                    label = stringResource(R.string.content_episode_details),
                    icon = MobileIcons.Info,
                    group = GROUP_DETAILS,
                    enabled = mode.enrich && meta != null,
                    onClick = { dialog = EpisodeDialog.DETAILS },
                ),
            )
            if (mode.enrich) {
                add(
                    SheetAction(
                        key = "refetch_tmdb",
                        label = stringResource(R.string.content_refetch_tmdb),
                        icon = MobileIcons.Refresh,
                        group = GROUP_DETAILS,
                        onClick = {
                            Toast.makeText(context, R.string.content_researching_tmdb, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                vm.clearEpisodeMeta(episode)
                                meta = vm.episodeMeta(episode)
                            }
                        },
                    ),
                )
            }
            add(
                SheetAction(
                    key = "delete_subtitles",
                    label = stringResource(R.string.content_delete_subtitles),
                    icon = MobileIcons.Subtitles,
                    group = GROUP_DETAILS,
                    enabled = hasSubtitles,
                    onClick = { dialog = EpisodeDialog.SUBTITLES },
                ),
            )
        }
        ContentMenuSheet(
            menu = ContentMenu.EPISODE,
            title = title,
            actions = actions,
            onDismiss = { sheetOpen = false },
            groupLabels = mapOf(
                GROUP_WATCH to stringResource(R.string.content_menu_group_watch),
                GROUP_LIBRARY to stringResource(R.string.content_menu_group_library),
                GROUP_DETAILS to stringResource(R.string.content_menu_group_details),
            ),
            subtitle = subtitle,
            leading = {
                AsyncImage(
                    still,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp, 40.dp).clip(RoundedCornerShape(7.dp)).background(LocalStagePalette.current.cardSolid),
                )
            },
        )
    }

    when (dialog) {
        EpisodeDialog.DETAILS -> TmdbDetailsSheet(
            details = episodeDetails(episode, meta, mode.tmdbWins),
            onDismiss = { dialog = null },
        )
        EpisodeDialog.SUBTITLES -> DeleteSubtitlesSheet(
            load = { vm.downloadedSubtitles(episode).map { it.cacheId to (it.languageName ?: it.fileName) } },
            onDelete = { cacheId -> vm.deleteSubtitle(cacheId) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/**
 * An episode's picture: TMDB's still, else the show's backdrop (16:9 like the still), else its poster — the
 * TV's chain. The provider stores no episode image, so the show's art only stands in for a missing still.
 */
private fun episodeStillUrl(meta: MetadataCacheEntity?, series: SeriesEntity): String? =
    MetadataImages.backdrop(meta?.backdropPath, size = "w300")
        ?: series.backdropUrl?.takeIf { it.isNotBlank() } ?: series.posterUrl?.takeIf { it.isNotBlank() }

/** An episode number as a plain figure in the user's digits, never grouped ("1000", not "1,000"). */
private fun episodeNumber(n: Int): String = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(n)

/** Where the crumb starts, from the top of the screen (`top: 200px` of the frame). */
private val CRUMB_TOP = 200.dp

private const val GROUP_WATCH = 0
private const val GROUP_LIBRARY = 1
private const val GROUP_DETAILS = 3
