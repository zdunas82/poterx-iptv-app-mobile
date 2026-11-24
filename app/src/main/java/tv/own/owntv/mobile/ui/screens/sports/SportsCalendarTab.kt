package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tv.own.owntv.core.sports.CalendarStatus
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTagTone
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** How far back the arrows go while ESPN's season start is not known (cricket has none). */
private const val MONTHS_BACK_UNKNOWN = 12L

/** A sideways swipe this long (dp) turns the month, or the folded week. */
private const val SWIPE_DP = 48

/** The list's key of the month grid: once it has scrolled away, the month folds to its week. */
private const val GRID_KEY = "grid"

/**
 * The Calendar tab: the month on top, the chosen day below — only the followed leagues the chips let
 * through. Tap a day to show it; swipe the month or tap ‹ › for another; Today jumps back; the arrows
 * stop at the season's first month. Scrolling the day's list folds the month into its week (swipe it
 * for another week; tap the month name, or scroll back up, to open the month again). A match opens its
 * page (or shows a hidden score first); a long press opens the sheet; the bell sets a reminder.
 */
@Composable
internal fun SportsCalendarTab(
    vm: SportsViewModel,
    frame: SportsFrame,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
) {
    val filter = frame.filter
    val config = filter.config
    val day by vm.calendarDay.collectAsStateWithLifecycle()
    val month = YearMonth.from(day)
    val keys = filter.leagueKeys
    val cricket = keys.any { SportsCatalog.league(it)?.sport == Sport.CRICKET }
    // Cricket's feed is per day: the chosen day is fetched first. Other feeds are per month.
    LaunchedEffect(keys, month, if (cricket) day else null) { vm.showMonth(keys.toSet(), month, if (cricket) day else null) }
    val cal = vm.calendarMonth.collectAsStateWithLifecycle().value?.takeIf { it.month == month }
    val days = remember(cal, filter) { cal?.days.orEmpty().mapValues { (_, l) -> l.filter(filter::passes) }.filterValues { it.isNotEmpty() } }

    // The earliest season start seen for these leagues stops the arrows.
    var seasonStart by remember(keys) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(cal?.seasonStart) { cal?.seasonStart?.let { s -> seasonStart = seasonStart?.let { minOf(it, s) } ?: s } }
    val thisMonth = YearMonth.now()
    val minMonth = (seasonStart?.let(YearMonth::from) ?: thisMonth.minusMonths(MONTHS_BACK_UNKNOWN)).let { if (it > thisMonth) thisMonth else it }
    fun go(to: LocalDate) {
        if (YearMonth.from(to) >= minMonth) vm.calendarDay.value = to
    }
    fun goMonth(m: YearMonth) = go(if (m == thisMonth) LocalDate.now() else m.atDay(1))

    val locale = LocalConfiguration.current.locales[0]
    val oneLeague = keys.singleOrNull()?.let(SportsCatalog::league)
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val columnOf = { d: LocalDate -> (d.dayOfWeek.value - firstDay.value + 7) % 7 }
    val revealed = remember { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val reminders by vm.reminderSet.collectAsStateWithLifecycle()
    val dayMatches = days[day].orEmpty()
    val hiddenOf = { m: Match -> config.spoilerFree && m.state == MatchState.POST && m.key !in revealed }
    val listState = frame.listState
    val scope = rememberCoroutineScope()
    // Folded once the list's top is past the month: the grid is off screen and nothing above it shows.
    val folded by remember {
        derivedStateOf {
            val shown = listState.layoutInfo.visibleItemsInfo
            shown.isNotEmpty() && shown.none { it.key == GRID_KEY } && shown.first().key !in ABOVE_GRID
        }
    }

    SportsTabPage(
        frame,
        subtitle = {
            // "**Premier League** · October 2026", or with no one choice "**October 2026**".
            val scopeName = oneLeague?.let { leagueLabel(it) } ?: filter.oneSport?.let { sportLabel(it) }
                ?: stringResource(R.string.sports_my_teams).takeIf { filter.myTeams && filter.chips.size == 1 }
            if (scopeName != null) SportsSubtitle(scopeName, monthTitle(month, locale)) else SportsSubtitle(monthTitle(month, locale))
        },
        overlay = {
            if (folded) FoldedWeek(month, day, locale, days, filter, oneLeague != null, columnOf, onDay = ::go, onOpen = { scope.launch { listState.animateScrollToItem(0) } })
        },
    ) {
        (cal?.status as? CalendarStatus.Offline)?.let { off ->
            item(key = "offline") {
                Text(
                    stringResource(R.string.sports_offline_saved, savedText(off.savedAt, locale)),
                    style = stageText(12.5f, 500, lineHeight = 1.4f), color = LocalStagePalette.current.warn,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth()
                        .background(LocalStagePalette.current.warn.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        item(key = "monthHead") {
            MonthHead(month, locale, canGoBack = month > minMonth, onMonth = ::goMonth, onToday = { go(LocalDate.now()) })
        }
        item(key = GRID_KEY) {
            Column(
                Modifier.pointerInput(month, minMonth) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > SWIPE_DP.dp.toPx()) goMonth(month.minusMonths(1))
                            else if (total < -SWIPE_DP.dp.toPx()) goMonth(month.plusMonths(1))
                        },
                    ) { _, dx -> total += dx }
                },
            ) {
                WeekdayRow(firstDay, locale)
                MonthGrid(
                    month = month, day = day, today = LocalDate.now(), days = days, filter = filter,
                    oneLeague = oneLeague != null, loading = cal == null || cal.status == CalendarStatus.Loading,
                    columnOf = columnOf, onDay = { vm.calendarDay.value = it },
                )
            }
        }
        val loading = cal == null || cal.status == CalendarStatus.Loading
        item(key = "dayHead") {
            val loadingText = if (loading) stringResource(R.string.sports_loading_month, month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)) else null
            DayHead(day, dayMatches, filter, config.spoilerFree && dayMatches.any { it.state == MatchState.POST }, loadingText, locale)
        }
        when {
            dayMatches.isEmpty() && loading -> items(5) { i -> SkeletonRow(i) }
            dayMatches.isEmpty() && cal?.status == CalendarStatus.Failed -> item(key = "failed") { EmptyNote(stringResource(R.string.sports_empty_unreachable), null, null) }
            dayMatches.isEmpty() -> item(key = "empty") {
                val sport = filter.oneSport
                val next = days.filterKeys { it > day }.toSortedMap().values.firstOrNull()?.firstOrNull()
                val nextText = next?.let { n ->
                    stringResource(R.string.sports_next, dateText(localDate(n.startMs), locale) + stringResource(R.string.content_epg_bits_separator) + MatchNotifications.pair(LocalContext.current, n))
                }
                EmptyNote(
                    if (sport != null) stringResource(R.string.sports_nothing_in_sport, sportLabel(sport)) else stringResource(R.string.sports_nothing_on_day),
                    stringResource(R.string.sports_change_leagues_hint),
                    nextText,
                )
            }
            else -> {
                val groups = dayMatches.groupBy { it.feed }.toList()
                    .sortedWith(compareBy({ config.rankOf(it.second.first().sport, it.first).first }, { config.rankOf(it.second.first().sport, it.first).second }))
                groups.forEach { (feed, list) ->
                    item(key = "h:$feed") { GroupHeading(feed, list, onOpenLeague = vm::chooseLeague) }
                    items(list.size, key = { "m:" + list[it].key }) { i ->
                        val m = list[i]
                        val hidden = hiddenOf(m)
                        MatchListRow(
                            m = m, mine = filter.isMine(m), hidden = hidden,
                            reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(LocalContext.current, m)) in reminders,
                            onClick = { if (hidden) revealed.add(m.key) else onOpenMatch(m) },
                            onHold = { menuFor = m },
                            onBell = { vm.remindFound(m) },
                        )
                    }
                }
            }
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

/** The page's items above the month grid (the shared header, the offline note, the month's arrows). */
private val ABOVE_GRID = setOf("tabs", "sub", "none", "chips", "leagues", "offline", "monthHead")

/** `.pmh`: ‹ "October 2026" › and Today. */
@Composable
private fun MonthHead(month: YearMonth, locale: Locale, canGoBack: Boolean, onMonth: (YearMonth) -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ArrowButton(back = true, enabled = canGoBack, label = stringResource(R.string.sports_previous_month)) { onMonth(month.minusMonths(1)) }
        Text(
            monthTitle(month, locale), style = stageText(18f, 800), color = LocalStagePalette.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        ArrowButton(back = false, enabled = true, label = stringResource(R.string.sports_next_month)) { onMonth(month.plusMonths(1)) }
        StageTool(onClick = onToday, label = stringResource(R.string.content_epg_today))
    }
}

/** `.ar`: a 36 dp month arrow, at 40% where it cannot go. */
@Composable
private fun ArrowButton(back: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp)).background(palette.controlFill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StageIcon(MobileIcons.KeyboardArrowRight, 20.dp, palette.text, if (back) Modifier.rotate(180f) else Modifier)
    }
}

