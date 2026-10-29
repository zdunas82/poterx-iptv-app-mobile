package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon

/** The item a long press was made on, wherever it was made. [artUrl] is its poster, or a channel's logo. */
data class ContentTarget(val type: MediaType, val id: Long, val title: String, val artUrl: String? = null)

/**
 * The short long-press menu: everything an item can do to itself, and nothing that needs the list it
 * would normally sit in — reordering a folder from a Home rail has no folder to reorder.
 *
 * The keys are the ones the rest of the app uses, so the order the user arranged in Settings still
 * applies here. The item's picture is on top, favourite and download under LIBRARY, hide under
 * ORGANIZE.
 */
@Composable
fun ContentActionsMenu(
    target: ContentTarget,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = buildList {
        add(
            SheetAction(
                key = "favourite",
                label = stringResource(
                    if (isFavorite) R.string.content_remove_favourite else R.string.content_add_favourite,
                ),
                icon = if (isFavorite) MobileIcons.Favorite else MobileIcons.FavoriteBorder,
                group = 0,
                onClick = onToggleFavorite,
            ),
        )
        if (target.type != MediaType.LIVE) {
            add(
                SheetAction(
                    key = "download",
                    label = stringResource(
                        if (target.type == MediaType.MOVIE) R.string.content_download
                        else R.string.content_download_all_episodes,
                    ),
                    icon = MobileIcons.Download,
                    group = 0,
                    onClick = onDownload,
                ),
            )
        }
        add(
            SheetAction(
                key = "hide",
                label = stringResource(
                    if (target.type == MediaType.LIVE) R.string.content_hide_channel else R.string.common_hide,
                ),
                icon = MobileIcons.VisibilityOff,
                destructive = true,
                group = 1,
                onClick = onHide,
            ),
        )
    }

    ContentMenuSheet(
        menu = when (target.type) {
            MediaType.LIVE -> ContentMenu.LIVE
            MediaType.MOVIE -> ContentMenu.MOVIE
            else -> ContentMenu.SERIES
        },
        title = target.title,
        actions = actions,
        onDismiss = onDismiss,
        groupLabels = mapOf(
            0 to stringResource(R.string.content_menu_group_library),
            1 to stringResource(R.string.content_menu_group_organise),
        ),
        leading = { TargetArt(target) },
    )
}

/** The sheet header's picture: a poster 34 × 50, or a channel's logo 44 × 32 with no tile. */
@Composable
private fun TargetArt(target: ContentTarget) {
    val live = target.type == MediaType.LIVE
    val dim = LocalStagePalette.current.dim
    if (live) {
        ChannelLogoImage(
            url = target.artUrl,
            fallback = { StageIcon(MobileIcons.LiveTv, 18.dp, dim) },
            modifier = Modifier.size(44.dp, 32.dp),
        )
    } else {
        AsyncImage(
            target.artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp, 50.dp).clip(RoundedCornerShape(6.dp)),
        )
    }
}
