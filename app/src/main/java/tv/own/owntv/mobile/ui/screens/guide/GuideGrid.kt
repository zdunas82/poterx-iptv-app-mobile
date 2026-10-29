package tv.own.owntv.mobile.ui.screens.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.home.formatYear
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageShadow
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import java.util.Date

/** What a cell shows beside its title: the bell of a reminder, the red dot of a recording. */
internal data class GuideMarks(
    /** (channel id, programme start) pairs with a reminder set. */
    val reminders: Set<Pair<Long, Long>> = emptySet(),
    /** … and with a recording running or scheduled. */
    val recordings: Set<Pair<Long, Long>> = emptySet(),
)

/**
 * The grid: a 70 dp channel column (logo + number), then the programmes as rounded cells
 * with their times — the one on now a shade lighter, past ones at half strength, the picked one filled
 * with the accent; a bell for a reminder, a red dot for a recording, ↺ on a catch-up cell that has
 * started, and a dim "No program at this time." where the feed leaves a gap. The ruler and the accent
 * now-line with its time chip sit over it. Two hours fill the width at 100 % (Size scales it).
 *
 * Every row shares [timeScroll], so dragging any of them moves the whole grid; each row's programmes
 * are read as the row comes into view.
 */
@Composable
internal fun GuideGrid(
    vm: GuideViewModel,
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    timeScroll: ScrollState,
    window: GuideWindow,
    densityPct: Int,
    nowMs: Long,
    marks: GuideMarks,
    /** Bumped by Now; the grid then centres the now-line (it opens centred too). */
    nowRequests: Int,
    picked: Pair<Long, Long>?,
    onTap: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onHold: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onChannel: (ChannelEntity) -> Unit,
    onChannelHold: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val times = rememberGuideTimeFormat()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gridHeight = maxHeight
        val area = maxWidth - CHANNEL_COLUMN - GRID_END
        val minuteWidth = area / SPAN_MINUTES * (densityPct / 100f)
        val totalWidth = minuteWidth * ((window.end - window.start) / MINUTE_MS).toFloat()
        val minutePx = with(LocalDensity.current) { minuteWidth.toPx() }
        fun x(ms: Long): Dp = minuteWidth * ((ms - window.start).toFloat() / MINUTE_MS)
        val nowIn = nowMs in window.start until window.end
        // The now-line in the middle of the cells, so what is on now keeps its title in
        // view — measured from this grid's own width, which a rail or a turned phone makes narrower.
        val areaPx = with(LocalDensity.current) { area.toPx() }
        LaunchedEffect(nowRequests, window.start, minutePx, areaPx) {
            val now = System.currentTimeMillis()
            if (now !in window.start until window.end) return@LaunchedEffect
            val to = ((now - window.start).toFloat() / MINUTE_MS * minutePx - areaPx / 2f).toInt().coerceAtLeast(0)
            timeScroll.animateScrollTo(to)
        }

        Column(Modifier.fillMaxSize()) {
            // The ruler: a time every half hour (10.5 / 700 dim), the accent chip on now.
            Box(
                Modifier
                    .padding(start = CHANNEL_COLUMN, end = GRID_END)
                    .fillMaxWidth()
                    .height(RULER)
                    .horizontalScroll(timeScroll),
            ) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    val ticks = remember(window.start, window.end) {
                        generateSequence(window.start) { it + TICK_MINUTES * MINUTE_MS }.takeWhile { it < window.end }.toList()
                    }
                    ticks.forEach { t ->
                        Text(
                            times.format(Date(t)),
                            style = stageText(10.5f, 700).copy(fontFeatureSettings = "tnum"),
                            color = palette.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = x(t)).padding(top = 2.dp),
                        )
                    }
                    if (nowIn) {
                        Text(
                            times.format(Date(nowMs)),
                            style = stageText(10.5f, 700).copy(fontFeatureSettings = "tnum"),
                            color = accent.onAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(x = x(nowMs) - 16.dp)
                                .background(accent.accent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = { "row" }) { index ->
                        val channel = channels[index] ?: return@items
                        GuideGridRow(
                            vm = vm,
                            channel = channel,
                            window = window,
                            revision = revision,
                            timeScroll = timeScroll,
                            totalWidth = totalWidth,
                            minuteWidth = minuteWidth,
                            nowMs = nowMs,
                            marks = marks,
                            picked = picked,
                            onTap = onTap,
                            onHold = onHold,
                            onChannel = { onChannel(channel) },
                            onChannelHold = { onChannelHold(channel) },
                        )
                    }
                }
                // The 2 dp accent now-line with its glow, from the ruler through the rows on screen.
                if (nowIn) {
                    val rowsHeight = (ROW_HEIGHT + ROW_GAP) * channels.itemCount
                    Box(
                        Modifier
                            .padding(start = CHANNEL_COLUMN, end = GRID_END)
                            .fillMaxWidth()
                            .height(minOf(gridHeight, rowsHeight))
                            .clipToBounds(),
                    ) {
                        val nowPx = (nowMs - window.start).toFloat() / MINUTE_MS * minutePx
                        Box(
                            Modifier
                                .offset { IntOffset((nowPx - timeScroll.value).toInt(), 0) }
                                .width(2.dp)
                                .fillMaxHeight()
                                .stageShadow(accent.accent, 10.dp)
                                .background(accent.accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideGridRow(
    vm: GuideViewModel,
    channel: ChannelEntity,
    window: GuideWindow,
    revision: Int,
    timeScroll: ScrollState,
    totalWidth: Dp,
    minuteWidth: Dp,
    nowMs: Long,
    marks: GuideMarks,
    picked: Pair<Long, Long>?,
    onTap: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onHold: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onChannel: () -> Unit,
    onChannelHold: () -> Unit,
) {
    val palette = LocalStagePalette.current
    // A new match or a new offset invalidates what was read, so the row is asked for again.
    var programmes by remember(channel.id, window.start, revision) { mutableStateOf(vm.cachedRow(channel.id)) }
    LaunchedEffect(channel.id, window.start, revision) {
        if (programmes == null) programmes = vm.row(channel)
    }
    fun x(ms: Long): Dp = minuteWidth * ((ms - window.start).toFloat() / MINUTE_MS)

    Row(Modifier.fillMaxWidth().padding(bottom = ROW_GAP).height(ROW_HEIGHT)) {
        Column(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .combinedClickable(onClick = onChannel, onLongClick = onChannelHold)
                .padding(start = 8.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(StageRadii.Plate)), contentAlignment = Alignment.Center) {
                ChannelLogoImage(
                    url = channel.displayLogoUrl,
                    modifier = Modifier.fillMaxSize(),
                    plateOn = palette.page,
                    fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, palette.dim) },
                )
            }
            channel.number?.takeIf { it > 0 }?.let {
                Text(formatYear(it), style = stageText(9.5f, 700).copy(fontFeatureSettings = "tnum"), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.padding(end = GRID_END).fillMaxSize().horizontalScroll(timeScroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                val rows = programmes ?: return@Box
                // A channel the guide has nothing for is one gap the width of the day.
                if (rows.isEmpty()) {
                    GapCell(start = 0.dp, width = totalWidth, timeScroll = timeScroll)
                    return@Box
                }
                var cursor = window.start
                rows.forEach { p ->
                    val start = p.startMs.coerceAtLeast(window.start)
                    val end = p.stopMs.coerceAtMost(window.end)
                    if (end <= cursor) return@forEach
                    // A stretch the feed leaves empty is its own dim cell, not a hole in the row.
                    if (start > cursor) GapCell(x(cursor), x(start) - x(cursor), timeScroll)
                    val from = maxOf(start, cursor)
                    ProgrammeCell(
                        programme = p,
                        start = x(from),
                        width = x(end) - x(from),
                        timeScroll = timeScroll,
                        nowMs = nowMs,
                        catchup = channel.catchup && p.startMs <= nowMs,
                        reminder = (channel.id to p.startMs) in marks.reminders,
                        recording = (channel.id to p.startMs) in marks.recordings,
                        picked = picked == channel.id to p.startMs,
                        onTap = { onTap(channel, p) },
                        onHold = { onHold(channel, p) },
                    )
                    cursor = end
                }
                if (cursor < window.end && rows.last().stopMs <= window.end) GapCell(x(cursor), x(window.end) - x(cursor), timeScroll)
            }
        }
    }
}

/** `.gcell`: 10 dp corners, 5 × 8 padding; title 12 / 700, the time 10 dim — the text keeps to the visible part. */
@Composable
private fun ProgrammeCell(
    programme: EpgProgrammeEntity,
    start: Dp,
    width: Dp,
    timeScroll: ScrollState,
    nowMs: Long,
    catchup: Boolean,
    reminder: Boolean,
    recording: Boolean,
    picked: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val times = rememberGuideTimeFormat()
    val onNow = programme.startMs <= nowMs && nowMs < programme.stopMs
    val past = programme.stopMs <= nowMs
    val fill = when {
        picked -> accent.accent
        onNow -> palette.ink.copy(alpha = 0.085f)
        else -> palette.ink.copy(alpha = 0.045f)
    }
    val small = if (picked) Color.Black.copy(alpha = 0.6f) else palette.dim
    Box(
        Modifier
            .offset(x = start + CELL_INSET)
            .width((width - CELL_INSET * 2).coerceAtLeast(0.dp))
            .fillMaxHeight()
            .alpha(if (past && !picked) 0.5f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .stickyText(start + CELL_INSET, width - CELL_INSET * 2, timeScroll)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reminder) StageIcon(MobileIcons.Bell, 12.dp, if (picked) accent.onAccent else accent.accent)
                Text(
                    programme.title,
                    style = stageText(12f, 700),
                    color = if (picked) accent.onAccent else palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (recording) Text("●", style = stageText(10f, 700), color = RecordRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (catchup) StageIcon(MobileIcons.Catchup, 11.dp, small)
                Text(
                    stringResource(R.string.content_live_time_range_plain, times.format(Date(programme.startMs)), times.format(Date(programme.stopMs))),
                    style = stageText(10f, 500).copy(fontFeatureSettings = "tnum"),
                    color = small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The dim cell where the feed has nothing: ink 2 % with a 1 dp ink-6 % ring, "No program at this time." */
@Composable
private fun GapCell(start: Dp, width: Dp, timeScroll: ScrollState) {
    if (width <= CELL_INSET * 2) return
    val palette = LocalStagePalette.current
    Box(
        Modifier
            .offset(x = start + CELL_INSET)
            .width(width - CELL_INSET * 2)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.ink.copy(alpha = 0.02f))
            .drawBehind { drawInnerRing(palette.ink.copy(alpha = 0.06f), 1.dp.toPx(), 10.dp.toPx()) }
            .stickyText(start + CELL_INSET, width - CELL_INSET * 2, timeScroll)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            stringResource(R.string.content_epg_no_programme),
            style = stageText(12f, 600),
            color = palette.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A long cell scrolled half out of view keeps its text at the visible left edge rather than taking it out
 * of sight with the cell's start. Read at placement, so scrolling never recomposes the rows.
 */
private fun Modifier.stickyText(cellStart: Dp, cellWidth: Dp, timeScroll: ScrollState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val hidden = timeScroll.value - cellStart.toPx()
            val room = (cellWidth - STICKY_MIN_ROOM).toPx().coerceAtLeast(0f)
            placeable.place(hidden.coerceIn(0f, room).toInt(), 0)
        }
    }

/**
 * Timeline: one channel's day read top to bottom — the channels as tools along the top, the
 * programmes as rows (the one on now in the fx look, ↺ where the archive has it).
 */
@Composable
internal fun GuideTimeline(
    vm: GuideViewModel,
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    nowMs: Long,
    onOpen: (ChannelEntity, EpgProgrammeEntity) -> Unit,
) {
    val palette = LocalStagePalette.current
    val times = rememberGuideTimeFormat()
    var selected by remember { mutableStateOf<ChannelEntity?>(null) }
    val channel = selected ?: channels.itemSnapshotList.items.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                channels[index]?.let { entry ->
                    StageTool(
                        onClick = { selected = entry },
                        value = remember(entry.name) { ProviderTags.parse(entry.name).name },
                        on = entry.id == channel?.id,
                        // A bounded width, so the name has room in a row that scrolls (and long ones end in …).
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }
        }
        if (channel == null) return@Column
        val revision by vm.revision.collectAsStateWithLifecycle()
        var programmes by remember(channel.id, revision) { mutableStateOf(vm.cachedRow(channel.id)) }
        LaunchedEffect(channel.id, revision) { if (programmes == null) programmes = vm.row(channel) }
        val rows = programmes
        if (rows != null && rows.isEmpty()) {
            Text(
                stringResource(R.string.content_epg_no_guide),
                style = stageText(13.5f, 500),
                color = palette.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 6.dp)) {
            items(rows.orEmpty(), key = { it.id }) { p ->
                val onNow = p.startMs <= nowMs && nowMs < p.stopMs
                StageRow(onClick = { onOpen(channel, p) }, onLongClick = { onOpen(channel, p) }, current = onNow, minHeight = 56.dp) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(p.title, style = stageText(14.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.content_live_time_range_plain, times.format(Date(p.startMs)), times.format(Date(p.stopMs))),
                            style = stageText(12f, 400),
                            color = if (onNow) palette.body else palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (channel.catchup && p.startMs <= nowMs) StageIcon(MobileIcons.Catchup, 14.dp, palette.dim)
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L

/** Half an hour is the unit every guide is drawn in, so it is what the ruler counts. */
private const val TICK_MINUTES = 30

/** Two hours across the cell area at Size 100 % (19:00 – 21:00 over 327 dp). */
private const val SPAN_MINUTES = 120f

private val CHANNEL_COLUMN = 74.dp
private val GRID_END = 8.dp
private val RULER = 26.dp
private val ROW_HEIGHT = 50.dp
private val ROW_GAP = 4.dp
private val CELL_INSET = 2.dp
/** The least of a cell its text keeps when it slides along a half-hidden cell. */
private val STICKY_MIN_ROOM = 60.dp
private val RecordRed = Color(0xFFFF5B5B)
