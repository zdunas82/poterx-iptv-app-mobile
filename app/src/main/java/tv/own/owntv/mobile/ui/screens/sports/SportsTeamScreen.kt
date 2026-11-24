package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

/** A finished match from one side's view. */
private enum class Outcome { W, D, L }

private fun outcomeOf(m: Match, teamId: String): Outcome? {
    if (m.state != MatchState.POST || m.competitors.size != 2) return null
    return when {
        m.competitors.any { it.team.id == teamId && it.winner == true } -> Outcome.W
        m.competitors.any { it.team.id != teamId && it.winner == true } -> Outcome.L
        else -> Outcome.D
    }
}

/**
 * A team's page: crest, name, "Football · Premier League" and In My teams; the next match (Match page,
 * Remind me), the last five and its place in the table (a tap opens that table in the Leagues tab); then
 * Fixtures | Results — ESPN's schedule with the cups the team plays from the saved calendar months —
 * grouped by month. A tap on a row opens the match (or shows a hidden score first), a long press the
 * sheet. Spoiler-free hides scores and W / D / L.
 */
@Composable
fun SportsTeamScreen(
    ref: TeamRef,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onOpenLeague: (String) -> Unit,
    vm: SportsViewModel = koinViewModel(),
) {
    val state = remember(ref) { vm.teamPage(ref) }.collectAsStateWithLifecycle(null).value
    val config by vm.sportsConfig.collectAsStateWithLifecycle()
    val reminders by vm.reminderSet.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val team = ref.team
    val followed = FollowedTeam(ref.league.sport, team.id, team.name, team.abbreviation)
    val inMyTeams = config.teams.any { it.key == followed.key }
    var resultsTab by rememberSaveable(ref) { mutableStateOf(false) }
    val revealed = remember(ref) { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val schedule = state?.schedule
    val next = schedule?.fixtures?.firstOrNull()
    val hiddenOf = { m: Match -> config.spoilerFree && m.state == MatchState.POST && m.key !in revealed }
    val sep = stringResource(R.string.content_epg_bits_separator)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        // --- The team ------------------------------------------------------------------------------
        item(key = "hero") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Crest(team, 58)
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = stageText(24f, 800), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sportLabel(ref.league.sport) + sep + leagueLabel(ref.league), style = stageText(13f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                StageTool(
                    onClick = { vm.toggleTeam(followed) },
                    label = stringResource(if (inMyTeams) R.string.sports_in_my_teams else R.string.sports_follow),
                    icon = if (inMyTeams) MobileIcons.Star else MobileIcons.StarBorder, on = inMyTeams,
                )
            }
        }
        // --- Next match, last five, league position ------------------------------------------------
        next?.let { n ->
            item(key = "next") {
                SportsCard {
                    Column {
                        Row {
                            Text((stringResource(R.string.sports_next_match) + sep + leagueName(n)).uppercase(locale), style = stageText(12f, 600, 0.04.em), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(dayOf(n.startMs, locale) ?: stringResource(R.string.content_epg_today), style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        NextMatch(n)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            StageTool(onClick = { onOpenMatch(n) }, label = stringResource(R.string.sports_hint_match_page))
                            if (n.state == MatchState.PRE && n.startMs > System.currentTimeMillis()) {
                                StageTool(onClick = { vm.remindFound(n) }, label = stringResource(R.string.content_remind_me), icon = MobileIcons.Bell)
                            }
                        }
                    }
                }
            }
        }
        val five = schedule?.results.orEmpty().take(5).mapNotNull { outcomeOf(it, team.id) }
        val rows = state?.board?.standings?.get(ref.league.key)?.groups?.firstOrNull { g -> g.rows.any { it.team.id == team.id } }?.rows
        val place = rows?.firstOrNull { it.team.id == team.id }
        val showFive = five.isNotEmpty() && !config.spoilerFree
        if (showFive || place != null) {
            item(key = "form") {
                SportsCard(onClick = place?.let { { onOpenLeague(ref.league.key) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showFive) {
                            Text(stringResource(R.string.sports_last_five).uppercase(locale), style = stageText(12f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { five.forEach { OutcomeBadge(it, 26) } }
                        }
                        Box(Modifier.weight(1f))
                        place?.let { r ->
                            Text("#${r.rank}$sep${r.points} ${stringResource(R.string.sports_table_points)}", style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StageIcon(MobileIcons.KeyboardArrowRight, 16.dp, stageAccent.accent)
                        }
                    }
                }
            }
        }
        // --- Fixtures | Results --------------------------------------------------------------------
        item(key = "tabs") {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StageTool(onClick = { resultsTab = false }, label = stringResource(R.string.sports_tab_fixtures), on = !resultsTab)
                StageTool(onClick = { resultsTab = true }, label = stringResource(R.string.sports_tab_results), on = resultsTab)
            }
        }
        val list = if (resultsTab) schedule?.results.orEmpty() else schedule?.fixtures.orEmpty()
        when {
            schedule == null -> Unit
            list.isEmpty() && schedule.failed -> item(key = "failed") {
                Text(stringResource(R.string.sports_empty_unreachable), style = stageText(13.5f, 500), color = palette.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
            else -> list.groupBy { monthOf(it.startMs) }.forEach { (month, ms) ->
                item(key = "mh:$month") {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale) + if (month.year != java.time.Year.now().value) " ${month.year}" else "",
                        style = stageText(11.5f, 800, 0.1.em), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(ms, key = { "m:" + it.key }) { m ->
                    val hidden = hiddenOf(m)
                    MatchListRow(
                        m = m, mine = false, hidden = hidden,
                        reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(context, m)) in reminders,
                        onClick = { if (hidden) revealed.add(m.key) else onOpenMatch(m) },
                        onHold = { menuFor = m },
                        onBell = { vm.remindFound(m) },
                        day = dateText(localDate(m.startMs), locale, "EEEd"),
                        sub = leagueName(m),
                        next = m.key == next?.key,
                        outcome = outcomeOf(m, team.id)?.let { o -> { OutcomeBadge(o, 24) } },
                    )
                }
            }
        }
    }

    menuFor?.let { m ->
        FollowRemindSheet(
            match = m, config = config, channels = menuChannels,
            onFollow = { vm.toggleTeam(it) },
            onRemind = { vm.remind(m, menuChannels) },
            onOpenTeam = { if (it.team.id != team.id) onOpenTeam(it) },
            onDismiss = { menuFor = null },
        )
    }
}

private fun monthOf(ms: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** `.pnm`: both sides with 52 dp crests, the kick-off big between them (the live minute once under way), the venue under it. */
@Composable
private fun NextMatch(m: Match) {
    val palette = LocalStagePalette.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOfNotNull(m.home, m.away).forEachIndexed { i, c ->
            if (i == 1) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (m.isLive) LiveMark(clockText(m), 14f)
                    else Text(timeText(m.startMs), style = stageText(30f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    m.venue?.let { Text(it, style = stageText(12f, 500), color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
            Column(Modifier.size(width = 110.dp, height = 90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Crest(c.team, 52)
                Text(teamLabel(c.team), style = stageText(14f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.wdl`: W, D or L on green, grey or red. */
@Composable
private fun OutcomeBadge(o: Outcome, size: Int) {
    val palette = LocalStagePalette.current
    val (color, label) = when (o) {
        Outcome.W -> TierGuide to R.string.sports_table_won
        Outcome.D -> palette.muted to R.string.sports_table_drawn
        Outcome.L -> SportsLive to R.string.sports_table_lost
    }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(stringResource(label), style = stageText(11.5f, 800), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
