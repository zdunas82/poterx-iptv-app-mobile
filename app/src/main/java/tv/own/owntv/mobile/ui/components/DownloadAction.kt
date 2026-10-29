package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.download.DownloadStripKind
import tv.own.owntv.core.download.DownloadStripState
import tv.own.owntv.core.download.downloadStripFor
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.mobile.R

/**
 * What the download control on a film, a show or an episode should currently say and do.
 *
 * The in-flight half is core's [downloadStripFor], so the phone and the television read the same
 * rows the same way. "Already downloaded" is the one state that function deliberately does not
 * report — it returns null both for "nothing here" and for "all finished" so a strip can hide — so
 * it is decided here, from the rows.
 */
data class DownloadAction(
    val strip: DownloadStripState?,
    val completed: Boolean,
) {
    /** True when tapping removes something — a running transfer or a file already on the phone. */
    val deletes: Boolean get() = completed || (strip != null && strip.kind != DownloadStripKind.FAILED)
}

fun downloadActionFor(rows: List<DownloadEntity>): DownloadAction = DownloadAction(
    strip = downloadStripFor(rows),
    completed = rows.isNotEmpty() && rows.all { it.status == DownloadStatus.COMPLETED },
)

fun DownloadAction.icon(): ImageVector = when {
    completed -> MobileIcons.CheckCircle
    strip?.kind == DownloadStripKind.FAILED -> MobileIcons.Refresh
    strip?.kind == DownloadStripKind.QUEUED -> MobileIcons.Schedule
    strip?.kind == DownloadStripKind.PAUSED -> MobileIcons.Pause
    strip != null -> MobileIcons.Download
    else -> MobileIcons.Download
}

/**
 * The label, which doubles as the icon button's description. A transfer in flight names its state
 * and count; a finished or failed one names what tapping will do, because that is what changes.
 */
@Composable
fun DownloadAction.label(idle: String = stringResource(R.string.content_download)): String {
    val s = strip
    return when {
        completed -> stringResource(R.string.common_delete)
        s == null -> idle
        s.kind == DownloadStripKind.FAILED -> stringResource(R.string.common_retry)
        s.kind == DownloadStripKind.QUEUED -> pluralStringResource(R.plurals.content_queued_items, s.count, s.count)
        s.kind == DownloadStripKind.PAUSED -> pluralStringResource(R.plurals.content_paused_items, s.count, s.count)
        else -> pluralStringResource(R.plurals.content_downloading_items, s.count, s.count)
    }
}

/**
 * Tapping: nothing yet → download it; failed → try again; anything else (running, queued, paused,
 * or already on the phone) → remove it. Cancelling a transfer and deleting a finished file are the
 * same reversible act from the user's side, so they are the same tap.
 */
fun DownloadAction.onTap(
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
): () -> Unit = when {
    completed -> onDelete
    strip?.kind == DownloadStripKind.FAILED -> onRetry
    strip != null -> onDelete
    else -> onDownload
}
