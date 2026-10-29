package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageText

/** The league page's tabs. Top scorers is not offered: the feeds carry none. */
private enum class LeagueTab { TABLE, FIXTURES, RESULTS }

/** A league: its name and Following, the table, and this matchday's matches; a tap opens a match. */
@Composable
fun SportsLeagueScreen(leagueKey: String, onOpenMatch: (Match) -> Unit, vm: SportsViewModel = koinViewModel()) {
    LaunchedEffect(leagueKey) { vm.openLeague(leagueKey) }
    val page by vm.leaguePage.collectAsStateWithLifecycle()
    val state = page ?: return
    val palette = LocalStagePalette.current
    val league = SportsCatalog.league(state.leagueKey)
    var tab by rememberSaveable { mutableStateOf(LeagueTab.TABLE) }
    val myTeams = state.config.teams.map { it.id }.toSet()
    val matches = when (tab) {
        LeagueTab.TABLE -> state.matches
        LeagueTab.FIXTURES -> state.matches.filter { it.state == MatchState.PRE }
        LeagueTab.RESULTS -> state.matches.filter { it.state == MatchState.POST }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(league?.let { leagueLabel(it) } ?: state.leagueKey, style = stageText(22f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    league?.sport?.let { Text(sportLabel(it), style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                val following = vm.follows(state.config, state.leagueKey)
                StageTool(
                    onClick = { vm.toggleLeague(state.leagueKey) },
                    label = stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                    icon = MobileIcons.Star, on = following,
                )
            }
        }
        item(key = "tabs") {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LeagueTab.entries.forEach { t ->
                    StageTool(
                        onClick = { tab = t },
                        label = stringResource(
                            when (t) {
                                LeagueTab.TABLE -> R.string.sports_tab_table
                                LeagueTab.FIXTURES -> R.string.sports_tab_fixtures
                                LeagueTab.RESULTS -> R.string.sports_tab_results
                            },
                        ),
                        on = tab == t,
                    )
                }
            }
        }
        if (tab == LeagueTab.TABLE) {
            item(key = "tableHead") { Box(Modifier.padding(horizontal = 8.dp)) { StandingsHeader() } }
            state.table?.groups?.forEach { g ->
                if (state.table.groups.size > 1) item(key = "g-" + g.name) {
                    Text(g.name, style = stageText(12.5f, 800), color = palette.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(g.rows, key = { "r-" + g.name + it.team.id }) { r -> Box(Modifier.padding(horizontal = 8.dp)) { StandingsLine(r, highlight = r.team.id in myTeams) } }
            }
        }
        item(key = "matchdayHead") {
            Text(stringResource(R.string.sports_this_matchday), style = stageText(16f, 800), color = palette.text, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp))
        }
        items(matches, key = { "m-" + it.key }) { m -> LeagueMatchRow(m, state.config.spoilerFree) { onOpenMatch(m) } }
    }
}

/** A row of "This matchday": LIVE or the day, "Frankfurt 2–1 Köln", then the minute or kick-off. */
@Composable
private fun LeagueMatchRow(m: Match, spoilerFree: Boolean, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    StageRow(onClick = onClick, height = 56.dp, modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.width(44.dp)) {
            if (m.isLive) LiveMark("") else Text(dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today), style = stageText(12f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            val title = if (m.state == MatchState.PRE || spoilerFree) MatchNotifications.pair(context, m) else MatchNotifications.scoreLine(context, m)
            Text(title, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = if (m.isLive) clockText(m) else if (m.state == MatchState.PRE) timeText(m.startMs) else m.statusText
            Text(time, style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
