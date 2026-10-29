package tv.own.owntv.mobile.ui.screens.settings

import androidx.annotation.StringRes
import tv.own.owntv.mobile.R

/**
 * A full page that a group row opens — the second and last layer under a group: Settings → a group →
 * a page. Registered once with its group and its title, so the route, the header's title and crumb and the
 * settings search all read the same list.
 */
enum class SettingsLeaf(
    val group: SettingsGroup,
    slug: String,
    @param:StringRes val titleRes: Int,
) {
    PLAYLISTS(SettingsGroup.SOURCES, "playlists", R.string.settings_playlists),
    ADD_SOURCE(SettingsGroup.SOURCES, "add", R.string.content_add_source),
    EPG_SOURCES(SettingsGroup.SOURCES, "epg", R.string.settings_epg_sources),
    CATCHUP_SOURCES(SettingsGroup.SOURCES, "catchup-playlists", R.string.settings_catchup_timezone_per_playlist),

    HIGHLIGHT(SettingsGroup.APPEARANCE, "highlight", R.string.settings_selection_highlight),
    GLASS_EFFECT(SettingsGroup.APPEARANCE, "glass", R.string.settings_glass_bg_title),
    FONTS(SettingsGroup.APPEARANCE, "fonts", R.string.settings_title_fonts),

    HOME(SettingsGroup.LAYOUT, "home", R.string.settings_home_screen),
    BROWSING(SettingsGroup.LAYOUT, "browsing", R.string.settings_browsing_lists),
    CONTENT_MENUS(SettingsGroup.LAYOUT, "menus", R.string.settings_content_menus_title),

    CUSTOMIZE(SettingsGroup.CONTENT, "customize", R.string.settings_customize_short_mobile),
    METADATA(SettingsGroup.CONTENT, "metadata", R.string.settings_metadata),
    OPEN_SUBTITLES(SettingsGroup.CONTENT, "opensubtitles", R.string.settings_open_subtitles),

    LIVE_ENGINE_SOURCES(SettingsGroup.PLAYER, "live-engine-playlists", R.string.settings_live_engine_per_playlist),
    VOD_ENGINE_SOURCES(SettingsGroup.PLAYER, "vod-engine-playlists", R.string.settings_vod_engine_per_playlist),
    EXTERNAL_PLAYER(SettingsGroup.PLAYER, "external", R.string.settings_external_player),

    SUBTITLE_APPEARANCE(SettingsGroup.SOUND, "subtitles", R.string.settings_subtitle_appearance),

    LATENCY_SOURCES(SettingsGroup.LIVE, "latency-playlists", R.string.settings_live_latency_per_playlist),
    PREROLL_SOURCES(SettingsGroup.LIVE, "preroll-playlists", R.string.settings_live_preroll_per_playlist),
    TUNE_TIMEOUT_SOURCES(SettingsGroup.LIVE, "give-up-playlists", R.string.settings_live_tune_timeout_per_playlist),

    LANGUAGE(SettingsGroup.APP, "language", R.string.settings_language),
    PROXY(SettingsGroup.APP, "proxy", R.string.common_proxy),
    DNS(SettingsGroup.APP, "dns", R.string.settings_dns),

    // Sports Hub: one page in App, and the pages its rows open.
    SPORTS(SettingsGroup.APP, "sports", R.string.sports_title),
    SPORTS_LEAGUES(SettingsGroup.APP, "sports/leagues", R.string.sports_leagues),
    SPORTS_TEAMS(SettingsGroup.APP, "sports/teams", R.string.sports_my_teams),
    SPORTS_ALERTS(SettingsGroup.APP, "sports/alerts", R.string.sports_channel_goal_alerts),
    ;

    /** `settings/content/customize` — the group's own route with the page hung off it. */
    val route: String = "${group.route}/$slug"
}

/** The page a route names, or null when the route is a group page or not settings at all. */
fun settingsLeafOf(route: String?): SettingsLeaf? =
    SettingsLeaf.entries.firstOrNull { it.route == route }