/** `.pwd`: the weekdays, starting on the language's first day. */
@Composable
private fun WeekdayRow(firstDay: java.time.DayOfWeek, locale: Locale) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { i ->
            Text(
                firstDay.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                style = stageText(10.5f, 800, 0.08.em), color = LocalStagePalette.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** `.pgrid`: the month's weeks, a tap choosing a day. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    day: LocalDate,
    today: LocalDate,
    days: Map<LocalDate, List<Match>>,
    filter: ChipFilter,
    oneLeague: Boolean,
    loading: Boolean,
    columnOf: (LocalDate) -> Int,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays(columnOf(first).toLong())
    val weeks = (columnOf(first) + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { w ->
            WeekRow(List(7) { start.plusDays((w * 7 + it).toLong()) }, month, day, today, days, filter, oneLeague, loading, onDay)
        }
    }
}

/** One week of cells; [month] marks which days are its own (the others are blank). */
@Composable
private fun WeekRow(
    week: List<LocalDate>,
    month: YearMonth?,
    day: LocalDate,
    today: LocalDate,
    days: Map<LocalDate, List<Match>>,
    filter: ChipFilter,
    oneLeague: Boolean,
    loading: Boolean,
    onDay: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { d ->
            val inMonth = month == null || YearMonth.from(d) == month
            DayCell(
                d, inMonth, selected = d == day, today = d == today, past = d < today,
                matches = if (inMonth) days[d].orEmpty() else emptyList(), filter = filter, oneLeague = oneLeague,
                loading = loading, onClick = { onDay(d) }, modifier = Modifier.weight(1f).height(if (oneLeague) 44.dp else 50.dp),
            )
        }
    }
}

