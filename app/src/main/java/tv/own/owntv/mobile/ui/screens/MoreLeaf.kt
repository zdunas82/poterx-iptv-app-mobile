package tv.own.owntv.mobile.ui.screens

import androidx.annotation.StringRes
import tv.own.owntv.mobile.R

/**
 * A page that hangs off the More tab.
 *
 * These are the things that were never settings — a profile manager, two lists of your own data, a
 * place that talks to another device, a log, and a page of facts. Each is a route of its own, and
 * **deliberately not a `settings/...` route**: an item that leaves Settings leaves, so the settings
 * search can no longer find it and a result can never point somewhere the row is not.
 *
 * Route and the page's title both read this list, so a page can never be reachable by one and
 * nameless in the other.
 */
enum class MoreLeaf(slug: String, @param:StringRes val titleRes: Int) {
    FAVORITES("favorites", R.string.content_category_favorites),
    HISTORY("history", R.string.content_category_history),
    BACKUP("backup", R.string.settings_backup_title),
    LOCAL_SYNC("localsync", R.string.local_sync_title),
    ERROR_LOG("errorlog", R.string.settings_playback_error_log),
    ABOUT("about", R.string.settings_about),
    ;

    /** `more/backup` — the tab's own route with the page hung off it. */
    val route: String = "more/$slug"
}

/** The bar's title for a More page, or null when the route is not one. */
@StringRes
fun morePageTitleRes(route: String?): Int? =
    MoreLeaf.entries.firstOrNull { it.route == route }?.titleRes
