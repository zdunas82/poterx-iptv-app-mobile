package tv.own.owntv.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.UserDataWriter
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.categorylock.lockAwareCount
import tv.own.owntv.core.categorylock.sealedCategoryIds
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.mobile.ui.screens.library.VodTuner

/** How many channels, films and shows a list holds, one number per type. */
data class TypeCounts(val live: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val total: Int get() = live + movies + series
}

/** More › Favourites: the starred items in the user's own order, with what is on each channel now. */
data class FavouriteLists(
    val live: List<ChannelEntity> = emptyList(),
    val onNow: Map<Long, XtEpgEntry> = emptyMap(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
)

/**
 * One More › History row. [episode] is the show's last-watched episode; [positionMs] / [durationMs]
 * its (or the film's) saved progress, 0 when there is none.
 */
data class HistoryEntry(
    val type: MediaType,
    val id: Long,
    val title: String,
    val art: String?,
    val channel: ChannelEntity?,
    val year: Int?,
    val episode: EpisodeEntity?,
    val watchedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)

data class HistoryLists(
    val live: List<HistoryEntry> = emptyList(),
    val movies: List<HistoryEntry> = emptyList(),
    val series: List<HistoryEntry> = emptyList(),
)

/**
 * More's data: the values the More rows carry — what is pinned, what is downloading, how
 * much is starred and watched, when the last backup was, whether Local sync is on — and the Favourites and
 * History pages' lists.
 *
 * The counts are queries the DAOs already had — `countFavorites` and `countHistory`, both joined to the
 * content table so a favourite whose channel went stale on a re-sync is not counted before the relink
 * purges it. The lists are the television's More loading (`features/more`), with the phone's programme
 * reader, whose entries carry the times the channel rows draw their progress from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreCountsViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val settings: SettingsRepository,
    sourceDao: SourceDao,
    downloadDao: DownloadDao,
    localSync: LocalSyncManager,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val userDataWriter: UserDataWriter,
    private val epgReader: LiveEpgReader,
    private val customize: CustomizationStore,
    private val vodTuner: VodTuner,
) : ViewModel() {

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    val favorites: StateFlow<TypeCounts> = counts(favorites = true)
    val history: StateFlow<TypeCounts> = counts(favorites = false)

    /** Settings pinned to Quick. */
    val pinnedCount: StateFlow<Int> = settings.quickPinnedKeys.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Downloads of the active profile running right now. */
    val runningDownloads: StateFlow<Int> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else downloadDao.observeForProfile(pid) }
        .map { list -> list.count { it.status == DownloadStatus.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The last successful backup, or `null` if none has been taken on this device. */
    val lastBackup: StateFlow<SettingsRepository.LastBackup?> = settings.lastBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether this phone is reachable for Local sync (Sync mode on). */
    val syncOn: StateFlow<Boolean> = localSync.hostState.map { it is CompanionServerState.Listening }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Each type's browse view; items in a category a lock still keeps shut stay off both pages. */
    private val visible: Flow<Map<MediaType, SectionCustomizations>> = ctx.flatMapLatest { c ->
        if (c.profileId < 0) flowOf(emptyMap())
        else combine(LISTED.map { t -> customize.observeVisible(c.profileId, t).map { t to it } }) { it.toMap() }
    }

    /** Read again whenever the favourite counts change, so starring or removing shows at once. */
    val favouriteLists: StateFlow<FavouriteLists> = combine(ctx, favorites, visible) { c, _, v -> c to v }
        .flatMapLatest { (c, v) -> flow { emit(loadFavourites(c, Sealed(v))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouriteLists())

    val historyLists: StateFlow<HistoryLists> = combine(ctx, history, visible) { c, _, v -> c to v }
        .flatMapLatest { (c, v) -> flow { emit(loadHistory(c, Sealed(v))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryLists())

    private class Sealed(private val visible: Map<MediaType, SectionCustomizations>) {
        fun holds(type: MediaType, categoryId: Long?) = visible[type]?.sealsItemIn(categoryId) == true
    }

    private fun ids(c: ActiveProfileSources, type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }

    private suspend fun loadFavourites(c: ActiveProfileSources, sealed: Sealed): FavouriteLists {
        if (c.profileId < 0) return FavouriteLists()
        val key = ContentOrderEntity.FAV_CONTEXT
        val live = channelDao.snapshotFavoritesManual(c.profileId, key, ids(c, MediaType.LIVE), LIST_LIMIT)
            .filterNot { sealed.holds(MediaType.LIVE, it.categoryId) }
        val onNow = if (live.isEmpty()) emptyMap() else epgReader.nowProgrammesFor(
            live, customize.observeVisible(c.profileId, MediaType.LIVE).first(), settings.epgOffsetMinutes.first(),
        )
        return FavouriteLists(
            live = live,
            onNow = onNow,
            movies = movieDao.snapshotFavoritesManual(c.profileId, key, ids(c, MediaType.MOVIE), LIST_LIMIT)
                .filterNot { sealed.holds(MediaType.MOVIE, it.categoryId) },
            series = seriesDao.snapshotFavoritesManual(c.profileId, key, ids(c, MediaType.SERIES), LIST_LIMIT)
                .filterNot { sealed.holds(MediaType.SERIES, it.categoryId) },
        )
    }

    private suspend fun loadHistory(c: ActiveProfileSources, sealed: Sealed): HistoryLists {
        val pid = c.profileId
        if (pid < 0) return HistoryLists()
        suspend fun watched(type: MediaType) = historyDao.getForProfileType(pid, type).associate { it.itemId to it.watchedAt }
        val live = channelDao.recentlyWatchedWithTimestampFiltered(pid, ids(c, MediaType.LIVE), LIST_LIMIT).first()
            .filterNot { sealed.holds(MediaType.LIVE, it.channel.categoryId) }.map {
            HistoryEntry(MediaType.LIVE, it.channel.id, it.channel.name, null, it.channel, null, null, it.watchedAt, 0L, 0L)
        }
        val movieAt = watched(MediaType.MOVIE)
        val movieProgress = progressDao.getForProfileType(pid, MediaType.MOVIE).associateBy { it.itemId }
        val movies = movieDao.recentlyWatchedSnapshot(pid, ids(c, MediaType.MOVIE), LIST_LIMIT)
            .filterNot { sealed.holds(MediaType.MOVIE, it.categoryId) }.map { m ->
            val p = movieProgress[m.id]
            HistoryEntry(
                MediaType.MOVIE, m.id, m.name, m.backdropUrl ?: m.posterUrl, null, m.year ?: m.parsedYear, null,
                movieAt[m.id] ?: 0L, p?.positionMs ?: 0L, p?.durationMs ?: 0L,
            )
        }
        val seriesAt = watched(MediaType.SERIES)
        val series = seriesDao.recentlyWatchedSnapshot(pid, ids(c, MediaType.SERIES), LIST_LIMIT)
            .filterNot { sealed.holds(MediaType.SERIES, it.categoryId) }.map { s ->
            val episode = progressDao.lastWatchedEpisodeId(pid, s.id)?.let { seriesDao.getEpisodeById(it) }
            val p = episode?.let { progressDao.get(pid, MediaType.EPISODE, it.id) }
            HistoryEntry(
                MediaType.SERIES, s.id, s.name, s.backdropUrl ?: s.posterUrl, null, s.year ?: s.parsedYear, episode,
                seriesAt[s.id] ?: 0L, p?.positionMs ?: 0L, p?.durationMs ?: 0L,
            )
        }
        return HistoryLists(live, movies, series)
    }

    fun removeFavourite(type: MediaType, id: Long) {
        val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return
        viewModelScope.launch { userDataWriter.removeFavorite(pid, type, id) }
    }

    /** History's Resume: the film or the show's last episode from where it stopped; [onStarted] opens the player. */
    fun resume(entry: HistoryEntry, onStarted: () -> Unit) {
        viewModelScope.launch {
            val started = when (entry.type) {
                MediaType.MOVIE -> vodTuner.playMovie(entry.id, entry.positionMs)
                MediaType.SERIES -> entry.episode?.let { vodTuner.playEpisode(it.id, entry.positionMs) } ?: false
                else -> false
            }
            if (started) onStarted()
        }
    }

    /** The same removal each browse screen's "Remove from History" does, per type. */
    fun removeHistory(entry: HistoryEntry) {
        val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            when (entry.type) {
                MediaType.SERIES -> userDataWriter.removeSeriesHistory(pid, entry.id)
                MediaType.MOVIE -> {
                    userDataWriter.removeHistory(pid, MediaType.MOVIE, entry.id)
                    userDataWriter.clearProgress(pid, MediaType.MOVIE, entry.id)
                }
                else -> userDataWriter.removeHistory(pid, entry.type, entry.id)
            }
        }
    }

    private fun counts(favorites: Boolean): StateFlow<TypeCounts> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(TypeCounts())
            } else {
                // An empty id list makes the IN clause match nothing useful — the sentinel every
                // other count flow in the app uses.
                // What a locked category holds leaves the counts too, so they agree with the lists.
                fun count(type: MediaType, all: (Long, List<Long>) -> Flow<Int>, excluding: (Long, List<Long>, List<Long>) -> Flow<Int>) =
                    lockAwareCount(customize.sealedCategoryIds(c.profileId, type), { all(c.profileId, ids(c, type)) }) { excluding(c.profileId, ids(c, type), it) }
                combine(
                    if (favorites) count(MediaType.LIVE, channelDao::countFavorites, channelDao::countFavoritesExcluding)
                    else count(MediaType.LIVE, channelDao::countHistory, channelDao::countHistoryExcluding),
                    if (favorites) count(MediaType.MOVIE, movieDao::countFavorites, movieDao::countFavoritesExcluding)
                    else count(MediaType.MOVIE, movieDao::countHistory, movieDao::countHistoryExcluding),
                    if (favorites) count(MediaType.SERIES, seriesDao::countFavorites, seriesDao::countFavoritesExcluding)
                    else count(MediaType.SERIES, seriesDao::countHistory, seriesDao::countHistoryExcluding),
                    ::TypeCounts,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeCounts())

    private companion object {
        /** Enough for any real list, bounded so a runaway history cannot load the whole catalogue. */
        const val LIST_LIMIT = 500

        val LISTED = listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)
    }
}
