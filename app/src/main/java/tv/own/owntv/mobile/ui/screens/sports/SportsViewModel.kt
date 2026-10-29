package tv.own.owntv.mobile.ui.screens.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.reminder.ReminderManager
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.Demand
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.GuideSport
import tv.own.owntv.core.sports.GuideSportItem
import tv.own.owntv.core.sports.RebuildState
import tv.own.owntv.core.sports.SportOnChannels
import tv.own.owntv.core.sports.SportsAlertsSetup
import tv.own.owntv.core.sports.SportsBoard
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsHomeRow
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchList
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchReader
import tv.own.owntv.core.sports.WatchWindow
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.StandingsTable
import java.util.Locale

/** Where a match page's Find channels stands (channels are searched only when asked). */
sealed interface ChannelSearch {
    /** The match is being fetched afresh to judge Find channels' window. */
    data object Checking : ChannelSearch
    data class Closed(val window: WatchWindow) : ChannelSearch
    data object NotSearched : ChannelSearch
    data object Searching : ChannelSearch
    data class Found(val options: List<WatchOption>) : ChannelSearch
}

/** The match page: the match, its summary and table from the board, and "Watch live" once searched. */
data class MatchPageState(
    val match: Match,
    val summary: MatchSummary?,
    val standings: StandingsTable?,
    val config: SportsConfig,
    val search: ChannelSearch,
    val list: WatchList,
    val filter: WatchFilter,
    val playlists: List<Pair<Long, String>>,
    val preferredLanguages: List<String>,
) {
    val hasPlaylist: Boolean get() = playlists.isNotEmpty()
}

/** The league page. [matches] are the league's on the board (today and tomorrow), kick-off order. */
data class LeaguePageState(
    val leagueKey: String,
    val table: StandingsTable?,
    val matches: List<Match>,
    val config: SportsConfig,
)

/** Everything the hub draws. No channels: those are found per match, on the match page. */
data class SportsHubState(
    val board: SportsBoard = SportsBoard(),
    val config: SportsConfig = SportsConfig(),
    val onNow: Map<GuideSport, List<GuideSportItem>> = emptyMap(),
    val hasPlaylist: Boolean = true,
    val rebuilding: Boolean = false,
    /** Start times of the profile's programme reminders, keyed `start|title`, so a row can show "Reminder". */
    val reminders: Set<String> = emptySet(),
)

