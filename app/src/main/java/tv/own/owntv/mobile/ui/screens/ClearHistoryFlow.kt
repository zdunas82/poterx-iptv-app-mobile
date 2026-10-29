package tv.own.owntv.mobile.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/** The history scopes the clear-history sheet offers, "All history" first. */
private val HISTORY_SCOPES: List<MediaType?> =
    listOf(null, MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)

/**
 * Throwing watch history away: pick a scope, then confirm it — two Stage sheets, the same title,
 * description, four scopes and confirm the old Settings → Data page had. Reached from More › History's
 * red trash tool, where it acts.
 */
@Composable
fun ClearHistoryFlow(onClear: (MediaType?) -> Unit, onDismiss: () -> Unit) {
    // An index, not the scope itself: null is a real scope here — it means "all history".
    var confirming by remember { mutableIntStateOf(-1) }

    if (confirming < 0) {
        MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_clear_history)) {
            StageSheetHeader(title = stringResource(R.string.settings_clear_history), subtitle = stringResource(R.string.settings_choose_history))
            HISTORY_SCOPES.forEachIndexed { index, scope ->
                StageMenuItem(stringResource(scope.scopeLabelRes()), onClick = { confirming = index }, icon = MobileIcons.History, chevron = true)
            }
        }
        return
    }

    val label = stringResource(HISTORY_SCOPES[confirming].scopeLabelRes())
    MobileBottomSheet(onDismissRequest = onDismiss, title = label) {
        StageSheetHeader(
            title = stringResource(R.string.settings_clear_history_confirm, label),
            subtitle = stringResource(R.string.settings_cannot_undo),
        )
        StageMenuItem(
            stringResource(R.string.settings_yes_clear),
            onClick = { onClear(HISTORY_SCOPES[confirming]); onDismiss() },
            icon = MobileIcons.Delete,
            danger = true,
        )
        StageMenuItem(stringResource(R.string.settings_no), onClick = onDismiss, icon = MobileIcons.Close)
    }
}

private fun MediaType?.scopeLabelRes() = when (this) {
    MediaType.LIVE -> R.string.settings_history_live
    MediaType.MOVIE -> R.string.settings_history_movies
    MediaType.SERIES -> R.string.settings_history_series
    else -> R.string.settings_all_history
}
