package tv.own.owntv.mobile.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.home.homeKey
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.home.HomeFeed
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.ui.components.jsonList
import tv.own.owntv.mobile.ui.screens.ContentActions
import tv.own.owntv.mobile.ui.screens.library.VodTuner

/**
 * Home, for the phone.
 *
 * The rails themselves are core's — the same reader the television's Home uses, so the rows are the
 * ones the user arranged there, in that order, minus the ones they hid. What is here is only the
 * phone's part: when to rebuild them (this screen is left and returned to rather than lived on), and
 * playing what was tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val reader: HomeFeedReader,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val tuner: VodTuner,
    private val actions: ContentActions,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val metadata: MetadataRepository,
    private val sourceDao: SourceDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    private val profileId: StateFlow<Long> = settings.activeProfileId
        .map { stored -> if (stored >= 0) profileDao.resolveExistingProfileId(stored) ?: -1L else -1L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    /**
     * One conflated request to rebuild. The screen asks on every entry, because coming back from a
     * film is exactly when the continue rows are wrong, and a profile switch asks on its own —
     * `collectLatest` drops a load already running rather than letting the two queue up.
     */
    private val reloads = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _feed = MutableStateFlow<HomeFeed?>(null)

    /** Null until the first load finishes — the screen shows nothing rather than a false empty state. */
    val feed: StateFlow<HomeFeed?> = _feed

    // Declared before `init`: the first load below fills them.
    private val _heroBackdrops = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The TMDB backdrop per Keep watching card, keyed by [homeKey]; the card falls back without one. */
    val heroBackdrops: StateFlow<Map<String, String>> = _heroBackdrops

    private val resolvingHeroKeys = mutableSetOf<String>()

    init {
        reloads.tryEmit(Unit)
        viewModelScope.launch {
            combine(profileId, reloads) { pid, _ -> pid }.collectLatest { pid ->
                val feed = if (pid < 0) HomeFeed() else reader.load(pid)
                _feed.value = feed
                resolveHeroBackdrops(feed.heroItems)
            }
        }
    }

    fun refresh() {
        reloads.tryEmit(Unit)
    }

    // --- Keep watching stills -------------------------------------------------------------------

    /**
     * The TV's lookup, card for card: a film's backdrop, a show's (or, with none, its episode's still).
     * Off the first frame: the rows paint first, the stills fill in after.
     */
    private fun resolveHeroBackdrops(items: List<HeroItem>) {
        items.filterNot { it is HeroItem.LiveHero }.forEach { item ->
            val key = item.homeKey
            if (_heroBackdrops.value.containsKey(key) || !resolvingHeroKeys.add(key)) return@forEach
            viewModelScope.launch {
                try {
                    delay(250)
                    val url = withContext(Dispatchers.IO) { runCatching { heroBackdrop(item) }.getOrNull() } ?: return@launch
                    _heroBackdrops.value = _heroBackdrops.value + (key to url)
                } finally {
                    resolvingHeroKeys.remove(key)
                }
            }
        }
    }

    private suspend fun heroBackdrop(item: HeroItem): String? = when (item) {
        is HeroItem.MovieHero -> MetadataImages.backdrop(metadata.resolveMovie(item.movie)?.backdropPath, size = "w1280")
        is HeroItem.SeriesHero -> {
            val show = metadata.resolveSeries(item.series)
            MetadataImages.backdrop(show?.backdropPath, size = "w1280")
                ?: metadata.resolveEpisode(item.series, item.episode)
                    ?.let { MetadataImages.backdrop(it.backdropPath ?: it.posterPath, size = "w1280") }
        }
        is HeroItem.LiveHero -> null
    }

    // --- Progress on the poster rows ------------------------------------------------------------------

    /** Per film, how far in (0..1); at the Library's watched fraction or more it counts as watched. */
    val movieProgress: StateFlow<Map<Long, Float>> = profileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else progressDao.observeMovieProgress(pid) }
        .map { rows -> rows.filter { it.durationMs > 0 }.associate { it.itemId to it.positionMs.toFloat() / it.durationMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Per show, how far into the episode watched last (0..1) — the same reading as [movieProgress]. */
    val seriesProgress: StateFlow<Map<Long, Float>> = profileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else progressDao.observeLatestEpisodeProgressPerSeries(pid) }
        .map { rows -> rows.filter { it.durationMs > 0 }.associate { it.seriesId to it.positionMs.toFloat() / it.durationMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // --- The Resume pill ------------------------------------------------------------------------------

    /** The last thing watched, as the pill at the top of Home names it (the TV's Continue chip). */
    data class ResumeTarget(val name: String, val type: MediaType, val itemId: Long, val positionMs: Long)

    val resume: StateFlow<ResumeTarget?> = profileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(null) else historyDao.observeMostRecent(pid).map { h -> h?.let { resolveResume(pid, it) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The TV's `resolveContinue`: the item behind the history row, unless the profile may not see it. */
    private suspend fun resolveResume(pid: Long, h: WatchHistoryEntity): ResumeTarget? = withContext(Dispatchers.IO) {
        when (h.mediaType) {
            MediaType.MOVIE -> movieDao.getById(h.itemId)
                ?.takeIf { AdultCategoryClassifier.allows(pid, it.categoryId, profileDao, categoryDao) }
                ?.let { m -> ResumeTarget(m.name, MediaType.MOVIE, m.id, progressDao.get(pid, MediaType.MOVIE, m.id)?.positionMs ?: 0L) }
            MediaType.EPISODE -> seriesDao.getEpisodeById(h.itemId)?.let { ep ->
                seriesDao.getSeriesById(ep.seriesId)
                    ?.takeIf { AdultCategoryClassifier.allows(pid, it.categoryId, profileDao, categoryDao) }
                    ?.let { s -> ResumeTarget(s.name, MediaType.EPISODE, ep.id, progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L) }
            }
            MediaType.LIVE -> channelDao.getById(h.itemId)
                ?.takeIf { AdultCategoryClassifier.allows(pid, it.categoryId, profileDao, categoryDao) }
                ?.let { c -> ResumeTarget(c.name, MediaType.LIVE, c.id, 0L) }
            else -> null
        }
    }

    /** Switch a live row between logo cards and what-is-on-now. Stored where the TV app reads it. */
    fun toggleLiveMode(row: HomeRow) {
        val pid = profileId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            settings.updateHomeConfig(pid) { config ->
                when (row) {
                    HomeRow.RECENT_CHANNELS -> config.copy(recentLiveMode = config.recentLiveMode.toggled())
                    HomeRow.FAVORITE_CHANNELS -> config.copy(favoriteLiveMode = config.favoriteLiveMode.toggled())
                    else -> config
                }
            }
            // The guide slice is only read for a row that is in "on now" mode, so the feed has to be
            // rebuilt for the row that just switched into it.
            refresh()
        }
    }

    fun modeOf(row: HomeRow, state: HomeFeed): HomeLiveRowMode = when (row) {
        HomeRow.FAVORITE_CHANNELS -> state.config.favoriteLiveMode
        else -> state.config.recentLiveMode
    }

    /** Play a film, or an episode, from where it was left. [onStarted] runs only if the picture is
     *  opening here rather than in another app — the same rule the detail screen plays by. */
    fun playMovie(movieId: Long, positionMs: Long, onStarted: () -> Unit) {
        viewModelScope.launch { if (tuner.playMovie(movieId, positionMs)) onStarted() }
    }

    fun playEpisode(episodeId: Long, positionMs: Long, onStarted: () -> Unit) {
        viewModelScope.launch { if (tuner.playEpisode(episodeId, positionMs)) onStarted() }
    }

    // --- Now Trending ---------------------------------------------------------------------------

    /**
     * Act on the trending item: play the film, or open the show's episodes.
     *
     * The saved row is re-read first, because the hero is built from a snapshot the worker took and a
     * sync since then may have replaced or dropped that exact version. [onUnavailable] is the case the
     * television shows the same message for, and the feed is rebuilt so the dead item goes.
     */
    fun activateTrending(
        item: TrendingHomeItem,
        onPlayerOpened: () -> Unit,
        onOpenSeries: (Long) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val current = revalidateTrendingItem(item)) {
                is TrendingHomeItem.Movie ->
                    if (tuner.playMovie(current.movie.id, 0L)) onPlayerOpened() else gone(onUnavailable)
                is TrendingHomeItem.Series -> onOpenSeries(current.series.id)
                null -> gone(onUnavailable)
            }
        }
    }

    private fun gone(onUnavailable: () -> Unit) {
        onUnavailable()
        refresh()
    }

    /** Re-resolve the exact saved provider row, in case a sync replaced it since the snapshot. */
    private suspend fun revalidateTrendingItem(item: TrendingHomeItem): TrendingHomeItem? =
        withContext(Dispatchers.IO) {
            when (item) {
                is TrendingHomeItem.Movie -> movieDao.getById(item.movie.id)
                    ?.takeIf { it.sourceId == item.snapshot.sourceId }
                    ?.let { item.copy(movie = it) }
                is TrendingHomeItem.Series -> seriesDao.getSeriesById(item.series.id)
                    ?.takeIf { it.sourceId == item.snapshot.sourceId }
                    ?.let { item.copy(series = it) }
            }
        }

    /** The full cached TMDB payload the details sheet shows, fetched under Trending's own TMDB id. */
    suspend fun resolveTrendingDetails(item: TrendingHomeItem): TrendingDetails =
        withContext(Dispatchers.IO) {
            val cache = when (item) {
                is TrendingHomeItem.Movie -> metadata.resolveKnownMovie(item.movie, item.snapshot.tmdbId)
                is TrendingHomeItem.Series -> metadata.resolveKnownSeries(item.series, item.snapshot.tmdbId)
            }
            TrendingDetails(cache, settings.metadataConfig().mode.tmdbWins)
        }

    data class TrendingDetails(val cache: MetadataCacheEntity?, val tmdbWins: Boolean)

    /**
     * What the hero's info line and buttons need beyond the snapshot (the TV's `TrendingExtras`): TMDB
     * genres and title art, and how many entries of the same title the shown playlists hold.
     */
    @Immutable
    data class TrendingExtras(val genres: List<String> = emptyList(), val versions: Int = 1, val logoUrl: String? = null)

    private val _trendingExtras = MutableStateFlow<Map<String, TrendingExtras>>(emptyMap())

    /** Keyed by [TrendingHomeItem.stableKey]; filled for the title on screen. */
    val trendingExtras: StateFlow<Map<String, TrendingExtras>> = _trendingExtras

    private val resolvingTrendingKeys = mutableSetOf<String>()

    fun resolveTrendingExtras(item: TrendingHomeItem) {
        val key = item.stableKey
        if (_trendingExtras.value.containsKey(key) || !resolvingTrendingKeys.add(key)) return
        viewModelScope.launch {
            try {
                val extras = withContext(Dispatchers.IO) {
                    val cache = runCatching { resolveTrendingDetails(item).cache }.getOrNull()
                    TrendingExtras(
                        genres = jsonList(cache?.genresJson),
                        versions = runCatching { versionCount(item) }.getOrDefault(1),
                        logoUrl = MetadataImages.logo(cache?.logoPath),
                    )
                }
                _trendingExtras.value = _trendingExtras.value + (key to extras)
            } finally {
                resolvingTrendingKeys.remove(key)
            }
        }
    }

    /**
     * Entries of the same title (same title signature, the year within one where both have one) across
     * the playlists Home is showing — what "All versions" then lists. The TV's count, rule for rule.
     */
    private suspend fun versionCount(item: TrendingHomeItem): Int {
        // Waits for the profile: right after launch it is still being read, and a 1 counted then would stay.
        val pid = profileId.first { it >= 0 }
        val (type, signature, year) = when (item) {
            is TrendingHomeItem.Movie -> Triple(MediaType.MOVIE, item.movie.titleSignature, item.movie.parsedYear)
            is TrendingHomeItem.Series -> Triple(MediaType.SERIES, item.series.titleSignature, item.series.parsedYear)
        }
        if (signature.isBlank()) return 1
        val rows = activeSourceIds(settings, sourceDao, pid, type).flatMap { sid ->
            if (type == MediaType.MOVIE) movieDao.trendingExact(sid, listOf(signature)) else seriesDao.trendingExact(sid, listOf(signature))
        }
        return rows.count { row -> year == null || row.parsedYear == null || kotlin.math.abs(row.parsedYear!! - year) <= 1 }
            .coerceAtLeast(1)
    }

    // --- The long-press menu ------------------------------------------------------------------------

    val favoriteChannels: StateFlow<Set<Long>> = favoriteIds(MediaType.LIVE)
    val favoriteMovies: StateFlow<Set<Long>> = favoriteIds(MediaType.MOVIE)
    val favoriteSeries: StateFlow<Set<Long>> = favoriteIds(MediaType.SERIES)

    private fun favoriteIds(type: MediaType): StateFlow<Set<Long>> = actions.favoriteIds(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Favouriting or hiding changes what the rails hold, so the feed is rebuilt after either. */
    fun toggleFavorite(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            actions.toggleFavorite(type, itemId)
            refresh()
        }
    }

    fun hide(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            actions.hide(type, itemId)
            refresh()
        }
    }

    fun download(type: MediaType, itemId: Long) {
        viewModelScope.launch { actions.download(type, itemId) }
    }
}
