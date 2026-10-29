package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.GuideSportItem
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/** One chip of the hub's first row: the followed teams, or one sport (none chosen = everything). */
sealed interface HubChip {
    data object MyTeams : HubChip
    data class One(val sport: Sport) : HubChip
}

/**
 * The Sports hub (the television's hub as built): today's counts, two multi-select chip rows (sports
 * and My teams, then the chosen sports' leagues; none = All) ending in ⟳, *Live now* with the featured
 * first card, *Sport on your channels* from the guide, and *Starting soon*. A tap opens the match (its
 * channels are found there); a long press follows or reminds. With nothing followed, a note and a button
 * into Settings → Sports and leagues.
 */
@Composable
fun SportsScreen(
    scrollToTop: SharedFlow<String>,
    route: String,
    onOpenMatch: (Match) -> Unit,
    onPlayChannel: (Long) -> Unit,
    onChooseSports: () -> Unit,
    vm: SportsViewModel = koinViewModel(),
) {
    val state by vm.hub.collectAsStateWithLifecycle()
    val config = state.config
    val now = System.currentTimeMillis()
    val playback = koinInject<SportsPlayback>()
    var chips by remember { mutableStateOf(playback.hubChips) }
    var leagues by remember { mutableStateOf(playback.hubLeagues) }
    LaunchedEffect(chips, leagues) { playback.hubChips = chips; playback.hubLeagues = leagues }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    // The sheet's Remind me names a channel when Find channels already found one for that match.
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }

    val myTeamKeys = remember(config.teams) { config.teams.map { it.key }.toSet() }
    fun Match.isMine() = competitors.any { FollowedTeam(sport, it.team.id, it.team.name).key in myTeamKeys }
    val chosenSports = chips.filterIsInstance<HubChip.One>().map { it.sport }.toSet()
    val sportLeagues = config.followedLeagues().mapNotNull(SportsCatalog::league).filter { it.sport in chosenSports }
        .sortedWith(compareBy({ config.rankOf(it.sport, it.key).first }, { config.rankOf(it.sport, it.key).second }))
    // A remembered league the profile no longer follows filters nothing.
    val activeLeagues = leagues.filterTo(HashSet()) { k -> sportLeagues.any { it.key == k } }
    fun toggle(c: HubChip) { chips = if (c in chips) chips - c else chips + c }
    fun toggleLeague(k: String) { leagues = if (k in activeLeagues) activeLeagues - k else activeLeagues + k }
    fun Match.passes(): Boolean {
        if (chips.isEmpty()) return true
        val bySport = sport in chosenSports && activeLeagues.filterTo(HashSet()) { k -> sportLeagues.any { it.key == k && it.sport == sport } }
            .let { mine -> mine.isEmpty() || feed in mine }
        return bySport || (HubChip.MyTeams in chips && isMine())
    }
    // ESPN's tennis and golf feeds list a whole tournament; "today" is what is live or starts today.
    val today = state.board.today.values.flatten().distinctBy { it.key }.filter { it.isLive || isToday(it.startMs) }
    val live = today.filter { it.isLive && it.passes() }
        .sortedWith(compareBy<Match>({ config.rankOf(it.sport, it.feed).first }, { config.rankOf(it.sport, it.feed).second }, { !it.isMine() }, { it.startMs }))
    val soon = state.board.matches.filter { it.state == MatchState.PRE && it.startMs > now && it.passes() }
        .sortedWith(compareBy<Match>({ config.rankOf(it.sport, it.feed).first }, { config.rankOf(it.sport, it.feed).second }, { it.startMs })).take(12)
    val onNow = state.onNow.values.flatten().take(6)
    val followedSports = remember(config) { vm.followedSports(config) }
    val context = LocalContext.current

    val listState = rememberLazyListState()
    listState.ObeyScrollToTop(route = route, scrollToTop = scrollToTop)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 14.dp)) {
                HubSubtitle(today.filter { it.passes() })
                if (state.board.paused) {
                    Text(stringResource(R.string.sports_paused), style = stageText(12.5f, 600), color = LocalStagePalette.current.warn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (config.followedLeagues().isNotEmpty()) {
            item(key = "chips") {
                LazyRow(
                    Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item { StageTool(onClick = { chips = emptySet(); leagues = emptySet() }, label = stringResource(R.string.content_epg_all), on = chips.isEmpty()) }
                    item { StageTool(onClick = { toggle(HubChip.MyTeams) }, label = stringResource(R.string.sports_my_teams), icon = MobileIcons.Star, on = HubChip.MyTeams in chips) }
                    items(followedSports, key = { it.name }) { s ->
                        StageTool(onClick = { toggle(HubChip.One(s)) }, label = "${sportEmoji(s)} ${sportLabel(s)}", on = HubChip.One(s) in chips)
                    }
                    // ⟳: build the whole board again; its progress shows in the status pill.
                    item {
                        StageTool(onClick = vm::rebuild, icon = MobileIcons.Refresh, contentDescription = stringResource(R.string.sports_refresh_data_now))
                    }
                }
            }
        }
        if (sportLeagues.size > 1) {
            item(key = "leagues") {
                LazyRow(
                    Modifier.padding(top = 8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item { StageTool(onClick = { leagues = emptySet() }, label = stringResource(R.string.content_epg_all), on = activeLeagues.isEmpty()) }
                    items(sportLeagues, key = { it.key }) { l ->
                        StageTool(onClick = { toggleLeague(l.key) }, label = leagueLabel(l), on = l.key in activeLeagues)
                    }
                }
            }
        }

        val nothing = live.isEmpty() && soon.isEmpty() && onNow.isEmpty()
        if (config.followedLeagues().isEmpty()) {
            item(key = "none") { NothingFollowedNote(onChooseSports) }
        } else if (nothing && !state.rebuilding) {
            item(key = "empty") {
                val unreachable = state.board.failing.isNotEmpty() && state.board.today.isEmpty()
                Text(
                    stringResource(if (unreachable) R.string.sports_empty_unreachable else R.string.sports_empty_nothing),
                    style = stageText(14f, 600), color = LocalStagePalette.current.muted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 40.dp),
                )
            }
        }

        if (live.isNotEmpty()) {
            item(key = "live") {
                Column {
                    RowHeader(stringResource(R.string.sports_live_now), live = true)
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(live, key = { _, m -> m.key }) { i, m ->
                            MatchCard(m, featured = i == 0, config = config, reminded = false, onClick = { onOpenMatch(m) }, onLongClick = { menuFor = m })
                        }
                    }
                }
            }
        }
        if (onNow.isNotEmpty()) {
            item(key = "onNowHead") { RowHeader(stringResource(R.string.sports_on_channels_now_mobile)) }
            items(onNow, key = { "on-" + it.title + it.startMs + it.channels.first().id }) { item ->
                OnChannelsRow(item, now) { onPlayChannel(item.channels.first().id) }
            }
        }
        if (soon.isNotEmpty()) {
            item(key = "soonHead") { RowHeader(stringResource(R.string.sports_starting_soon)) }
            items(soon, key = { "soon-" + it.key }) { m ->
                SoonRow(
                    m,
                    reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(context, m)) in state.reminders,
                    onClick = { onOpenMatch(m) },
                    onLongClick = { menuFor = m },
                )
            }
        }
    }

    menuFor?.let { m ->
        FollowRemindSheet(
            match = m, config = config, channels = menuChannels,
            onFollow = { vm.toggleTeam(it) },
            onRemind = { vm.remind(m, menuChannels) },
            onDismiss = { menuFor = null },
        )
    }
}

