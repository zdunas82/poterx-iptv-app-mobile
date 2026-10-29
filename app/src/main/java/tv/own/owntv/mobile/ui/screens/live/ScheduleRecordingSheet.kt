package tv.own.owntv.mobile.ui.screens.live

import tv.own.owntv.mobile.ui.screens.home.formatYear
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.recording.RecordingSchedule
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageLabel
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.drawInnerRing
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * "Schedule recording…" from a channel's menu: the channel on top, the hint, day tools from Today
 * to [DAYS_AHEAD] days out, Start and End as two big typed clocks, "next day" when End is at or before
 * Start, the clash or already-passed note in warn, then Cancel · Record. A clash warns, never blocks; a
 * window already under way records from now.
 */
@Composable
internal fun ScheduleRecordingSheet(
    channel: ChannelEntity,
    clashesFor: suspend (startMs: Long, stopMs: Long) -> List<RecordingEntity>,
    onSchedule: (startMs: Long, stopMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStagePalette.current
    // Frozen for the life of the sheet, as in the catch-up time sheet.
    val nowMs = remember { System.currentTimeMillis() }
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd"), locale)
    }
    // Start at the next five minutes, for an hour.
    val first = remember { Calendar.getInstance().apply { timeInMillis = nowMs; add(Calendar.MINUTE, 5 - get(Calendar.MINUTE) % 5) } }
    var start by remember { mutableStateOf(clockText(first.get(Calendar.HOUR_OF_DAY), first.get(Calendar.MINUTE))) }
    var end by remember { mutableStateOf(clockText((first.get(Calendar.HOUR_OF_DAY) + 1) % 24, first.get(Calendar.MINUTE))) }
    var day by remember { mutableIntStateOf(if (first.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) 0 else 1) }

    fun instantOf(dayOffset: Int, minutes: Int): Long = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
        add(Calendar.MINUTE, minutes)
    }.timeInMillis

    val startMin = parseClock(start)
    val endMin = parseClock(end)
    val startMs = startMin?.let { instantOf(day, it) }
    // An end at or before the start is the next day (core's manualWindow adds it).
    val nextDay = startMin != null && endMin != null && endMin <= startMin
    val stopMs = endMin?.let { instantOf(day, it) }
    val window = if (startMs != null && stopMs != null) RecordingSchedule.manualWindow(startMs, stopMs, nowMs) else null
    val clashes by produceState(emptyList<RecordingEntity>(), startMs, stopMs) {
        value = if (startMs != null && stopMs != null) clashesFor(startMs, stopMs) else emptyList()
    }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val name = remember(channel.name) { ProviderTags.parse(channel.name).name }
    val title = stringResource(R.string.recording_schedule)

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(
            title = title,
            subtitle = listOfNotNull(channel.number?.takeIf { it > 0 }?.let(::formatYear), name).joinToString(sep),
            leading = {
                Box(Modifier.size(44.dp, 32.dp).clip(RoundedCornerShape(StageRadii.Plate)), contentAlignment = Alignment.Center) {
                    ChannelLogoImage(
                        url = channel.displayLogoUrl,
                        modifier = Modifier.fillMaxSize(),
                        plateOn = palette.page,
                        fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, palette.dim) },
                    )
                }
            },
        )
        Text(
            stringResource(R.string.recording_schedule_hint),
            style = stageText(12.5f, 500, lineHeight = 1.5f), color = palette.body,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..DAYS_AHEAD).forEach { offset ->
                StageTool(
                    onClick = { day = offset },
                    label = if (offset == 0) stringResource(R.string.content_epg_today) else dayFormat.format(instantOf(offset, 0)),
                    on = offset == day,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClockField(stringResource(R.string.recording_schedule_start), start, { start = it }, Modifier.weight(1f))
            ClockField(stringResource(R.string.recording_schedule_end), end, { end = it }, Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            if (nextDay) Text(stringResource(R.string.recording_schedule_next_day), style = stageText(11.5f, 600), color = palette.muted)
        }
        val note = when {
            startMin == null || endMin == null -> null
            window == null -> stringResource(R.string.recording_schedule_past)
            clashes.isNotEmpty() -> stringResource(R.string.recording_clash_with, clashes.first().title)
            else -> null
        }
        note?.let {
            val warn = palette.warn
            Row(
                Modifier
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(warn.copy(alpha = 0.10f))
                    .drawBehind { drawInnerRing(warn.copy(alpha = 0.3f), 1.dp.toPx(), 13.dp.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageIcon(MobileIcons.Warning, 14.dp, warn)
                Text(it, style = stageText(12f, 500), color = warn)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageButton(onClick = onDismiss, text = stringResource(R.string.common_cancel), modifier = Modifier.weight(1f))
            StageButton(
                onClick = { if (startMs != null && stopMs != null) onSchedule(startMs, stopMs) },
                text = stringResource(R.string.recording_record),
                icon = MobileIcons.Record,
                style = StageButtonStyle.PRIMARY,
                enabled = window != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A label over a 52 dp clock (24 / 800, tabular), typed as HH:MM; ringed in the focus colour while typing. */
@Composable
private fun ClockField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        StageLabel(label, Modifier.padding(start = 2.dp, end = 2.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValue(typed.filter { it.isDigit() || it == ':' }.take(5)) },
            singleLine = true,
            textStyle = stageText(24f, 800).copy(color = if (parseClock(value) == null) palette.danger else palette.text, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
            cursorBrush = SolidColor(accent.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(StageRadii.Search))
                .background(palette.controlFill)
                .then(if (focused) Modifier.drawBehind { drawInnerRing(accent.focus, 1.5.dp.toPx(), StageRadii.Search.toPx()) } else Modifier)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { field() } },
        )
    }
}

/** "22:50" → minutes after midnight; also "2250" and "9:05". Null when it is not a time. */
internal fun parseClock(text: String): Int? {
    val digits = text.filter { it.isDigit() }
    val (h, m) = when {
        ':' in text -> text.substringBefore(':').toIntOrNull() to text.substringAfter(':').toIntOrNull()
        digits.length in 3..4 -> digits.dropLast(2).toIntOrNull() to digits.takeLast(2).toIntOrNull()
        else -> return null
    }
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun clockText(hour: Int, minute: Int) = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)

/** How far ahead the day chips go: a week. */
private const val DAYS_AHEAD = 6
