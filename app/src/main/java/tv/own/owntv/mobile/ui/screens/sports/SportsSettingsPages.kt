package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.sports.AlertKind
import tv.own.owntv.core.sports.AutoRebuild
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsRefreshPolicy
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.ReorderItem
import tv.own.owntv.mobile.ui.components.ReorderSheet
import tv.own.owntv.mobile.ui.components.SheetScroll
import tv.own.owntv.mobile.ui.nav.isExpandedWidth
import tv.own.owntv.mobile.ui.screens.settings.LocalSettingsNav
import tv.own.owntv.mobile.ui.screens.settings.PageNote
import tv.own.owntv.mobile.ui.screens.settings.SettingsLeaf
import tv.own.owntv.mobile.ui.screens.settings.SettingsPage
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSectionHead
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStepper
import tv.own.owntv.mobile.ui.stage.StageSwitch

/** Commentary languages offered (ISO 639-1): the ones provider prefixes and names carry — the TV's list. */
private val LANGUAGES = listOf("DE", "EN", "FR", "ES", "IT", "TR", "AR", "PT", "NL", "PL", "RU", "EL", "RO", "SV", "NO", "DA", "FI", "HU", "CS", "UR", "HI", "BN")

/** Channel countries offered: provider prefixes, two letters as Settings keeps them — the TV's list. */
private val COUNTRIES = listOf("DE", "AT", "CH", "UK", "US", "FR", "BE", "ES", "IT", "TR", "AR", "PT", "BR", "NL", "PL", "IN", "PK", "SA", "AE", "QA", "CA", "AU", "IE", "SE", "NO", "DK")

/** A choice list opened from the main page as a sheet. */
private enum class CodeSheet { LANGUAGES, COUNTRIES, PLAYLISTS, REMEMBERED }

/** One element of a stepper's cycle, moved by [d]. */
private fun <T> List<T>.step(current: T, d: Int): T = this[(indexOf(current).coerceAtLeast(0) + d).mod(size)]

/**
 * Settings → Sports (the television's page as built): What to show · Watching · While watching ·
 * Channels · Data. Languages, countries, playlists and remembered channels open as sheets; Sports and
 * leagues, My teams and Goal alerts are pages of their own.
 */
