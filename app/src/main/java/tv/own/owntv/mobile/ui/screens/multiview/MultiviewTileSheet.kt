package tv.own.owntv.mobile.ui.screens.multiview

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * Long-press on a tile: what can be done with that one tile.
 *
 * The same actions as the television's tile menu, as a bottom sheet. Not a [ContentMenuSheet]: those
 * carry a user-arranged order saved against a core `ContentMenu` value, and a tile menu is four fixed
 * actions that nobody has asked to rearrange. **"Record this" is deliberately absent** until Feature
 * A exists — an entry that does nothing is worse than one that is not there yet.
 */
@Composable
fun MultiviewTileSheet(
    filled: Boolean,
    soundOnly: Boolean,
    /** Null once the grid has reached the maximum the user allowed in Settings. */
    onAddTile: (() -> Unit)?,
    onChangeChannel: () -> Unit,
    onFullscreen: () -> Unit,
    onSound: () -> Unit,
    onSoundOnly: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.multiview_button)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title)
        Row(
            label = stringResource(R.string.multiview_tile_change_channel),
            icon = MobileIcons.LiveTv,
            onClick = onChangeChannel,
        )
        if (onAddTile != null) {
            Row(
                label = stringResource(R.string.multiview_add_channel),
                icon = MobileIcons.Add,
                onClick = onAddTile,
            )
        }
        if (filled) {
            Row(
                label = stringResource(R.string.multiview_tile_fullscreen),
                icon = MobileIcons.OpenInNew,
                onClick = onFullscreen,
            )
            Row(
                label = stringResource(R.string.multiview_audio_tile),
                icon = MobileIcons.VolumeUp,
                onClick = onSound,
            )
            // Give up this tile's picture and keep only its sound — or take the picture back.
            Row(
                label = stringResource(
                    if (soundOnly) R.string.multiview_tile_show_picture else R.string.multiview_tile_sound_only,
                ),
                icon = MobileIcons.Audiotrack,
                onClick = onSoundOnly,
            )
            Row(
                label = stringResource(R.string.multiview_tile_remove),
                icon = MobileIcons.Close,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun Row(label: String, icon: ImageVector, onClick: () -> Unit) {
    StageMenuItem(label, onClick = onClick, icon = icon)
}
