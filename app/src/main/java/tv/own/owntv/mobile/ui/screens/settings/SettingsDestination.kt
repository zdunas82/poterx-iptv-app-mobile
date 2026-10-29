package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.setup.SetupEntry

/**
 * One full page, chosen by its route. Each says where it is in the crumb ("‹ Settings · Player"); the
 * pages with a count of their own say it themselves. [onAddSource] is the setup flow, a route outside settings.
 */
@Composable
fun SettingsLeafPage(leaf: SettingsLeaf, onSetup: (SetupEntry) -> Unit) {
    when (leaf) {
        // Add a source sits under Playlists ("‹ Settings · Playlists").
        SettingsLeaf.ADD_SOURCE -> SettingsCrumb(stringResource(R.string.settings_playlists))
        !in OWN_CRUMB -> SettingsCrumb(null, leaf.group)
        else -> Unit
    }
    when (leaf) {
        SettingsLeaf.PLAYLISTS -> SettingsPlaylistsPage()
        SettingsLeaf.ADD_SOURCE -> AddSourcePage(onSetup)
        SettingsLeaf.EPG_SOURCES -> SettingsEpgSourcesPage()
        SettingsLeaf.HIGHLIGHT -> SettingsHighlightPage()
        SettingsLeaf.GLASS_EFFECT -> SettingsGlassPage()
        SettingsLeaf.FONTS -> SettingsFontsPage()
        SettingsLeaf.HOME -> SettingsHomePage()
        SettingsLeaf.BROWSING -> SettingsBrowsingPage()
        SettingsLeaf.CONTENT_MENUS -> SettingsMenusPage()
        SettingsLeaf.CUSTOMIZE -> SettingsCustomizePage()
        SettingsLeaf.METADATA -> SettingsMetadataPage()
        SettingsLeaf.OPEN_SUBTITLES -> SettingsOpenSubtitlesPage()
        SettingsLeaf.EXTERNAL_PLAYER -> ExternalPlayerPage()
        SettingsLeaf.SUBTITLE_APPEARANCE -> SettingsSubtitleAppearancePage()
        SettingsLeaf.LANGUAGE -> SettingsLanguagePage()
        SettingsLeaf.PROXY -> SettingsProxyPage()
        SettingsLeaf.DNS -> SettingsDnsPage()
        SettingsLeaf.SPORTS -> tv.own.owntv.mobile.ui.screens.sports.SportsSettingsPage()
        SettingsLeaf.SPORTS_LEAGUES -> tv.own.owntv.mobile.ui.screens.sports.SportsLeaguesPage()
        SettingsLeaf.SPORTS_TEAMS -> tv.own.owntv.mobile.ui.screens.sports.SportsTeamsPage()
        SettingsLeaf.SPORTS_ALERTS -> tv.own.owntv.mobile.ui.screens.sports.SportsAlertsPage()
        SettingsLeaf.CATCHUP_SOURCES, SettingsLeaf.LIVE_ENGINE_SOURCES, SettingsLeaf.VOD_ENGINE_SOURCES,
        SettingsLeaf.LATENCY_SOURCES, SettingsLeaf.PREROLL_SOURCES, SettingsLeaf.TUNE_TIMEOUT_SOURCES -> PerPlaylistPage(leaf)
    }
}

/** Pages whose crumb carries a count of their own. */
private val OWN_CRUMB = setOf(SettingsLeaf.PLAYLISTS, SettingsLeaf.EPG_SOURCES, SettingsLeaf.CUSTOMIZE, SettingsLeaf.GLASS_EFFECT)

/** What a settings route calls itself in the header, or null when it is not one. */
fun settingsPageTitleRes(route: String?): Int? = when (route) {
    tv.own.owntv.mobile.ui.nav.MobileDestination.SETTINGS.route -> tv.own.owntv.mobile.R.string.common_nav_settings
    else -> settingsLeafOf(route)?.titleRes ?: SettingsGroup.entries.firstOrNull { it.route == route }?.titleRes
}

/** A settings group page or full page: its crumb is its own ("‹ Settings · …"), not the shell's "‹ where from". */
fun isSettingsPage(route: String?): Boolean = route != null && route.startsWith("settings/")