@Composable
fun SportsSettingsPage(vm: SportsSettingsViewModel = koinViewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val rebuilding by vm.rebuilding.collectAsStateWithLifecycle()
    val rebuiltAt by vm.rebuiltAt.collectAsStateWithLifecycle()
    val nav = LocalSettingsNav.current
    var sheet by remember { mutableStateOf<CodeSheet?>(null) }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val any = stringResource(R.string.sports_any)
    val locale = LocalConfiguration.current.locales[0]

    SettingsPage {
        item(key = "what") {
            Column {
                StageSectionHead(stringResource(R.string.sports_settings_what))
                val sports = SportsCatalog.sports.filter { s -> c.leagues.any { SportsCatalog.league(it)?.sport == s } }
                val sportNames = sports.take(4).map { sportLabel(it) }
                StageSettingRow(
                    stringResource(R.string.sports_leagues), icon = MobileIcons.Layers,
                    subtitle = (sportNames + pluralStringResource(R.plurals.sports_league_count, c.leagues.size, c.leagues.size)).joinToString(sep),
                    onClick = { nav(SettingsLeaf.SPORTS_LEAGUES.route) },
                ) { StageRowValue("", tv.own.owntv.mobile.ui.stage.StageChevron.FORWARD) }
                StageSettingRow(stringResource(R.string.sports_sport_buttons), icon = MobileIcons.Tiles, subtitle = stringResource(R.string.sports_sport_buttons_description)) {
                    StageSegmented(
                        listOf(stringResource(R.string.sports_sport_buttons_mine), stringResource(R.string.sports_sport_buttons_all)),
                        if (c.allSports) 1 else 0,
                        { i -> vm.update { it.copy(allSports = i == 1) } },
                    )
                }
                StageSettingRow(
                    stringResource(R.string.sports_my_teams), icon = MobileIcons.Star,
                    subtitle = c.teams.joinToString(", ") { it.name }.ifBlank { null },
                    onClick = { nav(SettingsLeaf.SPORTS_TEAMS.route) },
                ) { StageRowValue(c.teams.size.toString(), tv.own.owntv.mobile.ui.stage.StageChevron.FORWARD) }
                SwitchRow(MobileIcons.VisibilityOff, stringResource(R.string.sports_spoiler_free), stringResource(R.string.sports_spoiler_free_description_mobile), c.spoilerFree) {
                    vm.update { it.copy(spoilerFree = !it.spoilerFree) }
                }
            }
        }
        item(key = "watching") {
            Column {
                StageSectionHead(stringResource(R.string.sports_settings_watching))
                val langs = c.preferredLanguages(vm.appLanguage).map { languageName(it) }
                OpensRow(MobileIcons.Translate, stringResource(R.string.sports_languages_mobile), stringResource(R.string.sports_languages_description), langs.joinToString(", ").ifBlank { any }) { sheet = CodeSheet.LANGUAGES }
                OpensRow(MobileIcons.Tv, stringResource(R.string.sports_countries), stringResource(R.string.sports_countries_description), c.countries.joinToString(", ").ifBlank { any }) { sheet = CodeSheet.COUNTRIES }
                val q = MinQuality.entries
                StageSettingRow(stringResource(R.string.settings_vp_cat_picture), icon = MobileIcons.AspectRatio, subtitle = stringResource(R.string.sports_picture_description)) {
                    StageStepper(qualityLabel(c.minQuality), { vm.update { it.copy(minQuality = q.step(it.minQuality, -1)) } }, { vm.update { it.copy(minQuality = q.step(it.minQuality, 1)) } })
                }
                val chosenLists = playlists.filter { it.first in c.sourceIds }
                OpensRow(MobileIcons.PlaylistPlay, stringResource(R.string.settings_sort_playlist), null,
                    if (chosenLists.isEmpty()) stringResource(R.string.sports_all_playlists) else chosenLists.joinToString(", ") { it.second }) { sheet = CodeSheet.PLAYLISTS }
                SwitchRow(MobileIcons.Guide, stringResource(R.string.sports_only_confirmed_mobile), stringResource(R.string.sports_only_confirmed_description), c.onlyConfirmed) {
                    vm.update { it.copy(onlyConfirmed = !it.onlyConfirmed) }
                }
                SwitchRow(MobileIcons.Layers, stringResource(R.string.sports_strict_filter), stringResource(R.string.sports_strict_filter_description), c.strictFilter) {
                    vm.update { it.copy(strictFilter = !it.strictFilter) }
                }
            }
        }
        item(key = "while") {
            Column {
                StageSectionHead(stringResource(R.string.sports_settings_while_watching))
                SwitchRow(MobileIcons.LiveTv, stringResource(R.string.sports_score_info_bar), stringResource(R.string.sports_score_info_bar_description_mobile), c.scoreInInfoBar) {
                    vm.update { it.copy(scoreInInfoBar = !it.scoreInInfoBar) }
                }
                OpensRow(MobileIcons.Bell, stringResource(R.string.sports_channel_goal_alerts), stringResource(R.string.sports_goal_alerts_description), alertsSummary(c)) {
                    nav(SettingsLeaf.SPORTS_ALERTS.route)
                }
                val leads = SportsConfig.REMINDER_LEAD_CHOICES
                StageSettingRow(stringResource(R.string.sports_channel_match_reminders), icon = MobileIcons.Schedule, subtitle = stringResource(R.string.sports_match_reminders_description)) {
                    StageStepper(
                        pluralStringResource(R.plurals.recording_minutes, c.reminderLeadMinutes, c.reminderLeadMinutes),
                        { vm.update { it.copy(reminderLeadMinutes = leads.step(it.reminderLeadMinutes, -1)) } },
                        { vm.update { it.copy(reminderLeadMinutes = leads.step(it.reminderLeadMinutes, 1)) } },
                    )
                }
            }
        }
        item(key = "channels") {
            Column {
                StageSectionHead(stringResource(R.string.content_channel_overlay_title))
                val first = c.rememberedChannels.entries.firstOrNull()
                val line = first?.let { (k, ch) ->
                    val name = SportsCatalog.league(k)?.name ?: k.substringAfter('/')
                    if (c.rememberedChannels.size == 1) stringResource(R.string.sports_remembered_one, name, ch.name)
                    else pluralStringResource(R.plurals.sports_remembered_summary, c.rememberedChannels.size - 1, name, ch.name, c.rememberedChannels.size - 1)
                }
                OpensRow(MobileIcons.PlaylistPlay, stringResource(R.string.sports_remembered_channels), line, c.rememberedChannels.size.toString()) { sheet = CodeSheet.REMEMBERED }
                SwitchRow(MobileIcons.Guide, stringResource(R.string.sports_show_likely), stringResource(R.string.sports_show_likely_description), c.showLikely) {
                    vm.update { it.copy(showLikely = !it.showLikely) }
                }
            }
        }
        item(key = "data") {
            Column {
                StageSectionHead(stringResource(R.string.sports_settings_data))
                val modes = AutoRebuild.entries
                StageSettingRow(stringResource(R.string.sports_auto_rebuild), icon = MobileIcons.History, subtitle = stringResource(R.string.sports_auto_rebuild_description)) {
                    StageStepper(rebuildLabel(c.autoRebuild), { vm.update { it.copy(autoRebuild = modes.step(it.autoRebuild, -1)) } }, { vm.update { it.copy(autoRebuild = modes.step(it.autoRebuild, 1)) } })
                }
                val refresh = SportsRefreshPolicy.LIVE_SECONDS_CHOICES
                StageSettingRow(stringResource(R.string.sports_live_refresh), icon = MobileIcons.Sync, subtitle = stringResource(R.string.sports_live_refresh_description)) {
                    StageStepper(
                        pluralStringResource(R.plurals.settings_nav_hide_after_seconds, c.liveRefreshSeconds, c.liveRefreshSeconds),
                        { vm.update { it.copy(liveRefreshSeconds = refresh.step(it.liveRefreshSeconds, -1)) } },
                        { vm.update { it.copy(liveRefreshSeconds = refresh.step(it.liveRefreshSeconds, 1)) } },
                    )
                }
                val last = rebuiltAt?.let { at -> (dayOf(at, locale) ?: stringResource(R.string.content_epg_today)) + sep + timeText(at) }
                StageSettingRow(
                    stringResource(R.string.sports_refresh_data_now), icon = MobileIcons.Refresh,
                    subtitle = if (rebuilding) stringResource(R.string.sports_rebuilding) else last?.let { stringResource(R.string.sports_last_updated, it) },
                    onClick = { if (!rebuilding) vm.rebuild() },
                )
                StageSettingRow(stringResource(R.string.sports_about), icon = MobileIcons.Info, subtitle = stringResource(R.string.sports_about_summary))
            }
        }
    }

    when (sheet) {
        CodeSheet.LANGUAGES -> CodeListSheet(stringResource(R.string.sports_languages_mobile), LANGUAGES.map { it to languageName(it) }, c.preferredLanguages(vm.appLanguage), { sheet = null }) { code ->
            vm.update { s -> val now = s.preferredLanguages(vm.appLanguage); s.copy(languages = if (code in now) now - code else now + code) }
        }
        CodeSheet.COUNTRIES -> CodeListSheet(stringResource(R.string.sports_countries), COUNTRIES.map { it to it }, c.countries, { sheet = null }) { code ->
            vm.update { s -> s.copy(countries = if (code in s.countries) s.countries - code else s.countries + code) }
        }
        CodeSheet.PLAYLISTS -> {
            val chosen = playlists.map { it.first }.filter { it in c.sourceIds }.toSet()
            MobileBottomSheet(onDismissRequest = { sheet = null }) {
                StageSheetHeader(stringResource(R.string.settings_sort_playlist))
                StageMenuItem(stringResource(R.string.sports_all_playlists), { vm.update { it.copy(sourceIds = emptySet()) } }, checked = chosen.isEmpty())
                playlists.forEach { (id, name) ->
                    StageMenuItem(name, { vm.update { s -> s.copy(sourceIds = if (id in chosen) chosen - id else chosen + id) } }, checked = id in chosen)
                }
            }
        }
        CodeSheet.REMEMBERED -> MobileBottomSheet(onDismissRequest = { sheet = null }) {
            StageSheetHeader(stringResource(R.string.sports_remembered_channels))
            c.rememberedChannels.forEach { (key, ch) ->
                StageMenuItem(
                    label = (SportsCatalog.league(key)?.name ?: key.substringAfter('/')) + sep + ch.name,
                    onClick = { vm.update { it.copy(rememberedChannels = it.rememberedChannels - key) } },
                    icon = MobileIcons.Delete,
                    value = stringResource(R.string.common_delete),
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String?, on: Boolean, onClick: () -> Unit) {
    StageSettingRow(title, icon = icon, subtitle = line, onClick = onClick) { StageSwitch(on) }
}

@Composable
private fun OpensRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String?, value: String, onClick: () -> Unit) {
    StageSettingRow(title, icon = icon, subtitle = line, onClick = onClick) { StageRowValue(value, tv.own.owntv.mobile.ui.stage.StageChevron.FORWARD) }
}

/** A list of codes, each ticked or not: the commentary languages or the countries. */
@Composable
private fun CodeListSheet(title: String, codes: List<Pair<String, String>>, chosen: Set<String>, onDismiss: () -> Unit, onToggle: (String) -> Unit) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(title)
        SheetScroll {
            codes.forEach { (code, label) -> StageMenuItem(label, { onToggle(code) }, value = code.takeIf { it != label }, checked = code in chosen) }
        }
    }
}

/** "My teams", "Followed leagues", both, or Off — what Goal alerts covers on this profile. */
@Composable
private fun alertsSummary(c: SportsConfig): String {
    val a = c.alerts
    if (!a.enabled) return stringResource(R.string.common_off)
    return listOfNotNull(
        stringResource(R.string.sports_my_teams).takeIf { a.myTeams },
        stringResource(R.string.sports_followed_leagues).takeIf { a.followedLeagues },
    ).joinToString(", ").ifBlank { stringResource(R.string.common_on) }
}

/** An Auto rebuild choice: "Every 4 hours", "On every app start", "Manual". */
@Composable
private fun rebuildLabel(mode: AutoRebuild): String = when (mode) {
    AutoRebuild.APP_START -> stringResource(R.string.sports_auto_rebuild_app_start)
    AutoRebuild.MANUAL -> stringResource(R.string.settings_manual)
    else -> SportsRefreshPolicy.hoursOf(mode).let { h -> pluralStringResource(R.plurals.sports_auto_rebuild_hours, h, h) }
}

/** What a long press on a sport or a chosen league reorders (the phone's Move). */
private class MoveList(val title: String, val names: List<String>, val start: Int, val commit: (List<Int>) -> Unit)

/**
 * Sports and leagues (phone layout): the sports, a tap opening that sport's leagues as switches, the
 * chosen ones first in the user's order. On a tablet both panes sit side by side, as on the TV. A long
 * press on a sport or a chosen league offers Move, which opens the phone's reorder sheet (Save / Cancel).
 */
@Composable
fun SportsLeaguesPage(vm: SportsSettingsViewModel = koinViewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    val sports = c.orderSports(SportsCatalog.sports)
    var open by rememberSaveable { mutableStateOf<Sport?>(null) }
    var menu by remember { mutableStateOf<MoveList?>(null) }
    var moving by remember { mutableStateOf<MoveList?>(null) }
    // Bumped once a saved order has reached the settings (the save is async), so the pane re-orders then.
    var orderVersion by remember { mutableStateOf(0) }
    var reorderPending by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(c.leagues) { if (reorderPending) { reorderPending = false; orderVersion++ } }
    val twoPane = isExpandedWidth()
    val sportNames = sports.map { sportLabel(it) }
    val sportMove = { i: Int -> MoveList(sportNames[i], sportNames, i) { order -> vm.setSportOrder(order.map { sports[it] }) } }

    val sportsPane = @Composable { modifier: Modifier ->
        Column(modifier) {
            PageNote(stringResource(R.string.sports_load_time_warning))
            sports.forEachIndexed { i, s ->
                val chosen = SportsCatalog.leaguesOf(s).count { it.key in c.leagues }
                StageSettingRow(
                    sportNames[i], icon = MobileIcons.Sports, current = twoPane && open == s,
                    onClick = { open = s }, onLongClick = { menu = sportMove(i) },
                ) { StageRowValue(chosen.takeIf { it > 0 }?.toString().orEmpty(), tv.own.owntv.mobile.ui.stage.StageChevron.FORWARD) }
            }
        }
    }
    val leaguesPane = @Composable { sport: Sport, modifier: Modifier ->
        val all = SportsCatalog.leaguesOf(sport)
        val chosenHere = c.leagues.mapNotNull { k -> all.firstOrNull { it.key == k } }
        // Ordered once per sport (and after a Move), so a league switched on does not jump away under the finger.
        val ordered = remember(sport, orderVersion) { chosenHere + all.filter { it !in chosenHere } }
        val chosenNames = chosenHere.map { leagueLabel(it) }
        Column(modifier) {
            if (!twoPane) StageSectionHead(sportLabel(sport))
            ordered.forEach { l: League ->
                val on = l.key in c.leagues
                val name = leagueLabel(l)
                StageSettingRow(
                    name, icon = MobileIcons.Sports, subtitle = l.country,
                    onClick = { vm.toggleLeague(l.key) },
                    onLongClick = if (on && chosenHere.size > 1) {
                        { menu = MoveList(name, chosenNames, chosenHere.indexOf(l)) { order -> vm.setLeagueOrder(order.map { chosenHere[it].key }) } }
                    } else null,
                ) { StageSwitch(on) }
            }
        }
    }

    if (twoPane) {
        val sport = open ?: sports.first()
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            sportsPane(Modifier.width(320.dp).verticalScroll(rememberScrollState()))
            leaguesPane(sport, Modifier.weight(1f).verticalScroll(rememberScrollState()))
        }
    } else {
        val sport = open
        if (sport == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) { sportsPane(Modifier) }
        } else {
            androidx.activity.compose.BackHandler { open = null }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) { leaguesPane(sport, Modifier) }
        }
    }

    menu?.let { m ->
        MobileBottomSheet(onDismissRequest = { menu = null }) {
            StageSheetHeader(m.title)
            StageMenuItem(stringResource(R.string.content_move), { menu = null; moving = m }, icon = MobileIcons.Move)
        }
    }
    moving?.let { m ->
        ReorderSheet(
            title = m.title,
            openAt = m.start.toLong(),
            load = { m.names.mapIndexed { i, n -> ReorderItem(i.toLong(), n) } },
            onSave = { ids -> reorderPending = true; m.commit(ids.map { it.toInt() }); moving = null },
            onDismiss = { moving = null },
        )
    }
}

