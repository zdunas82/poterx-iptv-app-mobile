package tv.own.owntv.mobile.ui.screens

import android.icu.util.Calendar
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.rememberClockTick
import tv.own.owntv.mobile.ui.screens.home.formatRating
import tv.own.owntv.mobile.ui.screens.home.timeLeftText
import tv.own.owntv.mobile.ui.screens.library.LibraryTab
import tv.own.owntv.mobile.ui.screens.live.LiveChannelRow
import tv.own.owntv.mobile.ui.screens.settings.SettingsViewModel
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StagePoster
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStill
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.SimpleDateFormat

private enum class FavFilter { ALL, LIVE, MOVIES, SERIES }

/**
 * More › Favourites: All · Live TV · Movies · Series with their counts, then LIVE TV · ON NOW — the
 * channel rows of Live TV, with what is on — and MOVIES & SERIES as three-column posters labelled Movie or
 * Series. A tap goes to the item (Live TV on that channel, the film's or the show's page); a hold offers
 * Remove from Favorites.
 */
@Composable
fun FavoritesScreen(
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: MoreCountsViewModel = koinViewModel(),
) {
    val counts by vm.favorites.collectAsStateWithLifecycle()
    val lists by vm.favouriteLists.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(FavFilter.ALL) }
    var menu by remember { mutableStateOf<Triple<MediaType, Long, String>?>(null) }
    val nowMs by rememberClockTick()

    val showLive = (filter == FavFilter.ALL || filter == FavFilter.LIVE) && lists.live.isNotEmpty()
    val vod = when (filter) {
        FavFilter.ALL -> lists.movies.map { VodFav(MediaType.MOVIE, it.id, it.name, it.posterUrl, it.rating) } +
            lists.series.map { VodFav(MediaType.SERIES, it.id, it.name, it.posterUrl, it.rating) }
        FavFilter.MOVIES -> lists.movies.map { VodFav(MediaType.MOVIE, it.id, it.name, it.posterUrl, it.rating) }
        FavFilter.SERIES -> lists.series.map { VodFav(MediaType.SERIES, it.id, it.name, it.posterUrl, it.rating) }
        FavFilter.LIVE -> emptyList()
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavFilter.entries.forEach { f ->
                    val (label, n) = when (f) {
                        FavFilter.ALL -> R.string.content_epg_all to counts.total
                        FavFilter.LIVE -> R.string.common_nav_live_tv to counts.live
                        FavFilter.MOVIES -> R.string.common_nav_movies to counts.movies
                        FavFilter.SERIES -> R.string.common_nav_series to counts.series
                    }
                    StageTool(onClick = { filter = f }, label = stringResource(label) + " " + n, on = filter == f)
                }
            }
        }
        if (counts.total == 0) {
            item { EmptyNote(stringResource(R.string.more_fav_empty_title), stringResource(R.string.more_fav_empty_body_mobile)) }
            return@LazyColumn
        }
        if (showLive) {
            item {
                StageLabel(
                    stringResource(R.string.common_nav_live_tv) + stringResource(R.string.content_epg_bits_separator) + stringResource(R.string.home_row_on_now),
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                )
            }
            items(lists.live, key = { "ch" + it.id }) { ch ->
                LiveChannelRow(
                    channel = ch,
                    showNumber = true,
                    now = lists.onNow[ch.id],
                    nowMs = nowMs,
                    mark = null,
                    isFavorite = true,
                    onClick = { onOpenChannel(ch.id, false) },
                    onLongClick = { menu = Triple(MediaType.LIVE, ch.id, ch.name) },
                )
            }
        }
        if (vod.isNotEmpty()) {
            item {
                val heading = when (filter) {
                    FavFilter.MOVIES -> stringResource(R.string.common_nav_movies)
                    FavFilter.SERIES -> stringResource(R.string.common_nav_series)
                    else -> stringResource(R.string.more_fav_movies_series)
                }
                StageLabel(heading, Modifier.padding(start = 12.dp, end = 12.dp, top = if (showLive) 16.dp else 14.dp, bottom = 8.dp))
            }
            items(vod.chunked(3), key = { row -> row.joinToString { it.type.name + it.id } }) { row ->
                Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        StagePoster(
                            imageUrl = item.poster,
                            title = item.name,
                            subtitle = stringResource(if (item.type == MediaType.MOVIE) R.string.search_movie else R.string.search_series),
                            rating = item.rating?.takeIf { it > 0 }?.let(::formatRating),
                            onClick = { onOpenItem(if (item.type == MediaType.MOVIE) LibraryTab.MOVIES else LibraryTab.SERIES, item.id) },
                            onLongClick = { menu = Triple(item.type, item.id, item.name) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    menu?.let { (type, id, name) ->
        MobileBottomSheet(onDismissRequest = { menu = null }, title = name) {
            StageSheetHeader(title = name)
            StageMenuItem(
                stringResource(R.string.content_remove_favourite),
                onClick = { vm.removeFavourite(type, id); menu = null },
                icon = MobileIcons.Favorite,
            )
        }
    }
}

private data class VodFav(val type: MediaType, val id: Long, val name: String, val poster: String?, val rating: Double?)

private enum class HistTab(val type: MediaType) { LIVE(MediaType.LIVE), MOVIES(MediaType.MOVIE), SERIES(MediaType.SERIES) }

/**
 * More › History: Live TV · Movies · Series with counts and the red Clear tool, then the type's
 * rows, newest first — a 100 dp still with the progress, the title and "Movie · 2026 · Today 18:40 ·
 * 48 min left". A tap selects a row (`.fx`) and shows Resume (Watch for a channel) · Remove. Opens on the
 * type watched most recently.
 */
@Composable
fun WatchHistoryScreen(
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MoreCountsViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val counts by vm.history.collectAsStateWithLifecycle()
    val lists by vm.historyLists.collectAsStateWithLifecycle()
    val latest = listOfNotNull(
        lists.live.firstOrNull()?.let { HistTab.LIVE to it.watchedAt },
        lists.movies.firstOrNull()?.let { HistTab.MOVIES to it.watchedAt },
        lists.series.firstOrNull()?.let { HistTab.SERIES to it.watchedAt },
    ).maxByOrNull { it.second }?.first ?: HistTab.MOVIES
    var picked by rememberSaveable { mutableStateOf<HistTab?>(null) }
    val tab = picked ?: latest
    val rows = when (tab) { HistTab.LIVE -> lists.live; HistTab.MOVIES -> lists.movies; HistTab.SERIES -> lists.series }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val sep = stringResource(R.string.content_epg_bits_separator)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistTab.entries.forEach { t ->
                    val (label, n) = when (t) {
                        HistTab.LIVE -> R.string.common_nav_live_tv to counts.live
                        HistTab.MOVIES -> R.string.common_nav_movies to counts.movies
                        HistTab.SERIES -> R.string.common_nav_series to counts.series
                    }
                    StageTool(onClick = { picked = t; selected = null }, label = stringResource(label) + " " + n, on = tab == t)
                }
                Spacer(Modifier.weight(1f))
                if (counts.total > 0) {
                    StageTool(
                        onClick = { clearing = true },
                        icon = MobileIcons.Delete,
                        danger = true,
                        contentDescription = stringResource(R.string.settings_clear_history),
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            item { EmptyNote(stringResource(R.string.more_history_empty), null) }
            return@LazyColumn
        }
        items(rows, key = { it.type.name + it.id }) { e ->
            val key = e.type.name + e.id
            val left = when {
                e.durationMs > 0 && e.positionMs > 0 -> timeLeftText(((e.durationMs - e.positionMs).coerceAtLeast(0L) + 59_999L) / 60_000L)
                // "0:14 watched": hours and minutes.
                e.positionMs > 0 -> (e.positionMs / 60_000L).let { m ->
                    stringResource(R.string.content_watched_duration, String.format(java.util.Locale.ROOT, "%d:%02d", m / 60, m % 60))
                }
                else -> null
            }
            val line = when (e.type) {
                MediaType.LIVE -> listOf(stringResource(R.string.common_nav_live_tv), watchedWhen(e.watchedAt))
                MediaType.MOVIE -> listOfNotNull(stringResource(R.string.search_movie), e.year?.toString(), watchedWhen(e.watchedAt), left)
                else -> listOfNotNull(
                    e.episode?.let { stringResource(R.string.content_season_episode, it.seasonNumber, it.episodeNumber) },
                    watchedWhen(e.watchedAt), left,
                )
            }.joinToString(sep)
            HistoryRow(
                entry = e,
                line = line,
                selected = selected == key,
                onClick = { selected = if (selected == key) null else key },
                onResume = {
                    when {
                        e.type == MediaType.LIVE -> onOpenChannel(e.id, false)
                        e.type == MediaType.SERIES && e.episode == null -> onOpenItem(LibraryTab.SERIES, e.id)
                        else -> vm.resume(e, onOpenPlayer)
                    }
                },
                onRemove = { vm.removeHistory(e); selected = null },
            )
        }
    }

    if (clearing) {
        ClearHistoryFlow(onClear = { scope -> settingsVm.clearHistory(scope) }, onDismiss = { clearing = false })
    }
}

/** `.chrow` 74 dp (110 when selected, with its buttons): the 100 dp still, the title (14.5 / 700) and the line. */
@Composable
private fun HistoryRow(entry: HistoryEntry, line: String, selected: Boolean, onClick: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    val palette = LocalStagePalette.current
    StageRow(onClick = onClick, current = selected, height = if (selected) 110.dp else 74.dp, gap = 12.dp) {
        Box(Modifier.width(100.dp)) {
            val progress = entry.positionMs.takeIf { entry.durationMs > 0 && it > 0 }?.let { (it.toFloat() / entry.durationMs).coerceIn(0f, 1f) }
            if (entry.channel != null) {
                StageStill(null, radius = 9.dp) {
                    tv.own.owntv.mobile.ui.components.ChannelLogoImage(
                        url = entry.channel.displayLogoUrl,
                        modifier = Modifier.align(Alignment.Center).fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                        plateOn = palette.cardSolid,
                        fallback = { tv.own.owntv.mobile.ui.stage.StageIcon(MobileIcons.LiveTv, 19.dp, palette.dim) },
                    )
                }
            } else {
                StageStill(entry.art, progress = progress, radius = 9.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            // A channel's name without its provider marks, as its Live TV row writes it.
            val title = remember(entry.title) { if (entry.channel != null) ProviderTags.parse(entry.title).name else entry.title }
            Text(title, style = stageText(14.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = stageText(12f, 500), color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selected) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StageButton(
                        onClick = onResume,
                        text = stringResource(if (entry.type == MediaType.LIVE) R.string.content_key_watch else R.string.common_resume),
                        icon = MobileIcons.PlayArrow,
                        small = true,
                    )
                    StageButton(onClick = onRemove, text = stringResource(R.string.settings_customize_remove), icon = MobileIcons.Close, small = true)
                }
            }
        }
    }
}

/** A list's empty state: its title (16 / 800) and an optional line under it. */
@Composable
private fun EmptyNote(title: String, body: String?) {
    val palette = LocalStagePalette.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 24.dp)) {
        Text(title, style = stageText(16f, 800), color = palette.text)
        body?.let { Text(it, style = stageText(12.5f, 500, lineHeight = 1.5f), color = palette.muted, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** "Today 18:40", "Yesterday", "28 Sep" — when it was watched. */
@Composable
private fun watchedWhen(atMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val day = 24 * 60 * 60_000L
    return when {
        atMs >= today -> stringResource(R.string.content_epg_today) + " " +
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "jm"), locale).format(atMs)
        atMs >= today - day -> stringResource(R.string.content_epg_yesterday)
        else -> SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(atMs)
    }
}
