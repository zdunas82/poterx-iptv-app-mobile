package tv.own.owntv.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.metadata.MetadataCast
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.TrailerKeys
import tv.own.owntv.core.trending.ProviderVariantParser
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.library.playTrailer
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageCastRow
import tv.own.owntv.mobile.ui.stage.StageEyebrow
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StagePoster
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTitleArt
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/*
 * Now Trending, Stage — the TV's hero turned upright. The backdrop is the page's,
 * drawn by the shell behind the header (see HomeChrome); this file draws what sits over it: the eyebrow,
 * the title art, one meta line, three lines of plot, Play · Trailer · versions · ⓘ · ♥ and the pager dots.
 * A sideways swipe changes the title, the dots say where you are, and every 10 s the next one comes — not
 * while a finger is on it. "Why this item appears" lives in the ⓘ sheet now, not on the card.
 */

/** "#3 in Trending · Movies". */
@Composable
fun trendingRank(item: TrendingHomeItem): String = stringResource(
    R.string.home_trending_rank,
    item.snapshot.trendingRank,
    stringResource(if (item is TrendingHomeItem.Movie) R.string.common_nav_movies else R.string.common_nav_series),
)

/** The backdrop the page shows behind the hero: TMDB's, else the playlist's own picture. */
fun trendingBackdrop(item: TrendingHomeItem): String? =
    MetadataImages.backdrop(item.snapshot.backdropPath, size = "w1280")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.backdropUrl ?: item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.backdropUrl ?: item.series.posterUrl
        }

private fun trendingPoster(item: TrendingHomeItem): String? =
    MetadataImages.poster(item.snapshot.posterPath, size = "w500")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.posterUrl
        }

/** The first trailer, when TMDB has one. */
fun trendingTrailer(item: TrendingHomeItem): String? = TrailerKeys.split(item.snapshot.trailerKey).firstOrNull()

