package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.SheetScroll
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * The line under the channel name while the controls show: "● LIVE **Frankfurt 2–1 Köln** · 63'".
 * It asks for the score only while it is drawn. Spoiler-free shows the two teams without the score.
 */
@Composable
fun MatchScoreLine(playback: SportsPlayback, match: Match, spoilerFree: Boolean) {
    val m by remember(match.key) { playback.score(match) }.collectAsStateWithLifecycle(match)
    val context = LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val pre = m.state == MatchState.PRE
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (m.isLive) LiveMark("")
        val status = when {
            m.isLive -> clockText(m)
            pre -> kickOffText(m.startMs)
            else -> m.statusText
        }
        Text(
            (if (spoilerFree || pre) MatchNotifications.pair(context, m) else MatchNotifications.scoreLine(context, m)) + sep + status,
            style = stageText(12f, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The other channels showing the match, as a sheet: the backup when a stream stalls. */
@Composable
fun OtherChannelsSheet(session: SportsSession, playingId: Long?, onSwitch: (ChannelEntity) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(MatchNotifications.pair(context, session.match), eyebrow = stringResource(R.string.sports_other_channels_title))
        SheetScroll {
            session.options.forEach { o ->
                val playing = o.channel.id == playingId
                ChannelRow(o, session.match, onClick = { if (playing) onDismiss() else onSwitch(o.channel) }, onHold = null, playing = playing)
            }
        }
    }
}
