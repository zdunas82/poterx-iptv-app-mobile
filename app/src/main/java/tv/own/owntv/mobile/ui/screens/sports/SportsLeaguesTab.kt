package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.sports.LeagueCard
import tv.own.owntv.core.sports.LeagueCards
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsWindow
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.RaceSession
import tv.own.owntv.core.sports.model.RankingKind
import tv.own.owntv.core.sports.model.RankingRow
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTagTone
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText
import java.time.LocalDate
import java.util.Locale

/** A league's own tabs, which depend on its sport. */
private enum class LeaguePane { TABLE, FIXTURES, RESULTS, DRIVERS, CONSTRUCTORS, RACES, RANKINGS, MATCHES }

/**
 * Football and the other team sports: Table | Fixtures | Results (no table: Fixtures | Results). F1:
 * Drivers | Constructors | Races; the other racing series Drivers | Races. ATP / WTA: Rankings | Matches.
 */
private fun panesOf(league: League): List<LeaguePane> = when {
    league.sport == Sport.RACING -> listOfNotNull(LeaguePane.DRIVERS, LeaguePane.CONSTRUCTORS.takeIf { league.slug == "f1" }, LeaguePane.RACES)
    SportsWindow.hasRanking(league) -> listOf(LeaguePane.RANKINGS, LeaguePane.MATCHES)
    SportsWindow.hasTable(league) -> listOf(LeaguePane.TABLE, LeaguePane.FIXTURES, LeaguePane.RESULTS)
    else -> listOf(LeaguePane.FIXTURES, LeaguePane.RESULTS)
}

private val LeaguePane.label: Int get() = when (this) {
    LeaguePane.TABLE -> R.string.sports_tab_table
    LeaguePane.FIXTURES -> R.string.sports_tab_fixtures
    LeaguePane.RESULTS -> R.string.sports_tab_results
    LeaguePane.DRIVERS -> R.string.sports_tab_drivers
    LeaguePane.CONSTRUCTORS -> R.string.sports_tab_constructors
    LeaguePane.RACES -> R.string.sports_tab_races
    LeaguePane.RANKINGS -> R.string.sports_tab_rankings
    LeaguePane.MATCHES -> R.string.sports_tab_matches
}

/**
 * The Leagues tab. With All, My teams or a sport with several leagues: a card per followed league (who
 * leads, what is next), one column; a tap opens that league (its chip turns on), a long press follows or
 * unfollows it. With one league chosen — or a sport whose only followed league it is — that league opens
 * here: its name, Following, and its own tabs.
 */