/** My teams: a search over the followed leagues' teams, the followed ones (tap stops following), and suggestions. */
@Composable
fun SportsTeamsPage(vm: SportsSettingsViewModel = koinViewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val found by produceState(emptyList<FollowedTeam>(), query) { value = vm.searchTeams(query) }
    val suggestions = vm.suggestions(c)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        item(key = "search") {
            StageSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.sports_search_teams), modifier = Modifier.padding(8.dp))
        }
        found.forEach { t ->
            item(key = "f-" + t.key) {
                StageSettingRow(t.name, icon = MobileIcons.Star, subtitle = sportLabel(t.sport), onClick = { vm.toggleTeam(t) }) { StageSwitch(c.teams.any { it.key == t.key }) }
            }
        }
        if (c.teams.isNotEmpty()) {
            item(key = "mine") { StageSectionHead(stringResource(R.string.sports_my_teams), count = c.teams.size) }
            c.teams.forEach { t ->
                item(key = "m-" + t.key) {
                    StageSettingRow(t.name, icon = MobileIcons.Star, subtitle = sportLabel(t.sport), onClick = { vm.toggleTeam(t) }) { StageSwitch(true) }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "sug") { StageSectionHead(stringResource(R.string.sports_suggested), count = suggestions.size) }
            suggestions.forEach { s ->
                item(key = "s-" + s.key) {
                    val line = if (s.team != null) pluralStringResource(R.plurals.sports_watched_matches, s.plays, s.plays) else stringResource(R.string.sports_league_label)
                    StageSettingRow(s.name, icon = MobileIcons.StarBorder, subtitle = line, onClick = {
                        val team = s.team
                        if (team != null) vm.toggleTeam(team) else vm.toggleLeague(s.key)
                    }) { StageRowValue(stringResource(R.string.sports_follow), plain = true) }
                }
            }
        }
    }
}