@Composable
fun TrendingHero(
    items: List<TrendingHomeItem>,
    activeIndex: Int,
    onIndexChange: (Int) -> Unit,
    extras: HomeViewModel.TrendingExtras,
    seasonCount: Int?,
    favourite: Boolean,
    unavailable: Boolean,
    paused: Boolean,
    topGap: Dp,
    onPlay: () -> Unit,
    onVersions: () -> Unit,
    onInfo: () -> Unit,
    onFavourite: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = items.getOrNull(activeIndex) ?: return
    val snapshot = item.snapshot
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    var touching by remember { mutableStateOf(false) }
    val index by rememberUpdatedState(activeIndex)

    // The next title, every 10 s; a touch or an open sheet ([paused]) holds it, and letting go starts
    // the 10 s again — the title never changes under a menu that is about it.
    LaunchedEffect(activeIndex, touching, paused, items.size) {
        if (touching || paused || items.size < 2) return@LaunchedEffect
        delay(INTERVAL_MS)
        onIndexChange((activeIndex + 1) % items.size)
    }

    // Warm the next title's backdrop, so the change swaps a cached picture rather than starting a load.
    val appContext = context.applicationContext
    LaunchedEffect(activeIndex, items) {
        if (items.size < 2) return@LaunchedEffect
        trendingBackdrop(items[(activeIndex + 1) % items.size])?.let { url ->
            SingletonImageLoader.get(appContext).enqueue(ImageRequest.Builder(appContext).data(url).size(1280, 720).build())
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            // Nothing is consumed, so the buttons still work; this only stops the clock.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touching = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    touching = false
                }
            }
            .pointerInput(items.size) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragged) > SWIPE_SLOP_PX) {
                            onIndexChange((index + (if (dragged < 0) 1 else -1) + items.size) % items.size)
                        }
                    },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onMenu() }) }
            .padding(start = 18.dp, end = 18.dp, top = topGap),
    ) {
        StageEyebrow(trendingRank(item))
        Spacer(Modifier.height(10.dp))
        // The title art when TMDB has it; the 40 / 800 title when it has none or it fails to load.
        var logoFailed by remember(extras.logoUrl) { mutableStateOf(false) }
        val logo = extras.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }
        if (logo != null) {
            StageTitleArt(
                url = logo,
                contentDescription = snapshot.localizedTitle,
                onFailed = { logoFailed = true },
                modifier = Modifier.width(260.dp).height(76.dp),
            )
        } else {
            Text(
                snapshot.localizedTitle,
                style = stageText(40f, 800, (-1.2f / 40f).em, lineHeight = 1.05f),
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        HeroMeta(item, extras.genres.take(2).joinToString(", "), seasonCount, size = 13f)
        snapshot.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(10.dp))
            Text(
                overview,
                style = stageText(13f, 500, lineHeight = 1.5f),
                color = palette.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        // When the row cannot hold every label ("Open episodes", German), Trailer gives up its word and
        // becomes a round button; when even that does not fit (UI zoom 120 %) it leaves the row — it stays
        // in the hold menu — so ⓘ and ♥ always fit and nothing is squeezed to an empty box (TV #4).
        // The row's own width, not the window's: UI zoom scales the density, not screenWidthDp.
        val measurer = rememberTextMeasurer()
        val buttonStyle = stageText(14f, 700)
        val playLabel = stringResource(if (item is TrendingHomeItem.Movie) R.string.home_trending_play else R.string.home_trending_open_episodes)
        val trailerWord = stringResource(R.string.home_trending_trailer)
        val versionsCount = extras.versions.takeIf { it > 1 }?.let { formatCount(it) }
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val (trailerShown, trailerFits) = with(density) {
                fun w(text: String) = measurer.measure(text, buttonStyle).size.width.toDp()
                val labelled = 36.dp + 16.dp + 7.dp
                val others = labelled + w(playLabel) +
                    (versionsCount?.let { 28.dp + 16.dp + 7.dp + w(it) } ?: 44.dp) + 44.dp * 2 + 8.dp * 4
                (others + 44.dp <= maxWidth) to (others + labelled + w(trailerWord) <= maxWidth)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The labelled buttons share what ⓘ and ♥ leave, so those two always fit (TV #4); a long
                // Trailer label is the one that shortens.
                Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isMovie = item is TrendingHomeItem.Movie
                    StageButton(
                        onClick = onPlay,
                        text = playLabel,
                        icon = if (isMovie) MobileIcons.PlayArrow else MobileIcons.Series,
                        style = StageButtonStyle.PRIMARY,
                    )
                    trendingTrailer(item)?.takeIf { trailerShown }?.let { key ->
                        val trailerLabel = stringResource(R.string.home_trending_trailer)
                        StageButton(
                            onClick = { context.playTrailer(key) },
                            modifier = Modifier.semantics { contentDescription = trailerLabel },
                            text = trailerLabel.takeIf { trailerFits },
                            icon = MobileIcons.PlayCircle,
                        )
                    }
                }
                // Versions keeps its own width like ⓘ and ♥: when the estimate above is short, only Trailer's word
                // shortens — the count was the one squeezed to "…".
                val versionsLabel = stringResource(R.string.home_trending_all_versions)
                StageButton(
                    onClick = onVersions,
                    modifier = Modifier.semantics { contentDescription = versionsLabel },
                    icon = MobileIcons.Layers,
                    trailing = versionsCount,
                )
                val infoLabel = stringResource(R.string.home_trending_more_details)
                StageButton(onClick = onInfo, modifier = Modifier.semantics { contentDescription = infoLabel }, icon = MobileIcons.Info)
                val favouriteLabel = stringResource(if (favourite) R.string.content_remove_favourite else R.string.content_add_favourite)
                StageButton(
                    onClick = onFavourite,
                    modifier = Modifier.semantics { contentDescription = favouriteLabel },
                    icon = if (favourite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PagerDots(count = items.size, active = activeIndex)
        if (unavailable) {
            Text(
                stringResource(R.string.home_trending_unavailable),
                style = stageText(12f, 600),
                color = palette.danger,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "2026 · Animation, Family · 1 h 42 min · ★ 8.4" and the quality tag; the rating in gold. */
@Composable
private fun HeroMeta(item: TrendingHomeItem, genres: String, seasonCount: Int?, size: Float, maxLines: Int = 1) {
    val palette = LocalStagePalette.current
    val snapshot = item.snapshot
    val runtime = when (item) {
        is TrendingHomeItem.Movie -> item.movie.durationSecs?.takeIf { it >= 60 }?.let { durationText(it / 60L) }
        is TrendingHomeItem.Series -> seasonCount?.let { pluralStringResource(R.plurals.home_trending_seasons, it, it) }
    }
    val parts = listOfNotNull(snapshot.year?.let { formatYear(it) }, genres.ifBlank { null }, runtime)
    val rating = snapshot.rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it) }
    val text = buildAnnotatedString {
        append(parts.joinToString(" · "))
        if (rating != null) {
            if (parts.isNotEmpty()) append(" · ")
            withStyle(SpanStyle(color = RatingGold)) { append(rating) }
        }
    }
    val quality = ProviderVariantParser.displaySignals(snapshot.providerRawName).quality.label
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = stageText(size, 600),
            color = palette.muted,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        quality?.let { StageTag(it) }
    }
}

/** The pager: one dot per title, the current one 22 wide in the accent. */
@Composable
private fun PagerDots(count: Int, active: Int) {
    val accent = stageAccent.accent
    val ink = LocalStagePalette.current.ink
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == active) 22.dp else 12.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i == active) accent else ink.copy(alpha = 0.22f)),
            )
        }
    }
}

