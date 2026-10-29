package tv.own.owntv.mobile.ui.screens.sports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.sports.Demand
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

    /** The hub's chosen sport and league chips: kept while the app runs, back to All on a fresh start. */
    var hubChips: Set<HubChip> = emptySet()
    var hubLeagues: Set<String> = emptySet()

    /** The channel last played from a match page, so its row is scrolled to again on return. */
    var lastPlayedChannelId: Long? = null

    /** A match an alert's tap asked for ([tv.own.owntv.core.sports.MatchNotifications.EXTRA_MATCH_KEY]); the shell opens it. */
    val openMatchKey = MutableStateFlow<String?>(null)

    fun start(match: Match, options: List<WatchOption>) { _session.value = SportsSession(match, options) }

    /** [match] as it stands now, refreshed while collected (`Demand.PlayerScore`: that one league, 30 s live). */
    fun score(match: Match): Flow<Match> =
        repository.observe(Demand.PlayerScore(match)).map { it.match(match.key) ?: match }.distinctUntilChanged()
}
