package tv.own.owntv.mobile.ui.screens.live

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.DateFormat
import java.util.Date

/**
 * The catch-up picker: what this channel's archive still holds, newest first, then "Go back to…" for a
 * bare time. A Stage sheet: the picture keeps playing above it.
 */
@Composable
internal fun CatchupSheet(
    channelName: String,
    load: suspend () -> List<EpgProgrammeEntity>,
    onPick: (EpgProgrammeEntity) -> Unit,
    onJumpBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var programmes by remember { mutableStateOf<List<EpgProgrammeEntity>?>(null) }
    LaunchedEffect(Unit) { programmes = load() }
    // Programme clocks follow the phone's own locale, so 20:00 and 8:00 PM are both right somewhere.
    val locales = LocalConfiguration.current.locales
    val times = remember(locales) { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val separator = stringResource(R.string.content_epg_bits_separator)
    val title = stringResource(R.string.content_catchup_title, channelName)

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        val list = programmes
        if (list != null && list.isEmpty()) {
            Text(
                text = stringResource(R.string.content_catchup_empty),
                style = stageText(13f, 500),
                color = LocalStagePalette.current.muted,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(list.orEmpty(), key = { it.id }) { programme ->
                StageMenuItem(
                    label = programme.title,
                    onClick = { onPick(programme); onDismiss() },
                    icon = MobileIcons.Catchup,
                    value = dates.format(Date(programme.startMs)) + separator + times.format(Date(programme.startMs)),
                )
            }
            item(key = "jump-back") {
                StageMenuItem(
                    label = stringResource(R.string.content_catchup_jump),
                    onClick = onJumpBack,
                    icon = MobileIcons.Schedule,
                )
            }
        }
    }
}
