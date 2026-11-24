package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet

/** A whole show or season waiting for the user's yes: how many [episodes] it queues, and how. */
class BulkDownloadAsk(val episodes: Int, val onConfirm: () -> Unit)

/**
 * "Download all episodes" and "Download season" ask first, saying how many episodes that is — one tap
 * queues every file of the show, and a long one fills the phone before anyone notices.
 */
@Composable
fun BulkDownloadConfirm(ask: BulkDownloadAsk, onDismiss: () -> Unit) {
    StageConfirmSheet(
        title = pluralStringResource(R.plurals.content_download_episodes_confirm, ask.episodes, ask.episodes),
        text = stringResource(R.string.content_download_episodes_confirm_message),
        confirm = stringResource(R.string.content_download),
        confirmIcon = MobileIcons.Download,
        onConfirm = ask.onConfirm,
        onDismiss = onDismiss,
    )
}