/**
 * Trending layout = Posters: the eyebrow and the 32 / 800 title of the poster at the front of the
 * row, then the row itself — the first poster in view is the one the line above names. Lower down the page
 * ([withTitle] off) it is just the row under its heading, as the TV draws it there.
 */
@Composable
fun TrendingPosters(
    items: List<TrendingHomeItem>,
    topGap: Dp,
    withTitle: Boolean,
    onShown: (Int) -> Unit,
    onActivate: (TrendingHomeItem) -> Unit,
    onMenu: (TrendingHomeItem) -> Unit,
) {
    val palette = LocalStagePalette.current
    val rowState = rememberLazyListState()
    val shown by remember { derivedStateOf { rowState.firstVisibleItemIndex } }
    LaunchedEffect(shown) { onShown(shown) }
    val item = items.getOrNull(shown) ?: return
    Column(Modifier.padding(top = topGap)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            if (withTitle) {
                StageEyebrow(trendingRank(item))
                Text(
                    item.snapshot.localizedTitle,
                    style = stageText(32f, 800, (-1f / 32f).em, lineHeight = 1.1f),
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
            Text(
                stringResource(R.string.home_row_now_trending),
                style = stageText(17f, 800),
                color = palette.text,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.stableKey }) { i, poster ->
                StagePoster(
                    imageUrl = when (poster) {
                        is TrendingHomeItem.Movie -> poster.movie.posterUrl
                        is TrendingHomeItem.Series -> poster.series.posterUrl
                    } ?: trendingPoster(poster),
                    title = poster.snapshot.localizedTitle,
                    rating = poster.snapshot.rating?.takeIf { it > 0 }?.let { formatRating(it) },
                    selected = withTitle && i == shown,
                    onClick = { onActivate(poster) },
                    onLongClick = { onMenu(poster) },
                    modifier = Modifier.width(PosterWidth),
                )
            }
        }
    }
}

/**
 * Hold on the hero: WATCH (Play, Trailer, All versions), LIBRARY (favourites, download) and
 * DETAILS (More details, Why this title is here — both open the ⓘ sheet, which holds the why).
 */
@Composable
fun TrendingMenuSheet(
    item: TrendingHomeItem,
    versions: Int,
    favourite: Boolean,
    onPlay: () -> Unit,
    onVersions: () -> Unit,
    onFavourite: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isMovie = item is TrendingHomeItem.Movie
    val title = item.snapshot.localizedTitle
    fun run(action: () -> Unit) {
        action()
        onDismiss()
    }
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(
            title = title,
            subtitle = listOfNotNull(item.snapshot.year?.let { formatYear(it) }, trendingRank(item)).joinToString(" · "),
            leading = {
                AsyncImage(
                    trendingPoster(item),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp, 50.dp).clip(RoundedCornerShape(6.dp)),
                )
            },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_watch))
        StageMenuItem(
            label = stringResource(if (isMovie) R.string.home_trending_play else R.string.home_trending_open_episodes),
            onClick = { run(onPlay) },
            icon = if (isMovie) MobileIcons.PlayOutline else MobileIcons.Series,
        )
        trendingTrailer(item)?.let { key ->
            StageMenuItem(
                label = stringResource(R.string.home_trending_trailer),
                onClick = { run { context.playTrailer(key) } },
                icon = MobileIcons.PlayCircle,
            )
        }
        StageMenuItem(
            label = stringResource(R.string.home_trending_all_versions),
            onClick = { run(onVersions) },
            icon = MobileIcons.Layers,
            count = versions.takeIf { it > 1 }?.let { formatCount(it) },
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_library))
        StageMenuItem(
            label = stringResource(if (favourite) R.string.content_remove_favourite else R.string.content_add_favourite),
            onClick = { run(onFavourite) },
            icon = if (favourite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
        )
        StageMenuItem(
            label = stringResource(if (isMovie) R.string.content_download else R.string.content_download_all_episodes),
            onClick = { run(onDownload) },
            icon = MobileIcons.Download,
        )
        StageGroupLabel(stringResource(R.string.content_menu_group_details))
        StageMenuItem(label = stringResource(R.string.home_trending_more_details), onClick = { run(onInfo) }, icon = MobileIcons.Info)
        StageMenuItem(label = stringResource(R.string.home_trending_why_menu), onClick = { run(onInfo) }, icon = MobileIcons.AutoAwesome)
    }
}