/** Goal alerts on the phone: on/off, when OwnTV is closed, what about, for whom, the live score card. */
@Composable
fun SportsAlertsPage(vm: SportsSettingsViewModel = koinViewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    val a = c.alerts
    fun edit(t: (tv.own.owntv.core.sports.GoalAlerts) -> tv.own.owntv.core.sports.GoalAlerts) = vm.update { it.copy(alerts = t(it.alerts)) }
    fun kind(k: AlertKind) = edit { it.copy(kinds = if (k in it.kinds) it.kinds - k else it.kinds + k) }
    SettingsPage {
        item(key = "main") {
            Column {
                SwitchRow(MobileIcons.Bell, stringResource(R.string.sports_channel_goal_alerts), stringResource(R.string.sports_goal_alerts_description), a.enabled) { vm.setAlerts(!a.enabled) }
                if (a.enabled) {
                    SwitchRow(MobileIcons.Bell, stringResource(R.string.sports_alerts_when_closed), stringResource(R.string.sports_alerts_when_closed_description), a.whenClosed) {
                        if (!a.whenClosed) tv.own.owntv.core.sports.MatchAlertHost.requestNotificationPermission?.invoke()
                        edit { it.copy(whenClosed = !it.whenClosed) }
                    }
                    StageSectionHead(stringResource(R.string.sports_alert_me_about))
                    SwitchRow(MobileIcons.Sports, stringResource(R.string.sports_kind_goals), null, AlertKind.GOALS in a.kinds) { kind(AlertKind.GOALS) }
                    SwitchRow(MobileIcons.Schedule, stringResource(R.string.sports_kind_kickoff_full_time), null, AlertKind.KICKOFF_FULL_TIME in a.kinds) { kind(AlertKind.KICKOFF_FULL_TIME) }
                    SwitchRow(MobileIcons.Schedule, stringResource(R.string.sports_event_half_time), null, AlertKind.HALF_TIME in a.kinds) { kind(AlertKind.HALF_TIME) }
                    SwitchRow(MobileIcons.Warning, stringResource(R.string.sports_stat_red_cards), null, AlertKind.RED_CARDS in a.kinds) { kind(AlertKind.RED_CARDS) }
                    StageSectionHead(stringResource(R.string.sports_alerts_for))
                    SwitchRow(MobileIcons.Star, stringResource(R.string.sports_my_teams), c.teams.joinToString(", ") { it.name }.ifBlank { null }, a.myTeams) { edit { it.copy(myTeams = !it.myTeams) } }
                    SwitchRow(MobileIcons.Layers, stringResource(R.string.sports_followed_leagues), stringResource(R.string.sports_followed_leagues_description), a.followedLeagues) { edit { it.copy(followedLeagues = !it.followedLeagues) } }
                    StageSectionHead(stringResource(R.string.sports_live_card))
                    SwitchRow(MobileIcons.LiveTv, stringResource(R.string.sports_live_card_offer), stringResource(R.string.sports_live_card_description), a.liveCard) { edit { it.copy(liveCard = !it.liveCard) } }
                    PageNote(stringResource(R.string.sports_alerts_footnote))
                }
            }
        }
    }
}
