package tv.own.owntv.mobile.ui.screens.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.RebuildState
import tv.own.owntv.core.sports.SportsActivityTracker
import tv.own.owntv.core.sports.SportsAlertsSetup
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.SportsSuggestion
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team
import java.util.Locale

/**
 * Settings → Sports — the television's settings view-model, for the phone, plus Goal alerts' "when OwnTV
 * is closed", which asks for notification permission through core. Every value is the active
 * profile's `SportsConfig`, so it travels in backups and local sync with no extra work.
 */
class SportsSettingsViewModel(
    private val settings: SettingsRepository,
    private val source: SportsSource,
    private val repository: SportsRepository,
    activity: SportsActivityTracker,
    sourceDao: SourceDao,
    private val alerts: SportsAlertsSetup,
) : ViewModel() {
    val config: StateFlow<SportsConfig> = settings.activeSportsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsConfig())

    /** The profile's live playlists (id → name), for the Playlist filter. */
    val playlists: StateFlow<List<Pair<Long, String>>> = activeProfileSources(settings, sourceDao)
        .map { a -> a.sources.filter { it.syncLive }.map { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When the sports data was last rebuilt, and whether a rebuild runs now (Refresh sports data now). */
    val rebuiltAt: StateFlow<Long?> = activity.rebuiltAt
    val rebuilding: StateFlow<Boolean> = activity.rebuild.map { it is RebuildState.Running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun rebuild() = repository.rebuild()

    /** The app's language, which ranks first until the user picks commentary languages. */
    val appLanguage: String get() = Locale.getDefault().language

    fun update(transform: (SportsConfig) -> SportsConfig) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow(), transform)
    }

    /** Goal alerts on or off; on asks for notification permission once (core's hook). */
    fun setAlerts(on: Boolean) = viewModelScope.launch { alerts.enable(settings.activeProfileIdNow(), on) }

    fun toggleLeague(key: String) = update { c -> c.copy(leagues = if (key in c.leagues) c.leagues - key else c.leagues + key) }

    /** The Move sheet saved a new sport order. */
    fun setSportOrder(order: List<Sport>) = update { it.copy(sportOrder = order) }

    /** The Move sheet saved a new order for one sport's chosen leagues: they go first, the others follow as they were. */
    fun setLeagueOrder(keys: List<String>) = update { c -> c.copy(leagues = keys + (c.leagues - keys.toSet())) }

    fun toggleTeam(team: FollowedTeam) = update { c ->
        if (c.teams.any { it.key == team.key }) c.copy(teams = c.teams.filterNot { it.key == team.key }) else c.copy(teams = c.teams + team)
    }

    fun suggestions(c: SportsConfig): List<SportsSuggestion> = SportsMemory.suggestions(c, c.followedLeagues())

    /** Teams of the followed leagues, fetched once per page visit, for the My teams search. */
    private var teamIndex: List<Pair<FollowedTeam, String>>? = null

    suspend fun searchTeams(query: String): List<FollowedTeam> {
        if (query.trim().length < 2) return emptyList()
        val index = teamIndex ?: buildList {
            for (key in config.value.followedLeagues()) {
                val league = SportsCatalog.league(key) ?: continue
                val teams: List<Team> = runCatching { source.teams(league) }.getOrDefault(emptyList())
                teams.forEach { t -> add(FollowedTeam(league.sport, t.id, t.name, t.abbreviation) to t.name) }
            }
        }.distinctBy { it.first.key }.also { teamIndex = it }
        val q = query.trim().lowercase()
        return index.filter { (_, name) -> name.lowercase().contains(q) }.map { it.first }.take(30)
    }
}
