package tv.own.owntv.mobile.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.rememberClockTick
import tv.own.owntv.mobile.ui.screens.guide.programmeEyebrow
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * The reminder prompt (Programme reminders = Ask to switch), the television's as a Stage sheet: the bell
 * and "Starts in 5 min · 20:05 – 21:40 · 95 min · Sky Cinema Family" in the accent, the title, then
 * Watch channel and Dismiss.
 */
@Composable
internal fun ReminderPrompt(reminder: ReminderEntity, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val nowMs by rememberClockTick()
    val programme = EpgProgrammeEntity(sourceId = 0, epgChannelId = "", startMs = reminder.startMs, stopMs = reminder.stopMs, title = reminder.title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = reminder.title) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StageIcon(MobileIcons.Bell, 14.dp, accent)
                Text(
                    programmeEyebrow(programme, nowMs, reminder.channelName),
                    style = stageText(11.5f, 700),
                    color = accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                reminder.title,
                style = stageText(22f, 800),
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageButton(onClick = onDismiss, text = stringResource(R.string.common_dismiss), modifier = Modifier.weight(1f))
            StageButton(
                onClick = onWatch,
                text = stringResource(R.string.content_epg_watch_channel),
                icon = MobileIcons.LiveTv,
                style = StageButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
