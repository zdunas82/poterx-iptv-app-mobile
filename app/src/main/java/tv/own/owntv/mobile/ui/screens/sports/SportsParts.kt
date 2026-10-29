package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import tv.own.owntv.core.sports.GuideSport
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Score
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.stageText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The live red: the LIVE dot, a running minute. */
internal val SportsLive = Color(0xFFFF4D4D)

/** The tier colours of the reasons: guide green, event blue, your pick gold. */
internal val TierGuide = Color(0xFF9FE07E)
internal val TierEvent = Color(0xFF7FD3FF)
internal val TierPick = Color(0xFFE7C56A)

/** A team's crest (ESPN's 96 px logo) in a circle, or its abbreviation / initials when there is none. */
@Composable
internal fun Crest(team: Team?, size: Int) {
    val palette = LocalStagePalette.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(palette.ink.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val url = team?.logoUrl
        if (url.isNullOrBlank()) {
            Text(
                team?.abbreviation?.ifBlank { null } ?: initials(team?.shortName.orEmpty()),
                style = stageText(size * 0.3f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding((size * 0.1f).dp))
        }
    }
}

/** "J. Sinner" → "JS": an athlete has no crest and no abbreviation. */
private fun initials(name: String): String {
    val words = name.split(' ', '.', '-').filter { it.isNotBlank() }
    return (words.firstOrNull()?.take(1).orEmpty() + words.drop(1).lastOrNull()?.take(1).orEmpty()).uppercase()
}

/** `.pill-live`: the red dot and a caps label ("LIVE", "LIVE · 63'"). */
@Composable
internal fun LiveMark(text: String, textSize: Float = 11f) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(SportsLive))
        if (text.isNotEmpty()) {
            Text(text, style = stageText(textSize, 800, 0.06.em), color = LocalStagePalette.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
@ReadOnlyComposable
internal fun sportLabel(sport: Sport): String = stringResource(
    when (sport) {
        Sport.SOCCER -> R.string.sports_sport_soccer
        Sport.BASKETBALL -> R.string.sports_sport_basketball
        Sport.TENNIS -> R.string.sports_sport_tennis
        Sport.CRICKET -> R.string.sports_sport_cricket
        Sport.RACING -> R.string.sports_sport_motorsport
        Sport.HOCKEY -> R.string.sports_sport_hockey
        Sport.FOOTBALL -> R.string.sports_sport_football
        Sport.MMA -> R.string.sports_sport_mma
        Sport.BASEBALL -> R.string.sports_sport_baseball
        Sport.GOLF -> R.string.sports_sport_golf
        Sport.RUGBY -> R.string.sports_sport_rugby
        Sport.RUGBY_LEAGUE -> R.string.sports_sport_rugby_league
        Sport.AUSSIE_RULES -> R.string.sports_sport_aussie_rules
        Sport.VOLLEYBALL -> R.string.sports_sport_volleyball
        Sport.LACROSSE -> R.string.sports_sport_lacrosse
    },
)

/** The chip emoji per sport. */
internal fun sportEmoji(sport: Sport): String = when (sport) {
    Sport.SOCCER -> "⚽"
    Sport.BASKETBALL -> "🏀"
    Sport.TENNIS -> "🎾"
    Sport.CRICKET -> "🏏"
    Sport.RACING -> "🏎"
    Sport.HOCKEY -> "🏒"
    Sport.FOOTBALL -> "🏈"
    Sport.MMA -> "🥊"
    Sport.BASEBALL -> "⚾"
    Sport.GOLF -> "⛳"
    Sport.RUGBY, Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES -> "🏉"
    Sport.VOLLEYBALL -> "🏐"
    Sport.LACROSSE -> "🥍"
}

@Composable
@ReadOnlyComposable
internal fun guideSportLabel(sport: GuideSport): String = stringResource(
    when (sport) {
        GuideSport.FOOTBALL -> R.string.sports_sport_soccer
        GuideSport.HANDBALL -> R.string.sports_sport_handball
        GuideSport.BASKETBALL -> R.string.sports_sport_basketball
        GuideSport.TENNIS -> R.string.sports_sport_tennis
        GuideSport.ICE_HOCKEY -> R.string.sports_sport_hockey
        GuideSport.MOTORSPORT -> R.string.sports_sport_motorsport
        GuideSport.DARTS -> R.string.sports_sport_darts
        GuideSport.SNOOKER -> R.string.sports_sport_snooker
        GuideSport.CYCLING -> R.string.sports_sport_cycling
        GuideSport.CRICKET -> R.string.sports_sport_cricket
        GuideSport.COMBAT -> R.string.sports_sport_combat
        GuideSport.AMERICAN_FOOTBALL -> R.string.sports_sport_american_football
        GuideSport.BASEBALL -> R.string.sports_sport_baseball
        GuideSport.GOLF -> R.string.sports_sport_golf
        GuideSport.RUGBY -> R.string.sports_sport_rugby
        GuideSport.VOLLEYBALL -> R.string.sports_sport_volleyball
        GuideSport.ATHLETICS -> R.string.sports_sport_athletics
        GuideSport.WINTER -> R.string.sports_sport_winter
        GuideSport.OTHER -> R.string.sports_sport_other
    },
)

/** The competition's brand name from the catalogue ("Bundesliga"), else ESPN's ("German Bundesliga"). */
internal fun leagueName(m: Match): String = SportsCatalog.league(League.keyOf(m.sport, m.leagueSlug))?.name ?: m.leagueName

/** A catalogue league's name as shown; cricket's one feed is the sport's own word. */
@Composable
internal fun leagueLabel(l: League): String = if (l.key == CRICKET_FEED) stringResource(R.string.sports_sport_cricket) else l.name

internal const val CRICKET_FEED = "cricket/all"

/** A side's score as shown: tennis as its set games ("6 3"), everything else as ESPN's text. */
internal fun scoreText(sport: Sport, score: Score?): String = when {
    score == null -> ""
    sport == Sport.TENNIS && score.periods.isNotEmpty() -> score.periods.joinToString(" ") { it.value.toString() }
    else -> score.text
}

/** The minute while live ("63'", "Set 2", "38.2 ov"), else ESPN's status. */
@Composable
internal fun clockText(m: Match): String {
    val clock = m.clock
    return when {
        m.sport == Sport.TENNIS && clock != null && clock.period > 0 -> stringResource(R.string.sports_set, clock.period)
        m.sport == Sport.CRICKET && clock?.overs != null -> stringResource(R.string.sports_overs, clock.overs!!)
        clock != null && clock.display.isNotBlank() -> clock.display
        else -> m.statusText
    }
}

/** "13:30" in the phone's own 12/24-hour setting. */
@Composable
internal fun timeText(ms: Long): String =
    android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(ms))

/** The day a match falls on, when it is not today: "Sat" within a week, "Fri 23" beyond it. Null for today. */
internal fun dayOf(ms: Long, locale: Locale, today: LocalDate = LocalDate.now()): String? {
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    if (date == today) return null
    val pattern = if (date.isBefore(today.plusDays(7))) "EEE" else android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** "Today 13:30" / "Sat 13:30": a kick-off in the user's own words. */
@Composable
internal fun kickOffText(ms: Long): String =
    (dayOf(ms, LocalConfiguration.current.locales[0]) ?: stringResource(R.string.content_epg_today)) + " " + timeText(ms)

internal fun isToday(ms: Long): Boolean = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

/** Postponed or cancelled: the match will not be played as listed. */
internal val Match.off: Boolean get() = state == MatchState.POSTPONED || state == MatchState.CANCELLED

/** A side's name on a card: the short name, unless ESPN gives only a code ("GHA" in cricket), then the full name. */
internal fun teamLabel(t: Team): String =
    if (t.shortName.length <= 4 && t.shortName == t.shortName.uppercase() && t.name.length > t.shortName.length) t.name else t.shortName

/** A language code as the user's language names it ("DE" → "German"). */
@Composable
internal fun languageName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return Locale.forLanguageTag(code.lowercase()).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
internal fun qualityLabel(q: MinQuality): String = stringResource(
    when (q) {
        MinQuality.ANY -> R.string.sports_any
        MinQuality.HD -> R.string.sports_quality_hd
        MinQuality.FHD -> R.string.sports_quality_fhd
        MinQuality.UHD -> R.string.sports_quality_uhd
    },
)

/** A quality rank as its usual tag. */
internal fun qualityTag(rank: Int): String? = when (rank) {
    2 -> "HD"
    3 -> "FHD"
    4 -> "4K"
    5 -> "8K"
    else -> null
}