/** "**Today** · 6 live · 41 matches · Scores: ESPN" (no channel count — channels are found per match). */
@Composable
private fun HubSubtitle(today: List<Match>) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val sep = stringResource(R.string.content_epg_bits_separator)
    val liveText = pluralStringResource(R.plurals.sports_hub_live, today.count { it.isLive }, today.count { it.isLive })
    val matchesText = pluralStringResource(R.plurals.sports_hub_matches, today.size, today.size)
    val todayLabel = stringResource(R.string.content_epg_today)
    val credit = stringResource(R.string.sports_scores_espn)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(todayLabel) }
            append(sep); append(liveText); append(sep); append(matchesText); append(sep)
            withStyle(SpanStyle(color = palette.dim)) { append(credit) }
        },
        style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The hub with nothing followed: what to do, where, why to choose with care — and the way there. */
@Composable
private fun NothingFollowedNote(onChooseSports: () -> Unit) {
    val palette = LocalStagePalette.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sports_empty_nothing_followed), style = stageText(16f, 800), color = palette.text)
        Text(stringResource(R.string.sports_empty_choose_in), style = stageText(13f, 600), color = palette.body)
        Text(stringResource(R.string.sports_load_time_warning), style = stageText(12f, 500, lineHeight = 1.5f), color = palette.muted)
        StageButton(onClick = onChooseSports, text = stringResource(R.string.sports_leagues), icon = MobileIcons.Sports, style = StageButtonStyle.PRIMARY, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A row's heading; *Live now* carries the red dot. */
@Composable
private fun RowHeader(title: String, live: Boolean = false) {
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) LiveMark("")
        Text(title, style = stageText(17f, 800), color = LocalStagePalette.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Sport on your channels": the logo, "Handball · Kiel – Magdeburg", "DYN SPORT 1 · until 20:45" and how far it is. */
@Composable
private fun OnChannelsRow(item: GuideSportItem, now: Long, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val ch = item.channels.first()
    val sep = stringResource(R.string.content_epg_bits_separator)
    StageRow(onClick = onClick, height = 64.dp, modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.size(46.dp, 32.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            ChannelLogoImage(url = ch.logoUrl, modifier = Modifier.fillMaxSize(), plateOn = palette.page, fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, palette.dim) })
        }
        Column(Modifier.weight(1f)) {
            Text(guideSportLabel(item.sport) + sep + item.title, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tv.own.owntv.core.live.ProviderTags.parse(ch.name).name + sep + stringResource(R.string.sports_until, timeText(item.stopMs)),
                style = stageText(12f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val span = (item.stopMs - item.startMs).coerceAtLeast(1)
            StageProgress(((now - item.startMs).toFloat() / span).coerceIn(0f, 1f), Modifier.padding(top = 4.dp).fillMaxWidth(0.6f), thin = true)
        }
    }
}

/** "Starting soon": kick-off, both crests, the pair, and the bell lit once a reminder is set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoonRow(m: Match, reminded: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(timeText(m.startMs), style = stageText(13f, 800), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            dayOf(m.startMs, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])?.let { Text(it, style = stageText(11f, 600), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        m.home?.let { Crest(it.team, 24) }
        m.away?.let { Crest(it.team, 24) }
        Column(Modifier.weight(1f)) {
            Text(MatchNotifications.pair(context, m), style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(leagueName(m), style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StageIcon(MobileIcons.Bell, 18.dp, if (reminded) stageAccent.accent else palette.dim)
    }
}
