package tv.own.owntv.mobile.ui.shell

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StagePlaylistMark
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Show playlist, the television's sheet: All playlists, then each playlist with its mark
 * and its channel count ("55K channels"; Settings › Layout › Navigation › Show counts). The current
 * one is filled with a ✓ and carries no count, as on the television. `All playlists` is id -1, the value Settings stores.
 */
@Composable
fun PlaylistSheet(
    playlists: List<SourceEntity>,
    activeId: Long,
    names: List<String>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings: SettingsRepository = koinInject()
    val channelDao: ChannelDao = koinInject()
    val showCounts by settings.sheetCounts.collectAsStateWithLifecycle(true)
    val counts by produceState(emptyMap<Long, Int>(), playlists) {
        value = playlists.associate { it.id to runCatching { channelDao.countForSourceOnce(it.id) }.getOrDefault(0) }
    }
    val locale = LocalConfiguration.current.locales[0]
    val compact = remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
    val title = stringResource(R.string.content_playlist_picker_title)

    fun pick(id: Long) {
        onSelect(id)
        onDismiss()
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.content_playlist_picker_subtitle))
        StageMenuItem(
            label = stringResource(R.string.content_all_playlists),
            onClick = { pick(-1L) },
            icon = MobileIcons.Layers,
            filled = activeId == -1L,
            checked = activeId == -1L,
        )
        playlists.forEachIndexed { i, source ->
            val current = source.id == activeId
            val count = counts[source.id]
            StageMenuItem(
                label = names[i],
                onClick = { pick(source.id) },
                leading = { StagePlaylistMark(PlaylistMark.of(names[i], i)) },
                filled = current,
                checked = current,
                count = if (showCounts && !current && count != null) {
                    pluralStringResource(R.plurals.sync_count_channels, count, compact.format(count))
                } else null,
            )
        }
        Text(
            stringResource(R.string.content_playlist_marks_note),
            style = stageText(11.5f, 500),
            color = LocalStagePalette.current.dim,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
    }
}