/**
 * `.c`: the day's number (today on the accent), then up to three sport marks ("+" for more) — with one
 * league, how many of its matches are on — ★ when one of My teams plays, a red dot while one is live.
 * Past days dimmed; the chosen day in the accent with its ring; a day still loading shows a grey bar.
 */
@Composable
private fun DayCell(
    d: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    past: Boolean,
    matches: List<Match>,
    filter: ChipFilter,
    oneLeague: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val palette = LocalStagePalette.current
    val a = stageAccent
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .then(
                when {
                    !inMonth -> Modifier
                    selected -> Modifier.background(a.accent.copy(alpha = 0.18f)).drawBehind { drawInnerRing(a.accent.copy(alpha = 0.9f), 1.5.dp.toPx(), 10.dp.toPx()) }
                    else -> Modifier.background(palette.ink.copy(alpha = 0.035f))
                },
            )
            .then(if (inMonth) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(top = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val number = d.dayOfMonth.toString()
            if (today) {
                Text(number, style = stageText(13f, 800), color = a.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(a.accent, RoundedCornerShape(7.dp)).padding(horizontal = 5.dp))
            } else {
                Text(
                    number, style = stageText(13f, 700), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when {
                        !inMonth -> palette.dim.copy(alpha = 0.35f)
                        past -> palette.muted
                        else -> palette.text
                    },
                )
            }
            if (!inMonth) return@Column
            when {
                matches.isEmpty() && loading -> Box(Modifier.padding(top = 8.dp).size(26.dp, 10.dp).background(palette.controlFill, RoundedCornerShape(5.dp)))
                matches.isEmpty() -> Unit
                oneLeague -> Text(
                    matches.size.toString(), style = stageText(10f, 800), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).background(a.accent.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
                else -> {
                    val sports = filter.config.orderSports(matches.map { it.sport }.distinct())
                    Text(
                        sports.take(3).joinToString("") { sportEmoji(it) } + if (sports.size > 3) "+" else "",
                        style = stageText(9.5f, 500), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp).alpha(if (past) 0.55f else 1f),
                    )
                }
            }
        }
        if (!inMonth) return@Box
        if (matches.any { it.isLive }) Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 1.dp).size(5.dp).background(SportsLive, CircleShape))
        if (matches.any(filter::isMine)) Text("★", style = stageText(8.5f, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp))
    }
}

