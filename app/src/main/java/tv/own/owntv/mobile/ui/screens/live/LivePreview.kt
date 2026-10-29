package tv.own.owntv.mobile.ui.screens.live

import tv.own.owntv.mobile.ui.screens.home.formatYear
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetOpen
import tv.own.owntv.mobile.ui.player.VideoStage
import tv.own.owntv.mobile.ui.screens.home.timeLeftText
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageLiveBadge
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.StageSpecChips
import tv.own.owntv.mobile.ui.stage.stageShadow
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Playing: the preview on top of Live TV — the TV's list + preview, stacked. The 16:9 video
 * (radius 20) with the LIVE badge, the stream's facts (quality, frame rate, sound) and the channel in the
 * lower corner, ⤢ for full screen; under it the programme (20 / 800), its details line with the time left
 * at the end, the progress, and what is on next (accent time) and after it. A tap on the video, like a
 * second tap on the playing row, opens full screen.
 */
@Composable
internal fun LivePreview(
    tuner: LiveTuner,
    channel: ChannelEntity,
    nowNext: EpgNowNext?,
    nowMs: Long,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Beside the list: the video at 16:9 across the pane instead of 212 dp, no side margin. */
    wide: Boolean = false,
    /** A tablet's pane: the bigger title and the programme's plot. */
    tablet: Boolean = false,
) {
    val engine by tuner.activeEngine.collectAsStateWithLifecycle()
    val previewing by tuner.previewing.collectAsStateWithLifecycle()
    val chips by (if (previewing) tuner.exoEngine else engine).streamChips.collectAsStateWithLifecycle()
    val name = remember(channel.name) { ProviderTags.parse(channel.name).name }
    val fullscreen = stringResource(R.string.multiview_tile_fullscreen)
    val shape = RoundedCornerShape(StageRadii.Video)
    val dark = !LocalStagePalette.current.isLight
    Column(modifier.fillMaxWidth().padding(start = if (wide) 0.dp else 14.dp, end = 14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.aspectRatio(16f / 9f) else Modifier.height(212.dp))
                // `.video`: 0 20 50 black 55% (light: 0 14 32 rgb(20,40,32) 22%).
                .then(
                    if (dark) Modifier.stageShadow(Color.Black.copy(alpha = 0.55f), 50.dp, 20.dp, shape)
                    else Modifier.stageShadow(Color(20, 40, 32).copy(alpha = 0.22f), 32.dp, 14.dp, shape),
                )
                .clip(shape)
                .background(Color.Black)
                .clickable(onClickLabel = fullscreen, onClick = onFullscreen),
        ) {
            // The surface is punched through the window, so a sheet's glass showed it sharp. Under a sheet it
            // is covered with black rather than taken away: a new surface waits 2–5 s for the next keyframe.
            VideoStage(player = tuner.player, modifier = Modifier.fillMaxSize())
            if (sheetOpen()) Box(Modifier.fillMaxSize().background(Color.Black))
            // `.shade`: black 72% at the bottom, clear by 45% up.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            if (dark) Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.dp.toPx(), StageRadii.Video.toPx()) })
            StageLiveBadge(stringResource(R.string.player_live), Modifier.align(Alignment.TopStart).padding(10.dp))
            // Three facts; the aspect ratio and the declared bitrate are left out, as on the TV.
            val facts = chips.filterNot { it.contains(':') || it.endsWith("Mbps") }
            if (facts.isNotEmpty()) StageSpecChips(facts, Modifier.align(Alignment.TopEnd).padding(10.dp))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp, 28.dp).clip(RoundedCornerShape(StageRadii.Plate)), contentAlignment = Alignment.Center) {
                    ChannelLogoImage(
                        url = channel.displayLogoUrl,
                        modifier = Modifier.fillMaxSize(),
                        plateOn = Color.Black,
                        fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, Color(0xFFA7B3AE)) },
                    )
                }
                Column(Modifier.weight(1f)) {
                    channel.number?.takeIf { it > 0 }?.let {
                        Text(formatYear(it), style = stageText(10.5f, 500), color = Color(0xFFCFD8D4), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(name, style = stageText(13.5f, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StageIcon(MobileIcons.OpenInFull, 18.dp, Color.White, Modifier.semantics { contentDescription = fullscreen })
            }
        }
        val now = nowNext?.now
        if (now != null) {
            // Beside the list the block is title, line and progress — 18, or 26 with the plot on a
            // tablet; what comes next is the phone's, where the list is under it.
            ProgrammeBlock(
                now, nowNext.nowDetails, nowNext.next.takeIf { !wide }, nowNext.upcoming.firstOrNull(), nowMs,
                titleSize = if (tablet) 26f else if (wide) 18f else 20f,
                plot = now.description?.takeIf { tablet && it.isNotBlank() },
            )
        }
    }
}

/** Under the video: title, "18:45 – 20:30 · Horror · 2026 · FSK 16" with the time left at the end, progress, next and later. */
@Composable
private fun ProgrammeBlock(
    now: XtEpgEntry,
    details: EpgDetails?,
    next: XtEpgEntry?,
    later: XtEpgEntry?,
    nowMs: Long,
    titleSize: Float,
    plot: String?,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val locales = LocalConfiguration.current.locales
    val times = remember(locales) { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val length = details?.lengthMin?.let { stringResource(R.string.player_duration_minutes, it) }
    val line = (
        listOf(stringResource(R.string.content_live_time_range_plain, times.format(Date(now.startMs)), times.format(Date(now.stopMs)))) +
            details?.categoryList.orEmpty() +
            listOfNotNull(details?.year?.toString(), details?.rating, length)
        ).joinToString(sep)
    Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 4.dp)) {
        Text(now.title, style = stageText(titleSize, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line, style = stageText(12f, 400), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(timeLeftText(((now.stopMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000), style = stageText(12f, 400), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val span = (now.stopMs - now.startMs).coerceAtLeast(1)
        StageProgress((nowMs - now.startMs).toFloat() / span, Modifier.padding(top = 8.dp))
        plot?.let { Text(it, style = stageText(13f, 500, lineHeight = 1.5f), color = palette.body, modifier = Modifier.padding(top = 10.dp)) }
        if (next != null) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(times.format(Date(next.startMs))) }
                    append(' '); append(next.title)
                    if (later != null) {
                        repeat(3) { append(' ') }
                        withStyle(SpanStyle(color = palette.dim, fontWeight = FontWeight.Bold)) { append(times.format(Date(later.startMs))) }
                        append(' '); append(later.title)
                    }
                },
                style = stageText(12f, 400), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
