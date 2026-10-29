package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.sports.MatchAlertHost
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.WatchWindow
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchEvent
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.core.sports.model.StandingsTable
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/** The match page's tabs: Watch live first, then the television's details (Form in Head to head's place). */
private enum class MatchTab { WATCH, TIMELINE, STATS, LINEUPS, TABLE, FORM, BROADCASTS }

/**
 * A match (the television's match page as built). The league line and Following, the score box
 * (time · round · venue, scorers; spoiler-free until a tap), then the tabs. *Watch live* finds the
 * channels only when asked, within its window; a tap on a channel plays it full screen and
 * Back returns here; a long press remembers it for the competition; *Filter channels* opens the
 * sheet. The details come from the stored board.
 */
@Composable
fun SportsMatchScreen(
    matchKey: String,
    onPlayChannel: (Long) -> Unit,
    onOpenLeague: (String) -> Unit,
    onAddPlaylist: () -> Unit,
    vm: SportsViewModel = koinViewModel(),
) {
    LaunchedEffect(matchKey) { vm.matchOf(matchKey)?.let(vm::open) }
    val page by vm.matchPage.collectAsStateWithLifecycle()
    val state = page ?: return
    val m = state.match
    var tab by rememberSaveable { mutableStateOf(MatchTab.WATCH) }
    var revealed by remember(m.key) { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var rememberFor by remember { mutableStateOf<WatchOption?>(null) }
    val palette = LocalStagePalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val shown = state.list.shown
    val play: (WatchOption) -> Unit = { o -> vm.playedFrom(m, shown, o.channel); onPlayChannel(o.channel.id) }
    val listState = rememberLazyListState()

    val tabs = MatchTab.entries.filter {
        (it != MatchTab.TABLE || state.standings?.groups?.any { g -> g.rows.isNotEmpty() } == true) &&
            (it != MatchTab.STATS || !state.summary?.stats.isNullOrEmpty()) &&
            (it != MatchTab.LINEUPS || !state.summary?.lineups.isNullOrEmpty()) &&
            (it != MatchTab.FORM || !state.summary?.form.isNullOrEmpty())
    }
    // Items above the channel list (crumb, score, tabs, the list's head), so Back from the player can
    // scroll to the channel that was playing.
    val rowsStart = 3 + WATCH_HEAD_ITEMS
    LaunchedEffect(shown.isNotEmpty(), tab) {
        val i = shown.indexOfFirst { it.channel.id == vm.lastPlayedChannelId }
        if (tab == MatchTab.WATCH && i >= 0) listState.scrollToItem(rowsStart + i)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        item(key = "crumb") {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(leagueName(m), style = stageText(12.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val following = vm.follows(state.config, m.feed)
                StageTool(
                    onClick = { vm.toggleLeague(m.feed) },
                    label = stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                    icon = MobileIcons.Star, on = following,
                )
            }
        }
        item(key = "score") {
            ScoreBox(m, state.summary, hidden = state.config.spoilerFree && !revealed, onReveal = { revealed = true })
            // The live score card: only on the phone, while the match is still to be decided.
            if (MatchAlertHost.backgroundAllowed && state.config.alerts.liveCard && (m.isLive || m.state == MatchState.PRE)) {
                StageTool(
                    onClick = { MatchAlertHost.requestNotificationPermission?.invoke(); tv.own.owntv.core.sports.LiveMatchCardService.start(context, m) },
                    label = stringResource(R.string.sports_live_card), icon = MobileIcons.Bell,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                )
            }
        }
        item(key = "tabs") {
            LazyRow(Modifier.padding(top = 12.dp, bottom = 6.dp), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tabs) { t -> StageTool(onClick = { tab = t }, label = stringResource(t.label), on = tab == t) }
            }
        }
        when (tab) {
            MatchTab.WATCH -> watchLive(state, sep, play = play, onHold = { rememberFor = it }, onFind = vm::findChannels, onFilters = { filters = true }, onAddPlaylist = onAddPlaylist)
            MatchTab.TIMELINE -> items((state.summary?.timeline ?: m.events).asReversed()) { e -> TimelineRow(e, m) }
            MatchTab.STATS -> items(state.summary?.stats.orEmpty()) { s -> StatRow(s.label, s.home, s.away) }
            MatchTab.LINEUPS -> item { Lineups(m, state.summary) }
            MatchTab.TABLE -> item { TablePreview(state.standings, m) { onOpenLeague(tv.own.owntv.core.sports.model.League.keyOf(m.sport, m.leagueSlug)) } }
            MatchTab.FORM -> item { Form(m, state.summary) }
            MatchTab.BROADCASTS -> item { Broadcasts(m, (state.search as? ChannelSearch.Found)?.options) }
        }
    }

    if (filters && state.hasPlaylist) {
        SportsFilterSheet(state, onFilter = vm::setFilter, onRemember = vm::setRememberFilters, onDismiss = { filters = false })
    }
    rememberFor?.let { o ->
        MobileBottomSheet(onDismissRequest = { rememberFor = null }) {
            StageSheetHeader(o.facets.name, subtitle = leagueName(m))
            StageMenuItem(
                label = stringResource(R.string.sports_always_use, leagueName(m)),
                icon = MobileIcons.Star,
                onClick = { vm.rememberChannel(m, o.channel); rememberFor = null },
            )
            StageMenuItem(label = stringResource(R.string.content_play), icon = MobileIcons.PlayArrow, onClick = { rememberFor = null; play(o) })
        }
    }
}

private val MatchTab.label: Int get() = when (this) {
    MatchTab.WATCH -> R.string.sports_watch_live
    MatchTab.TIMELINE -> R.string.sports_tab_timeline
    MatchTab.STATS -> R.string.sports_tab_stats
    MatchTab.LINEUPS -> R.string.sports_tab_lineups
    MatchTab.TABLE -> R.string.sports_tab_table
    MatchTab.FORM -> R.string.sports_table_form
    MatchTab.BROADCASTS -> R.string.sports_tab_broadcasts
}

/** *Watch live*: no playlist, outside the window, Find channels, or the ranked list with its filter chips. */
private fun LazyListScope.watchLive(
    state: MatchPageState,
    sep: String,
    play: (WatchOption) -> Unit,
    onHold: (WatchOption) -> Unit,
    onFind: () -> Unit,
    onFilters: () -> Unit,
    onAddPlaylist: () -> Unit,
) {
    val m = state.match
    if (!state.hasPlaylist) {
        item(key = "noPlaylist") { NoPlaylistNote(onAddPlaylist) }
        return
    }
    when (val s = state.search) {
        is ChannelSearch.Found -> Unit
        is ChannelSearch.Closed -> { item(key = "closed") { ClosedNote(s.window) }; return }
        else -> {
            item(key = "find") { FindChannels(busy = s == ChannelSearch.Searching || s == ChannelSearch.Checking, searching = s == ChannelSearch.Searching, onFind = onFind) }
            return
        }
    }
    if (state.list.total == 0) {
        item(key = "none") { Note(stringResource(R.string.sports_not_on_channels)) }
        return
    }
    // One item above the rows (WATCH_HEAD_ITEMS): the count, the language first, and the filter chips.
    item(key = "watchHead") {
        val palette = LocalStagePalette.current
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.sports_watch_live_count, state.list.total, state.list.total),
                    style = stageText(15f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                state.preferredLanguages.firstOrNull()?.let { lang ->
                    Text(stringResource(R.string.sports_language_first, languageName(lang)) , style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FilterChips(state, onFilters, Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
    }
    items(state.list.shown, key = { it.channel.id }) { o -> ChannelRow(o, m, onClick = { play(o) }, onHold = { onHold(o) }) }
    if (state.list.hidden > 0) {
        item(key = "hidden") {
            val names = state.list.hiddenLanguages.map { if (it == WatchFilter.UNKNOWN) stringResource(R.string.sports_unknown) else languageName(it) }
            Text(
                pluralStringResource(R.plurals.sports_hidden, state.list.hidden, state.list.hidden) + (if (names.isEmpty()) "" else " (${names.joinToString(sep)})"),
                style = stageText(12f, 500), color = LocalStagePalette.current.dim,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Items [watchLive] draws above its first channel row. */
private const val WATCH_HEAD_ITEMS = 1

/** "German, English" · the country · the quality floor · Filter channels — any of them opens the sheet. */
@Composable
private fun FilterChips(state: MatchPageState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val f = state.filter
    val langs = (f.languages - WatchFilter.UNKNOWN).map { languageName(it) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { StageTool(onClick = onOpen, label = stringResource(R.string.sports_filter_title), icon = MobileIcons.Sort) }
        item { StageTool(onClick = onOpen, label = if (langs.isEmpty()) stringResource(R.string.sports_any) else langs.joinToString(", "), icon = MobileIcons.Translate, on = langs.isNotEmpty()) }
        if (f.countries.isNotEmpty()) item { StageTool(onClick = onOpen, label = f.countries.joinToString(", "), on = true) }
        if (f.minQuality != MinQuality.ANY) item { StageTool(onClick = onOpen, label = qualityLabel(f.minQuality), on = true) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = stageText(13f, 500, lineHeight = 1.45f), color = LocalStagePalette.current.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
}

/** *Watch live* before a search: what Find channels does, and the button — or, while it runs, "Searching your channels…". */
@Composable
private fun FindChannels(busy: Boolean, searching: Boolean, onFind: () -> Unit) {
    val palette = LocalStagePalette.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            searching -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.sports_finding_channels), style = stageText(13f, 600), color = palette.body)
            }
            busy -> CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            else -> {
                Text(stringResource(R.string.sports_find_channels_description), style = stageText(13f, 500, lineHeight = 1.45f), color = palette.muted)
                StageButton(onClick = onFind, text = stringResource(R.string.sports_find_channels), icon = MobileIcons.Search, style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Find channels outside its window: when it opens, or that the match is over. */
@Composable
private fun ClosedNote(window: WatchWindow) {
    Note(
        when (window) {
            is WatchWindow.TooEarly -> stringResource(R.string.sports_find_channels_from, kickOffText(window.opensAt))
            else -> stringResource(R.string.sports_find_channels_ended)
        },
    )
}

/** No playlist on this profile: scores, tables and alerts work; channels need one. */
@Composable
private fun NoPlaylistNote(onAddPlaylist: () -> Unit) {
    val palette = LocalStagePalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StageIcon(MobileIcons.LiveTv, 44.dp, palette.dim)
        Text(stringResource(R.string.sports_no_playlist_title), style = stageText(18f, 800), color = palette.text, textAlign = TextAlign.Center)
        Text(stringResource(R.string.sports_no_playlist_body), style = stageText(13f, 500, lineHeight = 1.45f), color = palette.muted, textAlign = TextAlign.Center)
        StageButton(onClick = onAddPlaylist, text = stringResource(R.string.sports_add_playlist), style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

/**
 * The score box: crest and name each side, the score, LIVE · minute; then time · round · venue and
 * each side's scorers. Spoiler-free: the score is a dash until a tap.
 */
@Composable
private fun ScoreBox(m: Match, summary: MatchSummary?, hidden: Boolean, onReveal: () -> Unit) {
    val palette = LocalStagePalette.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.ink.copy(alpha = 0.05f))
            .clickable(enabled = hidden, onClick = onReveal)
            .padding(16.dp),
    ) {
        val home = m.home
        val away = m.away
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Side(home?.team, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val score = when {
                    m.competitors.size < 2 -> m.title
                    m.state == MatchState.PRE -> timeText(m.startMs)
                    hidden -> "– : –"
                    m.sport == Sport.TENNIS && !home?.score?.periods.isNullOrEmpty() ->
                        home.score!!.periods.zip(away?.score?.periods.orEmpty()).joinToString("  ") { (h, a) -> "${h.value}–${a.value}" }
                    else -> "${scoreText(m.sport, home?.score)}–${scoreText(m.sport, away?.score)}"
                }
                Text(score, style = stageText(if (m.competitors.size < 2) 18f else if (score.length > 7) 24f else 40f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                if (m.isLive) LiveMark(stringResource(R.string.player_live) + sep + clockText(m))
                else Text(
                    if (m.state == MatchState.PRE) dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today) else m.statusText,
                    style = stageText(11.5f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Side(away?.team, Modifier.weight(1f))
        }
        val info = listOfNotNull(kickOffText(m.startMs).takeIf { m.state != MatchState.PRE }, m.subtitle, m.venue).joinToString(sep)
        if (info.isNotEmpty()) Text(info, style = stageText(11.5f, 500), color = palette.muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        if (!hidden && m.competitors.size == 2) {
            val goals = (summary?.timeline ?: m.events).filter { it.kind == MatchEvent.Kind.GOAL || it.kind == MatchEvent.Kind.PENALTY_GOAL || it.kind == MatchEvent.Kind.OWN_GOAL }
            fun line(teamId: String?) = goals.filter { it.teamId == teamId }.joinToString(" · ") { "⚽ ${it.players.firstOrNull().orEmpty().substringAfterLast(' ')} ${it.minute}" }
            val h = line(home?.team?.id)
            val a = line(away?.team?.id)
            if (h.isNotEmpty() || a.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(h, style = stageText(11.5f, 500), color = palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(a, style = stageText(11.5f, 500), color = palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Side(team: tv.own.owntv.core.sports.model.Team?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (team != null) {
            Crest(team, 48)
            Text(teamLabel(team), style = stageText(12.5f, 700), color = LocalStagePalette.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One channel under Watch live: logo, name with its quality tag, why it is listed (in the tier's colour),
 * and its country. [playing] marks the one on screen (the player's Other channels).
 */
@Composable
internal fun ChannelRow(o: WatchOption, m: Match, onClick: () -> Unit, onHold: (() -> Unit)?, playing: Boolean = false) {
    val palette = LocalStagePalette.current
    StageRow(onClick = onClick, onLongClick = onHold, height = 62.dp, current = playing, modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.size(46.dp, 32.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            ChannelLogoImage(url = o.channel.logoUrl, modifier = Modifier.fillMaxSize(), plateOn = palette.page, fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, palette.dim) })
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.facets.name, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                qualityTag(o.facets.qualityRank)?.let { StageTag(it) }
            }
            val (color, text) = when {
                playing -> palette.muted to stringResource(R.string.sports_playing_now)
                o.tier == WatchTier.GUIDE -> TierGuide to (o.guideTitle?.let { stringResource(R.string.sports_reason_guide, it) } ?: stringResource(R.string.sports_confirmed))
                o.tier == WatchTier.EVENT -> TierEvent to stringResource(R.string.sports_reason_event)
                o.tier == WatchTier.REMEMBERED -> TierPick to stringResource(R.string.sports_reason_pick, leagueName(m))
                else -> palette.muted to stringResource(R.string.sports_reason_likely, leagueName(m))
            }
            Text(text, style = stageText(11.5f, 600), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) StageIcon(MobileIcons.Check, 18.dp, stageAccent.accent)
        else o.facets.country?.let { StageTag(it) }
    }
}

/** The timeline: the minute, the event's mark, "Goal · Mario Götze" and its team (· assist). */
@Composable
private fun TimelineRow(e: MatchEvent, m: Match) {
    val palette = LocalStagePalette.current
    val team = m.competitors.firstOrNull { it.team.id == e.teamId }?.team?.name
    val who = e.players.firstOrNull().orEmpty()
    val title = when (e.kind) {
        MatchEvent.Kind.GOAL -> stringResource(R.string.sports_event_goal, who)
        MatchEvent.Kind.OWN_GOAL -> stringResource(R.string.sports_event_own_goal, who)
        MatchEvent.Kind.PENALTY_GOAL -> stringResource(R.string.sports_event_penalty, who)
        MatchEvent.Kind.YELLOW_CARD -> stringResource(R.string.sports_event_yellow, who)
        MatchEvent.Kind.RED_CARD -> stringResource(R.string.sports_event_red, who)
        MatchEvent.Kind.SUBSTITUTION -> stringResource(R.string.sports_event_sub, who, e.players.getOrNull(1).orEmpty())
        MatchEvent.Kind.WICKET -> stringResource(R.string.sports_event_wicket, who)
        MatchEvent.Kind.KICKOFF -> stringResource(R.string.sports_event_kickoff)
        MatchEvent.Kind.HALF_TIME -> stringResource(R.string.sports_event_half_time)
        MatchEvent.Kind.FULL_TIME -> stringResource(R.string.sports_event_full_time)
    }
    val assist = if (e.kind == MatchEvent.Kind.GOAL) e.players.getOrNull(1)?.let { stringResource(R.string.sports_event_assist, it) } else null
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.minute, style = stageText(13f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(44.dp))
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (e.kind) {
                MatchEvent.Kind.YELLOW_CARD -> Box(Modifier.size(10.dp, 14.dp).background(androidx.compose.ui.graphics.Color(0xFFFFD23F), RoundedCornerShape(2.dp)))
                MatchEvent.Kind.RED_CARD -> Box(Modifier.size(10.dp, 14.dp).background(SportsLive, RoundedCornerShape(2.dp)))
                MatchEvent.Kind.SUBSTITUTION -> StageIcon(MobileIcons.SwapHoriz, 16.dp, palette.muted)
                MatchEvent.Kind.GOAL, MatchEvent.Kind.OWN_GOAL, MatchEvent.Kind.PENALTY_GOAL -> Text("⚽", style = stageText(13f, 400))
                else -> StageIcon(MobileIcons.Schedule, 15.dp, palette.muted)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(team, assist).joinToString(stringResource(R.string.content_epg_bits_separator))
            if (sub.isNotEmpty()) Text(sub, style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One stat: home value, the name, away value, each side's share as a bar. */
@Composable
private fun StatRow(label: String, home: String, away: String) {
    val palette = LocalStagePalette.current
    val h = home.trimEnd('%').toFloatOrNull() ?: 0f
    val a = away.trimEnd('%').toFloatOrNull() ?: 0f
    val share = if (h + a > 0f) h / (h + a) else 0.5f
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(home, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(56.dp))
            Text(label, style = stageText(12f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(away, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(share.coerceIn(0.01f, 0.99f)).fillMaxSize().background(stageAccent.accent, RoundedCornerShape(2.dp)))
            Box(Modifier.weight((1f - share).coerceIn(0.01f, 0.99f)).fillMaxSize().background(palette.ink.copy(alpha = 0.18f), RoundedCornerShape(2.dp)))
        }
    }
}

/** Both line-ups side by side: formation, then the starters with their shirt numbers. */
@Composable
private fun Lineups(m: Match, summary: MatchSummary?) {
    val palette = LocalStagePalette.current
    val lineups = summary?.lineups.orEmpty()
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOfNotNull(m.home, m.away).forEach { c ->
            val l = lineups.firstOrNull { it.teamId == c.team.id }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOfNotNull(c.team.shortName, l?.formation).joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(13.5f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                l?.starters?.forEach { p ->
                    Text(listOfNotNull(p.jersey, p.name).joinToString("  "), style = stageText(12.5f, 500), color = if (p.subbedOut) palette.muted else palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Each side's last five games: the result letter, ⌂ for a home game, the opponent, the score. */
@Composable
private fun Form(m: Match, summary: MatchSummary?) {
    val palette = LocalStagePalette.current
    val form = summary?.form.orEmpty()
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOfNotNull(m.home, m.away).forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.team.shortName, style = stageText(13.5f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                form[c.team.id].orEmpty().forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (letter, color) = when (g.result) {
                            "W" -> stringResource(R.string.sports_table_won) to TierGuide
                            "L" -> stringResource(R.string.sports_table_lost) to SportsLive
                            else -> stringResource(R.string.sports_table_drawn) to palette.muted
                        }
                        Box(Modifier.size(22.dp).background(color.copy(alpha = 0.22f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text(letter, style = stageText(11.5f, 800), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (g.home) StageIcon(MobileIcons.Home, 14.dp, palette.muted) else Spacer(Modifier.size(14.dp))
                        Text(g.opponent, style = stageText(12.5f, 600), color = palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(g.score, style = stageText(12.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Who shows the match: the channels Find channels found in the user's guide — one per row, under
 * their country, countries and names in order, any without a country last — then the broadcasters ESPN
 * lists (United States services only). The page scrolls, so a long list scrolls with it.
 */
@Composable
private fun Broadcasts(m: Match, found: List<WatchOption>?) {
    val palette = LocalStagePalette.current
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.sports_broadcasts_guide), style = stageText(13.5f, 800), color = palette.text)
        val guide = found?.filter { it.tier == WatchTier.GUIDE || it.tier == WatchTier.EVENT }
        when {
            guide == null -> Text(stringResource(R.string.sports_broadcasts_find_first), style = stageText(12.5f, 500), color = palette.muted)
            guide.isEmpty() -> Text(stringResource(R.string.sports_broadcasts_none), style = stageText(12.5f, 500), color = palette.muted)
            else -> byCountry(guide.map { it.facets.country.orEmpty() to it.facets.name }).forEach { (country, names) ->
                BroadcastGroup(country, names)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sports_broadcasts_espn), style = stageText(13.5f, 800), color = palette.text)
        if (m.broadcasts.isEmpty()) Text(stringResource(R.string.sports_broadcasts_none), style = stageText(12.5f, 500), color = palette.muted)
        byCountry(m.broadcasts.map { it.market?.takeIf { c -> c.length == 2 }.orEmpty() to it.name }).forEach { (country, names) ->
            BroadcastGroup(country, names)
        }
    }
}

/** (country, name) pairs grouped by country — countries in order, none last — each group's names sorted, once each. */
private fun byCountry(pairs: List<Pair<String, String>>): List<Pair<String, List<String>>> =
    pairs.groupBy({ it.first }, { it.second }).entries
        .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
        .map { (country, names) -> country to names.distinct().sortedBy { it.lowercase() } }

/** A country's heading (its tag), then one channel per row. */
@Composable
private fun BroadcastGroup(country: String, names: List<String>) {
    val palette = LocalStagePalette.current
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (country.isNotEmpty()) StageTag(country)
        names.forEach { Text(it, style = stageText(13f, 600), color = palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp)) }
    }
}

/** The table tab: the match's two sides highlighted in their group; a tap opens the league page. */
@Composable
private fun TablePreview(table: StandingsTable?, m: Match, onOpenLeague: () -> Unit) {
    val group = table?.groups?.firstOrNull { g -> g.rows.any { r -> m.competitors.any { it.team.id == r.team.id } } } ?: table?.groups?.firstOrNull() ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenLeague).padding(6.dp)) {
        StandingsHeader()
        group.rows.forEach { row -> StandingsLine(row, highlight = m.competitors.any { it.team.id == row.team.id }) }
    }
}

/** `# Team P W D L GD Pts`. */
@Composable
internal fun StandingsHeader() {
    val palette = LocalStagePalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = stageText(11f, 700), color = palette.dim, modifier = Modifier.width(24.dp))
        Text(stringResource(R.string.sports_table_team), style = stageText(11f, 700), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        listOf(
            R.string.sports_table_played, R.string.sports_table_won, R.string.sports_table_drawn,
            R.string.sports_table_lost, R.string.sports_table_difference, R.string.sports_table_points,
        ).forEach { Text(stringResource(it), style = stageText(11f, 700), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn)) }
    }
}

/** One table row; [highlight] (a side of the open match, a followed team) on accent 14%. */
@Composable
internal fun StandingsLine(row: StandingsRow, highlight: Boolean) {
    val palette = LocalStagePalette.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlight) Modifier.background(stageAccent.accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.rank.toString(), style = stageText(12.5f, 700), color = palette.muted, modifier = Modifier.width(24.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(row.team, 18)
            Text(row.team.shortName, style = stageText(12.5f, if (highlight) 800 else 600), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listOf(row.played.toString(), row.won.toString(), row.drawn.toString(), row.lost.toString(), row.difference, row.points.toString()).forEachIndexed { i, v ->
            Text(v, style = stageText(12.5f, if (i == 5) 800 else 500), color = if (i == 5) palette.text else palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn))
        }
    }
}

/** The number columns of a table, each 26 wide — six fit beside the team on a phone. */
private val NumberColumn = 26.dp