/**
 * The month folded into one row while the day's list scrolls: the month's name with ▾ (a tap opens the
 * month again), and the chosen day's week; swipe it for the next or previous week.
 */
@Composable
private fun BoxScope.FoldedWeek(
    month: YearMonth,
    day: LocalDate,
    locale: Locale,
    days: Map<LocalDate, List<Match>>,
    filter: ChipFilter,
    oneLeague: Boolean,
    columnOf: (LocalDate) -> Int,
    onDay: (LocalDate) -> Unit,
    onOpen: () -> Unit,
) {
    val palette = LocalStagePalette.current
    Column(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().background(palette.page).padding(bottom = 8.dp)
            .pointerInput(day) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > SWIPE_DP.dp.toPx()) onDay(day.minusDays(7))
                        else if (total < -SWIPE_DP.dp.toPx()) onDay(day.plusDays(7))
                    },
                ) { _, dx -> total += dx }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(monthTitle(month, locale), style = stageText(18f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StageIcon(MobileIcons.KeyboardArrowDown, 18.dp, palette.muted)
        }
        WeekdayRow(WeekFields.of(locale).firstDayOfWeek, locale)
        val weekStart = day.minusDays(columnOf(day).toLong())
        Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 5.dp)) {
            WeekRow(List(7) { weekStart.plusDays(it.toLong()) }, null, day, LocalDate.now(), days, filter, oneLeague = oneLeague, loading = false, onDay = onDay)
        }
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(36.dp, 4.dp).background(palette.ink.copy(alpha = 0.18f), RoundedCornerShape(2.dp)))
    }
}

/**
 * `.pdh`: "Sat 24 Oct", then "34 matches · ★ Arsenal" — with Spoiler-free on a past day, how to see a
 * score; while the month loads, [loading] ("Loading October…").
 */
