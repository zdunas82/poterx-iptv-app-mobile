package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.stageText

/** Sports whose score is a line (sets, innings), not one number a side. */
private val LINE_SCORE_SPORTS = setOf(Sport.TENNIS, Sport.CRICKET, Sport.GOLF, Sport.RACING)

/**
 * `.pmc`: a match card — league line and LIVE · minute or kick-off on top, both sides with crest
 * and score, then the venue and a Reminder tag or the followed star. No channels: a tap opens the
 * match page, where they are found; a long press follows or reminds.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MatchCard(
    m: Match,
    featured: Boolean,
    config: SportsConfig,
    reminded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    val haptics = LocalHapticFeedback.current
    val locale = LocalConfiguration.current.locales[0]
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .width(if (featured) 300.dp else 220.dp)
            .height(150.dp)
            .clip(shape)
            .background(palette.ink.copy(alpha = 0.06f))
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val sep = stringResource(R.string.content_epg_bits_separator)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val day = if (m.isLive) null else dayOf(m.startMs, locale)
            Text(
                listOfNotNull(leagueName(m), m.subtitle?.takeIf { featured }, day).joinToString(sep),
                style = stageText(11.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (m.isLive) LiveMark(stringResource(R.string.player_live) + sep + clockText(m))
            else Text(
                if (m.off || m.state == MatchState.POST) m.statusText else timeText(m.startMs),
                style = stageText(12f, 800), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (featured && m.sport !in LINE_SCORE_SPORTS) FeaturedScore(m, config.spoilerFree) else Sides(m, config.spoilerFree)
        Spacer(Modifier.weight(1f))
        val followed = m.competitors.any { c -> config.teams.any { it.sport == m.sport && it.id == c.team.id } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.venue.orEmpty(), style = stageText(11f, 500), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (reminded) StageTag(stringResource(R.string.sports_reminder_set))
            else if (followed) StageIcon(MobileIcons.Star, 14.dp, TierPick)
        }
    }
}

/** Two sides, each a crest, the name and the score; the losing side muted. Racing and cards: the title. */
@Composable
private fun Sides(m: Match, spoiler: Boolean) {
    val palette = LocalStagePalette.current
    if (m.competitors.size < 2) {
        Spacer(Modifier.height(8.dp))
        Text(m.title, style = stageText(14f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.subtitle?.let { Text(it, style = stageText(12.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        return
    }
    listOfNotNull(m.home, m.away).forEach { c ->
        Spacer(Modifier.height(7.dp))
        val lost = c.winner == false
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(c.team, 26)
            Text(teamLabel(c.team), style = stageText(14f, 700), color = if (lost) palette.muted else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (m.state != MatchState.PRE && !m.off) {
                val s = if (spoiler) "–" else scoreText(m.sport, c.score)
                Text(s, style = stageText(if (s.length > 2) 14f else 17f, 800), color = if (lost) palette.muted else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The featured card's middle: crest and name each side, the big score in between. */
@Composable
private fun FeaturedScore(m: Match, spoiler: Boolean) {
    val palette = LocalStagePalette.current
    val home = m.home
    val away = m.away
    if (home == null || away == null) { Sides(m, spoiler); return }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(home.team, 38)
            Text(teamLabel(home.team), style = stageText(12.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val score = when {
            m.state == MatchState.PRE -> timeText(m.startMs)
            spoiler -> "– : –"
            else -> "${scoreText(m.sport, home.score)} – ${scoreText(m.sport, away.score)}"
        }
        Text(score, style = stageText(28f, 800), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(away.team, 38)
            Text(teamLabel(away.team), style = stageText(12.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Long press on a match: follow either side (or stop following), and "Remind me" for a match still to come. */
@Composable
internal fun FollowRemindSheet(
    match: Match,
    config: SportsConfig,
    channels: List<WatchOption>,
    onFollow: (FollowedTeam) -> Unit,
    onRemind: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(MatchNotifications.pair(context, match), subtitle = leagueName(match))
        match.competitors.forEach { c ->
            val team = FollowedTeam(match.sport, c.team.id, c.team.name, c.team.abbreviation)
            val following = config.teams.any { it.key == team.key }
            StageMenuItem(
                label = c.team.name,
                icon = MobileIcons.Star,
                value = stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                selected = following,
                onClick = { onFollow(team); onDismiss() },
            )
        }
        // Without a found channel the phone still reminds: a notification at the lead time.
        if (match.state == MatchState.PRE && match.startMs > System.currentTimeMillis()) {
            StageMenuItem(
                label = stringResource(R.string.content_remind_me),
                icon = MobileIcons.Bell,
                value = channels.firstOrNull()?.facets?.name,
                onClick = { onRemind(); onDismiss() },
            )
        }
        Box(Modifier.size(1.dp))
    }
}