@Composable
internal fun SportsLeaguesTab(
    vm: SportsViewModel,
    frame: SportsFrame,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
) {
    val filter = frame.filter
    val oneKey = filter.leagueKeys.singleOrNull()
    LaunchedEffect(oneKey, filter.leagueKeys) {
        vm.showLeague(oneKey)
        vm.showCards(if (oneKey == null) filter.leagueKeys else emptyList())
        if (oneKey != null) vm.leagueTabFor(oneKey)
    }
    val league = oneKey?.let(SportsCatalog::league)
    val locale = LocalConfiguration.current.locales[0]
    val myTeamsOnly = filter.myTeams && filter.chips.size == 1

    if (league == null) {
        val state = vm.leagueCards.collectAsStateWithLifecycle().value
        var menuFor by remember { mutableStateOf<League?>(null) }
        val teamIds = filter.config.teams.map { it.sport to it.id }.toSet()
        fun mineIn(card: LeagueCard) = state?.board?.standings?.get(card.league.key)?.groups?.flatMap { it.rows }
            ?.firstOrNull { (card.league.sport to it.team.id) in teamIds }
        val cards = state?.cards.orEmpty().filter { c -> !myTeamsOnly || mineIn(c) != null || c.next?.let(filter::isMine) == true }
        SportsTabPage(
            frame,
            subtitle = {
                val head = if (myTeamsOnly) stringResource(R.string.sports_my_teams_leagues) else stringResource(R.string.sports_tab_leagues)
                val scope = filter.oneSport?.let { sportLabel(it) }
                if (scope != null) SportsSubtitle(head, scope) else SportsSubtitle(head)
            },
        ) {
            items(cards, key = { "c:" + it.league.key }) { c ->
                LeagueCardView(
                    card = c, locale = locale,
                    top = state?.board?.standings?.get(c.league.key)?.groups?.flatMap { it.rows }?.firstOrNull { it.team.id == c.leader?.id },
                    mine = if (myTeamsOnly) mineIn(c) else null,
                    onClick = { vm.setChips(setOf(HubChip.One(c.league.sport)), setOf(c.league.key)) },
                    onHold = { menuFor = c.league },
                )
            }
        }
        menuFor?.let { l ->
            val following = l.key in filter.config.followedLeagues()
            MobileBottomSheet(onDismissRequest = { menuFor = null }) {
                StageSheetHeader(leagueLabel(l), subtitle = sportLabel(l.sport))
                StageMenuItem(
                    label = stringResource(if (following) R.string.sports_unfollow_league else R.string.sports_follow_league),
                    icon = if (following) MobileIcons.StarBorder else MobileIcons.Star,
                    onClick = { vm.toggleLeague(l.key); menuFor = null },
                )
            }
        }
        return
    }

    val view = vm.leagueView.collectAsStateWithLifecycle().value?.takeIf { it.league.key == league.key }
    val panes = panesOf(league)
    val paneIndex by vm.leagueTab.collectAsStateWithLifecycle()
    val pane = panes.getOrNull(paneIndex) ?: panes.first()
    val config = filter.config
    val reminders by vm.reminderSet.collectAsStateWithLifecycle()
    val revealed = remember { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val context = LocalContext.current
    val row: LazyListScope.(List<Match>) -> Unit = { list ->
        items(list, key = { "m:" + it.key }) { m ->
            val hidden = config.spoilerFree && m.state == MatchState.POST && m.key !in revealed
            MatchListRow(
                m = m, mine = filter.isMine(m), hidden = hidden,
                reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(context, m)) in reminders,
                onClick = { if (hidden) revealed.add(m.key) else onOpenMatch(m) },
                onHold = { menuFor = m },
                onBell = { vm.remindFound(m) },
                day = dateText(localDate(m.startMs), locale, "EEEd"),
            )
        }
    }

    SportsTabPage(frame, subtitle = { SportsSubtitle(leagueLabel(league), sportLabel(league.sport)) }) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(leagueLabel(league), style = stageText(21f, 800), color = LocalStagePalette.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val following = vm.follows(config, league.key)
                StageTool(
                    onClick = { vm.toggleLeague(league.key) },
                    label = stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                    icon = if (following) MobileIcons.Star else MobileIcons.StarBorder, on = following,
                )
            }
        }
        item(key = "panes") {
            LazyRow(Modifier.padding(top = 12.dp, bottom = 6.dp), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(panes.size) { i -> StageTool(onClick = { vm.setLeagueTab(league.key, i) }, label = stringResource(panes[i].label), on = panes[i] == pane) }
            }
        }
        if (view == null) return@SportsTabPage
        val myTeamIds = config.teams.filter { it.sport == league.sport }.map { it.id }.toSet()
        when (pane) {
            LeaguePane.TABLE -> {
                item(key = "tableHead") { Box(Modifier.padding(horizontal = 8.dp)) { StandingsHeader() } }
                val groups = view.board.standings[league.key]?.groups.orEmpty()
                groups.forEach { g ->
                    if (groups.size > 1) item(key = "g:" + g.name) { Heading(g.name) }
                    items(g.rows, key = { "t:${g.name}:${it.team.id}" }) { r ->
                        val mine = r.team.id in myTeamIds
                        val ref = teamRefOf(league, r.team)
                        Box(Modifier.padding(horizontal = 8.dp)) { StandingsLine(r, highlight = mine, star = mine, onClick = ref?.let { { onOpenTeam(it) } }) }
                    }
                }
            }
            LeaguePane.FIXTURES, LeaguePane.RESULTS -> {
                val list = if (pane == LeaguePane.FIXTURES) view.fixtures else view.results
                byRound(list, locale).forEach { (heading, ms) ->
                    item(key = "w:$heading") { Heading(heading) }
                    row(ms)
                }
            }
            LeaguePane.DRIVERS, LeaguePane.CONSTRUCTORS, LeaguePane.RANKINGS -> {
                val kind = when (pane) {
                    LeaguePane.CONSTRUCTORS -> RankingKind.CONSTRUCTORS
                    LeaguePane.DRIVERS -> RankingKind.DRIVERS
                    else -> RankingKind.PLAYERS
                }
                if (league.sport == Sport.RACING) {
                    nextWeekend(view)?.let { e ->
                        item(key = "strip") { NextWeekendStrip(e, locale) { vm.setLeagueTab(league.key, panes.indexOf(LeaguePane.RACES)) } }
                    }
                }
                val rows = view.board.rankings[league.key]?.groups?.firstOrNull { it.kind == kind }?.rows.orEmpty()
                item(key = "rankHead") { RankingHeader(stringResource(pane.label)) }
                items(rows, key = { "r:${it.rank}:${it.team.id}" }) { r -> RankingLine(r, rows.first().points) }
            }
            LeaguePane.RACES -> {
                val events = raceWeekends(view.fixtures + view.results)
                val next = nextWeekend(view)
                next?.let { e ->
                    item(key = "nextRace") { NextWeekendCard(e, locale, onOpenMatch) }
                }
                items(events, key = { "e:${it.name}:${it.sessions.first().key}" }) { e ->
                    RaceRow(e, isNext = e.main.key == next?.main?.key, locale = locale) { onOpenMatch(e.main) }
                }
            }
            LeaguePane.MATCHES -> row(view.onBoard.ifEmpty { view.fixtures.take(30) })
        }
    }

    menuFor?.let { m ->
        FollowRemindSheet(
            match = m, config = config, channels = menuChannels,
            onFollow = { vm.toggleTeam(it) },
            onRemind = { vm.remind(m, menuChannels) },
            onOpenTeam = onOpenTeam,
            onDismiss = { menuFor = null },
        )
    }
}