@Composable
private fun DayHead(day: LocalDate, matches: List<Match>, filter: ChipFilter, spoiler: Boolean, loading: String?, locale: Locale) {
    val palette = LocalStagePalette.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .drawBehind { drawRect(palette.ink.copy(alpha = 0.06f), size = size.copy(height = 1.dp.toPx())) }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(dateText(day, locale), style = stageText(17f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val small = when {
            matches.isEmpty() && loading != null -> loading
            spoiler -> stringResource(R.string.sports_spoiler_free) + sep + stringResource(R.string.sports_spoiler_free_description_mobile)
            matches.isEmpty() -> null
            else -> {
                val mine = matches.filter(filter::isMine).flatMap { m -> m.competitors.filter { c -> filter.config.teams.any { it.sport == m.sport && it.id == c.team.id } } }
                    .map { teamLabel(it.team) }.distinct()
                pluralStringResource(R.plurals.sports_hub_matches, matches.size, matches.size) + if (mine.isNotEmpty()) "$sep★ " + mine.joinToString(", ") else ""
            }
        }
        small?.let { Text(it, style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** `.pgh`: "⚽ PREMIER LEAGUE", its count and ›; a tap opens the league in the Leagues tab. */
@Composable
private fun GroupHeading(feed: String, list: List<Match>, onOpenLeague: (String) -> Unit) {
    val palette = LocalStagePalette.current
    val league = SportsCatalog.league(feed)
    val sport = list.first().sport
    val name = if (league == null || sport == Sport.CRICKET) sportLabel(sport) else league.name
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .then(if (league != null) Modifier.clickable { onOpenLeague(feed) } else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("${sportEmoji(sport)} ${name.uppercase(locale)}", style = stageText(11.5f, 800, 0.1.em), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(list.size.toString(), style = stageText(12f, 700), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (league != null) StageIcon(MobileIcons.KeyboardArrowRight, 15.dp, stageAccent.accent)
    }
}

/**
 * `.pm`, a match in a list (Calendar, a league's Fixtures and Results, a team page): kick-off (or the live
 * dot and minute) over its [day], both crests, "Arsenal – Everton" with ★ for one of My teams — a racing
 * session by its name — and a [sub] line; then the result and FT ("• – •" while Spoiler-free hides it; a
 * team page puts its [outcome] in FT's place), or NEXT and the bell for a match still to come.
 */
@Composable
internal fun MatchListRow(
    m: Match,
    mine: Boolean,
    hidden: Boolean,
    reminded: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    onBell: () -> Unit,
    day: String? = null,
    sub: String? = null,
    next: Boolean = false,
    outcome: (@Composable () -> Unit)? = null,
) {
    val palette = LocalStagePalette.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .then(if (mine) Modifier.background(StarYellow.copy(alpha = 0.05f)) else Modifier)
            .stageClickable(interaction, onClick, onHold)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(52.dp)) {
            when {
                m.isLive -> LiveMark(clockText(m))
                m.state == MatchState.PRE -> Text(timeText(m.startMs), style = stageText(13.5f, 700), color = palette.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            day?.let { Text(it, style = stageText(11.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        val home = m.home
        val away = m.away
        if (home != null && away != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Crest(home.team, 28); Crest(away.team, 28) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val title = if (home != null && away != null) MatchNotifications.pair(context, m)
                else listOfNotNull(sessionLabel(m), m.title).joinToString(stringResource(R.string.content_epg_bits_separator))
                Text(title, style = stageText(14.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (mine) Text("★", style = stageText(13f, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            sub?.let { Text(it, style = stageText(12.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (next) StageTag(stringResource(R.string.sports_next_badge).uppercase(LocalConfiguration.current.locales[0]), tone = StageTagTone.ACCENT)
        when {
            m.state == MatchState.POST && m.competitors.size == 2 -> {
                Text(
                    if (hidden) "• – •" else "${scoreText(m.sport, home?.score)} – ${scoreText(m.sport, away?.score)}",
                    style = stageText(15f, 800), color = if (hidden) palette.muted else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (outcome != null) { if (!hidden) outcome() }
                else Text(m.statusText, style = stageText(10.5f, 800, 0.05.em), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = StatusWidth))
            }
            m.state == MatchState.POST || m.off -> Text(m.statusText, style = stageText(12f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = StatusWidth))
            m.state == MatchState.PRE -> Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.content_remind_me), onClick = onBell),
                contentAlignment = Alignment.Center,
            ) { StageIcon(MobileIcons.Bell, 20.dp, if (reminded) stageAccent.accent else palette.muted) }
        }
    }
}

/** At most this wide: ESPN's status beside a result ("FT", "AET", "Final/OT"). */
private val StatusWidth = 56.dp

/** `.pskel`: a loading row's grey bars. */
@Composable
private fun SkeletonRow(i: Int) {
    val fill = LocalStagePalette.current.controlFill
    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp, 12.dp).background(fill, RoundedCornerShape(6.dp)))
        Box(Modifier.size(60.dp, 26.dp).background(fill, RoundedCornerShape(6.dp)))
        Box(Modifier.size((150 + i * 12).dp, 12.dp).background(fill, RoundedCornerShape(6.dp)))
    }
}

/** `.pempty`: the title, then a line, then what comes next. */
@Composable
private fun EmptyNote(title: String, line: String?, next: String?) {
    val palette = LocalStagePalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = stageText(16f, 800), color = palette.text, textAlign = TextAlign.Center)
        line?.let { Text(it, style = stageText(13.5f, 500, lineHeight = 1.5f), color = palette.muted, textAlign = TextAlign.Center) }
        next?.let { Text(it, style = stageText(13.5f, 600, lineHeight = 1.5f), color = palette.body, textAlign = TextAlign.Center) }
    }
}

/** When an offline month was saved: "Fri 16 Oct, 21:04" in the user's language. */
private fun savedText(ms: Long, locale: Locale): String =
    java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMHHmm"), locale)
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
