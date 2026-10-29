package tv.own.owntv.mobile.ui.screens

import androidx.compose.runtime.Composable
import tv.own.owntv.mobile.ui.screens.library.LibraryTab
import tv.own.owntv.mobile.ui.screens.settings.AboutPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsBackupPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsErrorLogPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsLocalSyncPage

/**
 * One More page, chosen by its route — the same shape `SettingsGroupPage` has, and for the same
 * reason: a page is wired once, so it can never be reachable by a route and missing from it.
 *
 * Five of the eight are pages that already existed and only changed door. Favourites, Watch
 * history and Recordings are the ones that are genuinely new.
 */
@Composable
fun MoreLeafPage(
    leaf: MoreLeaf,
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    when (leaf) {
        MoreLeaf.FAVORITES -> FavoritesScreen(onOpenChannel, onOpenItem)
        MoreLeaf.HISTORY -> WatchHistoryScreen(onOpenChannel, onOpenItem, onOpenPlayer)
        // Poterx: both could copy the built-in account out of the app; a locked build shows a note instead.
        MoreLeaf.BACKUP -> if (tv.own.owntv.mobile.poterx.LockedSource.enabled) tv.own.owntv.mobile.poterx.LockedInfoPage() else SettingsBackupPage()
        MoreLeaf.LOCAL_SYNC -> if (tv.own.owntv.mobile.poterx.LockedSource.enabled) tv.own.owntv.mobile.poterx.LockedInfoPage() else SettingsLocalSyncPage()
        MoreLeaf.ERROR_LOG -> SettingsErrorLogPage()
        MoreLeaf.ABOUT -> AboutPage(onOpenLanguage)
    }
}