/**
 * The Sports screens' state — the television's own view-model, for the phone. Collecting [hub] is what
 * asks core for scores (`Demand.Hub`); nothing is fetched while no sports screen is shown, and core drops
 * the board five minutes after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModel(
    private val settings: SettingsRepository,
    private val repository: SportsRepository,
    private val watchReader: WatchReader,
    private val onChannels: SportOnChannels,
    private val alerts: SportsAlertsSetup,
    private val reminders: ReminderManager,
    private val playback: SportsPlayback,
    sourceDao: SourceDao,
) : ViewModel() {

    private val config = settings.activeSportsConfig.distinctUntilChanged()

    private val board = repository.observe(Demand.Hub)

    /** The profile's live playlists (id → name), for the filter sheet's Playlist choice. */
    private val playlists = activeProfileSources(settings, sourceDao)
        .map { a -> a.sources.filter { it.syncLive }.map { it.id to it.name } }.distinctUntilChanged()

    private val hasPlaylist = playlists.map { it.isNotEmpty() }.distinctUntilChanged()

    /** This device's live playlist ids: a Settings playlist filter only counts these (core `WatchFilter.from`). */
    private val liveSources = playlists.map { l -> l.map { it.first } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The hub's ⟳: the whole board built again. */
    fun rebuild() = repository.rebuild()

    private val onNow = combine(board, hasPlaylist) { b, has -> has to b.matches.filter { it.isLive }.map { it.key } }
        .distinctUntilChanged()
        .flatMapLatest { (has, _) ->
            if (!has) flowOf(emptyMap()) else onChannels.observe { repository.board.value.matches.filter { it.isLive } }
        }
        .flowOn(Dispatchers.Default)
        .onStart { emit(emptyMap()) }

    private val reminderKeys = settings.activeProfileId.flatMapLatest { pid ->
        if (pid < 0) flowOf(emptySet()) else reminders.observe(pid).map { list -> list.map { reminderKey(it.startMs, it.title) }.toSet() }
    }

    val hub: StateFlow<SportsHubState> = combine(board, config, onNow, hasPlaylist, repository.rebuild) { b, c, n, has, r ->
        SportsHubState(b, c, n, has, rebuilding = r is RebuildState.Running)
    }.combine(reminderKeys) { s, r -> s.copy(reminders = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsHubState())

    /** The profile's sports settings alone — no scores are asked for by reading it. */
    val sportsConfig: StateFlow<SportsConfig> = config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsConfig())

    // --- The Home row --------------------------------------------------------------------------------

    /**
     * Home's "Sports" row: followed matches while one is live or kicks off within the hour (core's
     * `SportsHomeRow`), in the user's order. Asks for scores only while collected, and only after
     * [HOME_ROW_DELAY_MS], so opening the app is never slowed by it.
     */
    val homeRow: StateFlow<List<Match>> = flow {
        delay(HOME_ROW_DELAY_MS)
        emitAll(
            combine(repository.observe(Demand.HomeRow), config) { b, c ->
                SportsHomeRow.items(b, c, c.leagues.toSet(), System.currentTimeMillis())
                    .sortedWith(compareBy<Match>({ c.rankOf(it.sport, it.feed).first }, { c.rankOf(it.sport, it.feed).second }, { !it.isLive }, { it.startMs }))
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- The match page ------------------------------------------------------------------------------

    private val openMatch = MutableStateFlow<Match?>(null)

    /** The filter while a match page is open; null = the profile's defaults (Settings → Sports). */
    private val pageFilter = MutableStateFlow<WatchFilter?>(null)

    /** Set by Find channels on the open match page; reset when another match opens. */
    private val findAsked = MutableStateFlow(false)

    /** Find channels: search this match's channels (only this match, only now). */
    fun findChannels() { findAsked.value = true }

    /**
     * The open match, kept fresh while shown: its details and table come from the stored board.
     * Channels are not searched until Find channels; a search already made under the current guide shows at once.
     */
    val matchPage: StateFlow<MatchPageState?> = openMatch.flatMapLatest { m ->
        if (m == null) return@flatMapLatest flowOf(null)
        val leagueKey = League.keyOf(m.sport, m.leagueSlug).takeIf { SportsCatalog.league(it) != null }
        val boards = if (leagueKey == null) repository.observe(Demand.MatchPage(m))
        else combine(repository.observe(Demand.MatchPage(m)), repository.observe(Demand.LeaguePage(leagueKey))) { a, _ -> a }
        // Find channels' window is judged on the match fetched now, not the stored copy.
        val window = flow {
            emit(null)
            val fresh = repository.refetch(m) ?: m
            while (true) {
                val b = repository.board.value
                emit(WatchWindow.of(b.match(m.key) ?: fresh, b.endedAt[m.key], System.currentTimeMillis()))
                delay(60_000L)
            }
        }.distinctUntilChanged()
        val search = combine(config, hasPlaylist, findAsked, window) { c, has, asked, w -> listOf(c, has, asked, w) }.transformLatest { (cv, hv, av, w) ->
            val c = cv as SportsConfig
            if (!(hv as Boolean)) return@transformLatest emit(ChannelSearch.NotSearched)
            if (w == null) return@transformLatest emit(ChannelSearch.Checking)
            if (w != WatchWindow.Open) return@transformLatest emit(ChannelSearch.Closed(w as WatchWindow))
            val found = watchReader.found(m) { SportsMemory.rememberedFor(c, it) }
            when {
                found != null -> emit(ChannelSearch.Found(found))
                !(av as Boolean) -> emit(ChannelSearch.NotSearched)
                else -> {
                    emit(ChannelSearch.Searching)
                    emit(ChannelSearch.Found(watchReader.watchFor(listOf(m)) { SportsMemory.rememberedFor(c, it) }[m.key].orEmpty()))
                }
            }
        }.flowOn(Dispatchers.Default)
        combine(boards, config, search, pageFilter, playlists) { b, c, s, f, lists ->
            val appLanguage = Locale.getDefault().language
            val filter = f ?: WatchFilter.from(c, appLanguage, lists.map { it.first })
            val preferred = c.preferredLanguages(appLanguage).toList()
            val opts = (s as? ChannelSearch.Found)?.options.orEmpty()
            MatchPageState(
                match = b.match(m.key) ?: m,
                summary = b.summaries[m.key],
                standings = leagueKey?.let { b.standings[it] },
                config = c,
                search = s,
                list = WatchList.of(opts, filter, preferred, alsoList = preferred.toSet()),
                filter = filter,
                playlists = lists,
                preferredLanguages = preferred,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- The league page -----------------------------------------------------------------------------

    private val openLeague = MutableStateFlow<String?>(null)

    /** A league's table and its matches on the board; its live matches kept fresh while shown. */
    val leaguePage: StateFlow<LeaguePageState?> = openLeague.flatMapLatest { key ->
        if (key == null) return@flatMapLatest flowOf(null)
        combine(repository.observe(Demand.LeaguePage(key)), config) { b, c ->
            val matches = (b.today[key].orEmpty() + b.tomorrow[key].orEmpty()).distinctBy { it.key }.sortedBy { it.startMs }
            LeaguePageState(key, b.standings[key], matches, c)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openLeague(key: String) { openLeague.value = key }

    /** The match a route names, from the stored board — after a cold start (an alert's tap) once the file has loaded. */
    suspend fun matchOf(key: String): Match? = repository.board.value.match(key)
        ?: kotlinx.coroutines.withTimeoutOrNull(MATCH_WAIT_MS) { repository.observe(Demand.Hub).first { it.match(key) != null } }?.match(key)

    fun open(match: Match) {
        if (openMatch.value?.key == match.key) return
        pageFilter.value = null
        findAsked.value = false
        openMatch.value = match
    }

    /** A change in the filter sheet. With "Remember for every match" on, it is saved to Settings → Sports as well. */
    fun setFilter(filter: WatchFilter) = viewModelScope.launch {
        pageFilter.value = filter
        if (config.first().rememberFilters) saveFilter(filter, remember = true)
    }

    /** "Remember for every match": on saves the current filter as the profile's default. */
    fun setRememberFilters(on: Boolean) = viewModelScope.launch {
        val filter = matchPage.value?.filter ?: return@launch
        saveFilter(filter, remember = on)
    }

    private suspend fun saveFilter(filter: WatchFilter, remember: Boolean) {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c ->
            if (!remember) c.copy(rememberFilters = false)
            else c.copy(
                rememberFilters = true,
                languages = filter.languages - WatchFilter.UNKNOWN,
                // Settings keep two-letter countries; a three-letter prefix ("USA") filters this page only.
                countries = filter.countries.filter { it.length == 2 }.toSet(),
                minQuality = filter.minQuality,
                onlyConfirmed = filter.onlyConfirmed,
                strictFilter = filter.strict,
                sourceIds = filter.sourceIds,
            )
        }
    }

    /** Long press on a channel: "Always use for <competition>". */
    fun rememberChannel(match: Match, channel: ChannelEntity) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { SportsMemory.remember(it, match, channel) }
    }

    /** The match page's Following: the competition it is fetched under, in or out of the followed set. */
    fun toggleLeague(feed: String) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c ->
            val now = c.leagues
            c.copy(leagues = if (feed in now) now - feed else now + feed)
        }
    }

    fun follows(config: SportsConfig, feed: String): Boolean = feed in config.followedLeagues()

    /** Follow or unfollow a team (long press on a card). */
    fun toggleTeam(team: FollowedTeam) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c ->
            if (c.teams.any { it.key == team.key }) c.copy(teams = c.teams.filterNot { it.key == team.key })
            else c.copy(teams = c.teams + team)
        }
    }

    /** "Remind me" on a match not started yet: the best found channel's programme reminder, else a notification. */
    fun remind(match: Match, channels: List<WatchOption>) = viewModelScope.launch {
        alerts.remind(settings.activeProfileIdNow(), match, channels, config.first().reminderLeadMinutes)
    }

    val lastPlayedChannelId: Long? get() = playback.lastPlayedChannelId

    /** A channel is played from [match]: the player then offers its other [channels], and the play counts toward suggestions. */
    fun playedFrom(match: Match, channels: List<WatchOption>, playing: ChannelEntity) {
        playback.lastPlayedChannelId = playing.id
        playback.start(match, channels)
        viewModelScope.launch {
            settings.updateSportsConfig(settings.activeProfileIdNow()) { SportsMemory.recordPlay(it, match) }
        }
    }

    /** The sports the profile follows a league of, in the user's order. */
    fun followedSports(config: SportsConfig): List<Sport> {
        val keys = config.followedLeagues()
        return config.orderSports(SportsCatalog.sports.filter { s -> keys.any { SportsCatalog.league(it)?.sport == s } })
    }

    /** [match]'s channels if Find channels already ran for it (the hub's Remind me uses one); never searches. */
    suspend fun channelsFound(match: Match): List<WatchOption> {
        val c = config.first()
        val options = watchReader.found(match) { SportsMemory.rememberedFor(c, it) } ?: return emptyList()
        return WatchList.of(options, WatchFilter.from(c, Locale.getDefault().language, liveSources.value)).shown
    }

    companion object {
        private const val HOME_ROW_DELAY_MS = 3_000L
        private const val MATCH_WAIT_MS = 10_000L

        fun reminderKey(startMs: Long, title: String) = "$startMs|$title"
    }
}
