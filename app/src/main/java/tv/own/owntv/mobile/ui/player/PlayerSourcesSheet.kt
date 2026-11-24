package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.ChannelVersion
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * "Sources": the live channel playing and its other versions — the same channel in another quality,
 * category or playlist, best picture first — from core's ChannelVersions, as on the television. The
 * one playing is ticked; a tap on another switches to it.
 */
@Composable
fun SourcesSheet(
    load: suspend () -> List<ChannelVersion>,
    playingId: Long,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val versions by produceState<List<ChannelVersion>?>(null) { value = load() }
    val title = stringResource(R.string.player_tool_sources)
    val separator = stringResource(R.string.content_epg_bits_separator)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        val list = versions ?: return@MobileBottomSheet
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(list, key = { it.channel.id }) { v ->
                val playing = v.channel.id == playingId
                StageMenuItem(
                    listOfNotNull(v.name, v.categoryName, v.playlistName).filter { it.isNotBlank() }.distinct().joinToString(separator),
                    onClick = { onDismiss(); if (!playing) onPick(v.channel) },
                    icon = MobileIcons.LiveTv,
                    value = v.tags.joinToString(" ").ifEmpty { null },
                    checked = playing,
                )
            }
            // Only the channel itself: say so, rather than leave a list of one that looks like a fault.
            if (list.size <= 1) item(key = "none") {
                StageMenuItem(stringResource(R.string.player_sources_none), onClick = onDismiss, enabled = false)
            }
        }
    }
}
