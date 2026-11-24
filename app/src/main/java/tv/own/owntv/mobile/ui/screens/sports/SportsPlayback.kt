package tv.own.owntv.mobile.ui.screens.sports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.sports.Demand
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match

/** A channel started from a match: the match, and every channel showing it, best first (after the filter). */
data class SportsSession(val match: Match, val options: List<WatchOption>)

/**
 * What the player knows about Sports: which match the playing channel was started
 * from and its score while the controls show. Nothing here touches playback; with no session, or ESPN
 * unreachable, the player is exactly the plain Live player.
 */
class SportsPlayback(private val repository: SportsRepository) {
    private val _session = MutableStateFlow<SportsSession?>(null)
    val session: StateFlow<SportsSession?> = _session.asStateFlow()

    /**
     * The Sports page's open tab and its two chip rows (none chosen = All), shared by the three tabs and
     * kept while the app runs, so a match page opened over them returns to the same place; a fresh start
     * opens on Scores with everything.
     */
    val tab = MutableStateFlow(SportsTab.SCORES)
    val hubChips = MutableStateFlow<Set<HubChip>>(emptySet())
    val hubLeagues = MutableStateFlow<Set<String>>(emptySet())

    /** The open league's own tab (Table, Fixtures …), and the league it belongs to. */
    val leagueTab = MutableStateFlow(0)
    var leagueTabOf: String? = null

    /** Opens [leagueKey] in the Leagues tab, on its first tab: its sport and that league chosen, as the chips would. */
    fun chooseLeague(leagueKey: String) {
        val league = SportsCatalog.league(leagueKey) ?: return
        hubChips.value = setOf(HubChip.One(league.sport))
        hubLeagues.value = setOf(leagueKey)
        leagueTabOf = leagueKey
        leagueTab.value = 0
        tab.value = SportsTab.LEAGUES
    }

    /** The channel last played from a match page, so its row is scrolled to again on return. */
    var lastPlayedChannelId: Long? = null

    /**
     * The match a page is being opened for. Its route carries only the key, and the board holds today and
     * tomorrow only, so a match from the Calendar, a league or a team page is found here.
     */
    var opening: Match? = null

    /** A match an alert's tap asked for ([tv.own.owntv.core.sports.MatchNotifications.EXTRA_MATCH_KEY]); the shell opens it. */
    val openMatchKey = MutableStateFlow<String?>(null)

    fun start(match: Match, options: List<WatchOption>) { _session.value = SportsSession(match, options) }

    /** [match] as it stands now, refreshed while collected (`Demand.PlayerScore`: that one league, 30 s live). */
    fun score(match: Match): Flow<Match> =
        repository.observe(Demand.PlayerScore(match)).map { it.match(match.key) ?: match }.distinctUntilChanged()
}
