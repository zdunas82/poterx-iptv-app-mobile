package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.cast.CastPlaybackEngine
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.rememberClockTick
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageLiveBadge
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageGlass
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaybackEngine
import kotlin.math.roundToInt

private const val DISMISS_DRAG_PX = 120f

/**
 * What is playing, kept in reach while the user goes looking for something else.
 *
 * It is the same engine and the same stream as the full screen player — the picture simply moves into
 * a smaller box — so opening it costs nothing and closing it is the only thing in the app that
 * actually stops playback. A radio channel has no picture to move, so its artwork stands in.
 *
 * Drawn as Stage chrome above the bar: a 58 dp glass card, the picture as a 72 × 42 still (LIVE
 * on a channel), the title, the programme or the film's line with what is left, its progress, then
 * pause and close. A channel's line and progress follow its programme ([programmeStartMs] to
 * [programmeStopMs]); a film's follow the engine.
 *
 * It is told what to say rather than which tuner to ask, because a channel and a film are two
 * different objects and this bar is the same bar for both.
 */
@Composable
fun MiniPlayer(
    /** Handed to [VideoStage], which resolves the live engine itself — see its own comment. */
    player: OwnTVPlayer,
    /**
     * The engine actually holding the stream. Everything this bar READS and DRIVES comes from here:
     * live plays on ExoPlayer, where `player` is a stopped mpv, so the bar used to show the play
     * glyph over a moving picture and its play/pause button did nothing at all.
     */
    engine: PlaybackEngine,
    title: String,
    onExpand: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artworkUrl: String? = null,
    /** The receiver, when the stream is on a television: the bar then reports and drives that one. */
    remote: CastPlaybackEngine? = null,
    isLive: Boolean = false,
    programmeStartMs: Long? = null,
    programmeStopMs: Long? = null,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val playing by (remote?.isPlaying ?: engine.isPlaying).collectAsStateWithLifecycle()
    // The engine's position ticks many times a minute; the bar reads it only through the derived
    // minutes and line below, so it recomposes when what it shows changes, not on every tick.
    val position = (remote?.position ?: engine.position).collectAsStateWithLifecycle()
    val duration by (remote?.duration ?: engine.duration).collectAsStateWithLifecycle()
    // Two ways to have no picture — the user turned it off, or the stream never had one — and the bar
    // has the same black rectangle to fill either way. Casting is a third: the picture exists, it is
    // simply on the television, so this bar has nothing to draw either.
    val noVideoTrack by engine.audioOnlyMedia.collectAsStateWithLifecycle()
    val chosenSoundOnly by engine.audioOnly.collectAsStateWithLifecycle()
    val audioOnly = remote != null || noVideoTrack || chosenSoundOnly

    // A channel's programme moves with the clock, not with the engine: half a minute is fine enough
    // for "1 h 23 min left" and a 3 dp line.
    val now by rememberClockTick()
    val programme = if (programmeStartMs != null && programmeStopMs != null && programmeStopMs > programmeStartMs) {
        programmeStartMs to programmeStopMs
    } else {
        null
    }
    val leftMinutes by remember(programme, duration, now) {
        derivedStateOf {
            when {
                programme != null -> ((programme.second - now).coerceAtLeast(0L) + 59_999L) / 60_000L
                duration > 0 -> ((duration - position.value).coerceAtLeast(0L) + 59_999L) / 60_000L
                else -> null
            }
        }
    }
    val left = leftMinutes?.let {
        stringResource(
            R.string.home_time_left,
            if (it >= 60) {
                stringResource(R.string.home_duration_hours_minutes, (it / 60).toInt(), (it % 60).toInt())
            } else {
                stringResource(R.string.player_duration_minutes, it.toInt())
            },
        )
    }
    val line = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, left).joinToString(" · ")
    // In 0.5% steps: finer than a 3 dp line can show.
    val fraction by remember(programme, duration, now) {
        derivedStateOf {
            when {
                programme != null -> ((now - programme.first).toFloat() / (programme.second - programme.first)).coerceIn(0f, 1f)
                duration > 0 -> (position.value.toFloat() / duration).coerceIn(0f, 1f)
                else -> null
            }?.let { (it * 200f).roundToInt() / 200f }
        }
    }
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .stageGlass(shape)
            .clip(shape)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var travel = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (travel > DISMISS_DRAG_PX) onStop()
                        travel = 0f
                    },
                    onVerticalDrag = { _, delta -> travel += delta },
                )
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(72.dp, 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (audioOnly) {
                // Artwork underneath, bars over it: plenty of channels have no logo at all, and a
                // bar that shows an empty black square looks stopped rather than sound-only.
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Waveform(active = playing, modifier = Modifier.padding(6.dp))
            } else {
                VideoStage(player = player, modifier = Modifier.fillMaxSize())
            }
            if (isLive) {
                StageLiveBadge(
                    stringResource(R.string.player_live),
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .graphicsLayer {
                            scaleX = 0.8f
                            scaleY = 0.8f
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotEmpty()) {
                Text(line, style = stageText(11.5f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            fraction?.let { StageProgress(it, Modifier.padding(top = 5.dp).height(3.dp)) }
        }
        val playPause = stringResource(R.string.settings_remote_action_play_pause)
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.accent)
                .clickable(role = Role.Button) {
                    if (remote != null) remote.togglePlayPause() else engine.togglePlayPause()
                }
                .semantics { contentDescription = playPause },
            contentAlignment = Alignment.Center,
        ) { StageIcon(if (playing) MobileIcons.Pause else MobileIcons.PlayArrow, 16.dp, accent.onAccent) }
        val close = stringResource(R.string.content_close)
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(role = Role.Button, onClick = onStop)
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) { StageIcon(MobileIcons.Close, 16.dp, palette.text) }
    }
}