/** `.pgh`: a list heading, 11.5 / 800 caps-spaced, dim. */
@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]), style = stageText(11.5f, 800, 0.1.em), color = LocalStagePalette.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

/** `.pcard`: the card shape of the Leagues tab, F1's strips and the team page. */
@Composable
internal fun SportsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onHold: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val palette = LocalStagePalette.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.ink.copy(alpha = 0.06f))
            .drawBehind { drawInnerRing(palette.ink.copy(alpha = 0.07f), 1.dp.toPx(), 18.dp.toPx()) }
            .stageClickable(interaction, onClick, onHold)
            .padding(horizontal = 15.dp, vertical = 14.dp),
    ) { content() }
}

/**
 * A league card: "⚽ Football ›", the league, "🏆 Man City · 21 Pts" (win–loss sports: "6–1"; cricket:
 * no table), "Next: Today 15:00 · Bournemouth – Sunderland", and with My teams the team's place.
 */
@Composable
private fun LeagueCardView(card: LeagueCard, locale: Locale, top: StandingsRow?, mine: StandingsRow?, onClick: () -> Unit, onHold: () -> Unit) {
    val palette = LocalStagePalette.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val pts = stringResource(R.string.sports_table_points)
    SportsCard(onClick = onClick, onHold = onHold) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${sportEmoji(card.league.sport)} ${sportLabel(card.league.sport)}", style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                StageIcon(MobileIcons.KeyboardArrowRight, 16.dp, stageAccent.accent)
            }
            Text(leagueLabel(card.league), style = stageText(17f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            val lead = when {
                card.league.sport == Sport.CRICKET -> stringResource(R.string.sports_no_table_series)
                // No league points in these sports: the leader's wins and losses ("6–2").
                card.league.sport in LeagueCards.RECORD_SPORTS && top != null -> "🏆 " + top.team.shortName + sep + "${top.won}–${top.lost}"
                card.leader != null -> "🏆 " + card.leader!!.shortName + (card.leaderPoints?.let { "$sep$it $pts" } ?: "")
                else -> null
            }
            lead?.let { Text(it, style = stageText(13.5f, 500), color = palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
            card.next?.let { n ->
                val what = whenText(n.startMs, locale) + sep + MatchNotifications.pair(LocalContext.current, n)
                Text(stringResource(R.string.sports_next, what), style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            mine?.let { r ->
                Text("★ ${r.team.shortName}$sep#${r.rank}$sep${r.points} $pts", style = stageText(12.5f, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** "Today 15:00", "Sat 24 Oct 16:00": when a match starts. */
@Composable
private fun whenText(ms: Long, locale: Locale): String {
    val date = localDate(ms)
    val day = if (date == LocalDate.now()) stringResource(R.string.content_epg_today) else dateText(date, locale)
    return day + " " + timeText(ms)
}

/** `#  Drivers  Gap  Pts`. */
@Composable
private fun RankingHeader(name: String) {
    val palette = LocalStagePalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = stageText(11.5f, 600), color = palette.muted, modifier = Modifier.width(30.dp))
        Text(name, style = stageText(11.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sports_table_points), style = stageText(11.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn * 2))
    }
}

/** One place in a ranking: rank, initials or the constructor's letters, the name and country, the gap to the leader, the points. */
@Composable
private fun RankingLine(row: RankingRow, leaderPoints: Int) {
    val palette = LocalStagePalette.current
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.rank.toString(), style = stageText(13.5f, 700), color = palette.body, modifier = Modifier.width(30.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // No crest: a driver by initials, a constructor by its first letters ("MER"); ESPN's own abbreviation for constructors is not theirs.
            Crest(row.team.copy(abbreviation = if (row.team.isAthlete) "" else row.team.shortName.take(3).uppercase(Locale.ROOT)), 22)
            Text(row.team.name, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            row.team.country?.let { Text(it, style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        val gap = row.points - leaderPoints
        Text(if (gap == 0) "—" else "−${-gap}", style = stageText(13f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn))
        Text(row.points.toString(), style = stageText(13.5f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn))
    }
}

private val RankColumn = 46.dp

// --- Racing ------------------------------------------------------------------------------------------

/** A race weekend: its sessions in order; [main] is the race itself (else the last session). */
private class RaceWeekend(val name: String, val sessions: List<Match>) {
    val main: Match = sessions.lastOrNull { RaceSession.of(it.subtitle) == RaceSession.RACE } ?: sessions.last()
}

/** The season's sessions grouped into weekends by their event name, in date order. */
private fun raceWeekends(sessions: List<Match>): List<RaceWeekend> =
    sessions.sortedBy { it.startMs }.groupBy { it.title }.map { (name, list) -> RaceWeekend(name, list) }.sortedBy { it.sessions.first().startMs }

/** The first weekend with a session still to run. */
private fun nextWeekend(view: LeagueView): RaceWeekend? =
    raceWeekends(view.fixtures + view.results).firstOrNull { e -> e.sessions.any { it.state != MatchState.POST } }

/** Drivers and Constructors: 🏁 the next weekend, its dates and the race's start; a tap opens Races. */
@Composable
private fun NextWeekendStrip(e: RaceWeekend, locale: Locale, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    SportsCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏁", style = stageText(20f, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.weight(1f)) {
                Text(e.name, style = stageText(14f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val race = sessionLabel(e.main)?.let { "$it ${dateText(localDate(e.main.startMs), locale, "EEE")} ${timeText(e.main.startMs)}" }
                Text(listOfNotNull(rangeText(e.sessions, locale), race).joinToString(sep), style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StageIcon(MobileIcons.KeyboardArrowRight, 16.dp, stageAccent.accent)
        }
    }
}

/** Races: NEXT RACE WEEKEND and its dates, the event and its circuit, then every session in local time. */
@Composable
private fun NextWeekendCard(e: RaceWeekend, locale: Locale, onOpenMatch: (Match) -> Unit) {
    val palette = LocalStagePalette.current
    SportsCard {
        Column {
            Row {
                Text(stringResource(R.string.sports_next_race_weekend).uppercase(locale), style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(rangeText(e.sessions, locale).orEmpty(), style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(e.name, style = stageText(17f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            e.main.venue?.let { Text(it, style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            e.sessions.forEach { s ->
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp)
                        .drawBehind { drawRect(palette.ink.copy(alpha = 0.05f), size = size.copy(height = 1.dp.toPx())) }
                        .stageClickable(interaction, { onOpenMatch(s) }, null)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dateText(localDate(s.startMs), locale, "EEE"), style = stageText(13.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(44.dp))
                    Text(sessionLabel(s) ?: s.subtitle.orEmpty(), style = stageText(13.5f, 600), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (s.isLive) LiveMark(clockText(s)) else Text(timeText(s.startMs), style = stageText(13.5f, 600), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A weekend of the season: the race and its dates, 🏆 and the winner once run, NEXT on the coming one. */
@Composable
private fun RaceRow(e: RaceWeekend, isNext: Boolean, locale: Locale, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .then(if (isNext) Modifier.background(stageAccent.accent.copy(alpha = 0.08f)) else Modifier)
            .stageClickable(interaction, onClick, null)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = stageText(14.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rangeText(e.sessions, locale).orEmpty(), style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val winner = e.main.takeIf { it.state == MatchState.POST }?.competitors?.firstOrNull { it.winner == true }?.team?.name
        when {
            winner != null -> Text("🏆 $winner", style = stageText(13f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            isNext -> StageTag(stringResource(R.string.sports_next_badge).uppercase(locale), tone = StageTagTone.ACCENT)
        }
    }
}
