package tv.own.owntv.mobile.ui.screens.home

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.mobile.R

/*
 * Home's words and numbers, as the TV's HomeRowText: durations, time left, when a channel was last watched,
 * and numbers in the user's own digits.
 */

/** "42 min", "1 h 05 min". */
@Composable
fun durationText(minutes: Long): String =
    if (minutes >= 60) {
        stringResource(R.string.home_duration_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    } else {
        stringResource(R.string.player_duration_minutes, minutes.toInt())
    }

/** "31 min left", "1 h 05 min left". */
@Composable
fun timeLeftText(minutes: Long): String = stringResource(R.string.home_time_left, durationText(minutes))

/** "Last watched 8 minutes ago" — the TV's wording for a channel on Keep watching. */
@Composable
fun lastWatchedText(lastEngagementAt: Long, nowMs: Long): String {
    val minutes = (nowMs - lastEngagementAt) / 60_000L
    return when {
        minutes < 1 -> stringResource(R.string.home_last_watched_now)
        minutes < 60 -> pluralStringResource(R.plurals.home_last_watched_minutes, minutes.toInt(), minutes.toInt())
        minutes < 60 * 24 -> (minutes / 60).toInt().let { pluralStringResource(R.plurals.home_last_watched_hours, it, it) }
        else -> (minutes / (60 * 24)).toInt().let { pluralStringResource(R.plurals.home_last_watched_days, it, it) }
    }
}

/** A Keep watching card's second line: "S1 · E2 · 31 min left", "1 h 05 min left", or a channel's last watch. */
@Composable
fun keepWatchingLine(item: HeroItem, nowMs: Long): String? {
    val left = if (item.durationMs > 0 && item.positionMs > 0) {
        timeLeftText(((item.durationMs - item.positionMs).coerceAtLeast(0L) + 59_999L) / 60_000L)
    } else null
    return when (item) {
        is HeroItem.MovieHero -> left
        is HeroItem.SeriesHero -> listOfNotNull(
            stringResource(R.string.content_season_episode, item.episode.seasonNumber, item.episode.episodeNumber),
            left,
        ).joinToString(" · ")
        is HeroItem.LiveHero -> lastWatchedText(item.lastEngagementAt, nowMs)
    }
}

/**
 * A count plural with its number grouped ("244,930 titles", "244.930 Titel"). Core's count plurals take `%d`,
 * which Android writes without separators; the number is put into the plural's own text instead.
 */
@Composable
fun groupedCountText(@PluralsRes id: Int, count: Int): String {
    LocalConfiguration.current // a language change re-reads the text
    return LocalContext.current.resources.getQuantityString(id, count).replace(COUNT_PLACEHOLDER, formatCount(count))
}

/** The one argument of a count plural, as core writes it. */
private const val COUNT_PLACEHOLDER = "%1\$d"

/** A count in the locale's digits ("3", "٣"). */
fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** A year in the locale's digits, never grouped ("2026", not "2,026"). */
fun formatYear(year: Int): String = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(year)

/** A rating with one decimal, in the locale's format ("8.4", "8,4"). */
fun formatRating(rating: Double): String =
    NumberFormat.getInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(rating)