/**
 * The ⓘ sheet: poster, title, the meta line with the tags, the genres in the accent, the plot, the
 * cast, and "Why this item appears" — the sentence that used to sit on the hero card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendingInfoSheet(
    item: TrendingHomeItem,
    genres: List<String>,
    seasonCount: Int?,
    preferredLanguage: String,
    resolveDetails: suspend (TrendingHomeItem) -> HomeViewModel.TrendingDetails,
    onDismiss: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val snapshot = item.snapshot
    val details by produceState<HomeViewModel.TrendingDetails?>(null, item) { value = resolveDetails(item) }
    val meta = details?.cache
    val tmdbWins = details?.tmdbWins == true
    val providerPlot = when (item) {
        is TrendingHomeItem.Movie -> item.movie.plot
        is TrendingHomeItem.Series -> item.series.plot
    }?.takeIf { it.isNotBlank() }
    val tmdbPlot = meta?.overview?.takeIf { it.isNotBlank() } ?: snapshot.overview?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) tmdbPlot ?: providerPlot else providerPlot ?: tmdbPlot
    val cast = remember(meta?.castJson) { MetadataCast.parse(meta?.castJson) }
    val signals = ProviderVariantParser.displaySignals(snapshot.providerRawName)
    val tags = listOfNotNull(signals.quality.label) + signals.capabilities

    MobileBottomSheet(onDismissRequest = onDismiss, title = snapshot.localizedTitle) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 3 / 4).dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    trendingPoster(item),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(70.dp, 105.dp).clip(RoundedCornerShape(10.dp)).background(palette.cardSolid),
                )
                Column {
                    Text(snapshot.localizedTitle, style = stageText(19f, 800), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        HeroMeta(item, genres.joinToString(" · "), seasonCount, size = 12f, maxLines = 2)
                        tags.drop(if (signals.quality.label != null) 1 else 0).forEach { StageTag(it) }
                    }
                    if (genres.isNotEmpty()) {
                        Text(
                            genres.joinToString(" · "),
                            style = stageText(12f, 700),
                            color = stageAccent.accent,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            plot?.let {
                Text(it, style = stageText(12.5f, 500, lineHeight = 1.5f), color = palette.body, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp))
            }
            if (cast.isNotEmpty()) {
                StageCastRow(cast, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.padding(bottom = 14.dp))
            }
            StageGroupLabel(stringResource(R.string.home_trending_why_label))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val whyStyle = stageText(12f, 500, lineHeight = 1.45f)
                Text(
                    stringResource(
                        R.string.home_trending_reason_title,
                        snapshot.trendingRank,
                        stringResource(if (item is TrendingHomeItem.Movie) R.string.home_trending_movie else R.string.home_trending_series),
                    ),
                    style = whyStyle,
                    color = palette.muted,
                )
                Text(whyCopy(snapshot.providerLanguage, preferredLanguage), style = whyStyle, color = palette.muted)
                Text(
                    stringResource(R.string.home_trending_provider_match) + " · " + snapshot.providerRawName,
                    style = whyStyle,
                    color = palette.dim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Which version was picked and why — the sentence the TV's "why" panel shows. */
@Composable
private fun whyCopy(providerLanguage: String?, preferredLanguage: String): String = when (providerLanguage) {
    preferredLanguage -> stringResource(R.string.home_trending_reason_preferred, preferredLanguage)
    "EN" -> stringResource(R.string.home_trending_reason_english, preferredLanguage)
    null -> stringResource(R.string.home_trending_reason_untagged, preferredLanguage)
    else -> stringResource(R.string.home_trending_reason_other)
}

private const val INTERVAL_MS = 10_000L
private const val SWIPE_SLOP_PX = 60f

/** `.pc` on Home is 104 wide. */
internal val PosterWidth = 104.dp

/** The rating chip's gold, `#ffcc4d`. */
private val RatingGold = Color(0xFFFFCC4D)
