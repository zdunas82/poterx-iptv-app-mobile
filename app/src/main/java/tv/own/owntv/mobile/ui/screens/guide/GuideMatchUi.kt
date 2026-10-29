package tv.own.owntv.mobile.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.live.epgShiftLabel
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import kotlin.math.roundToInt
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.screens.live.EpgMatchSheet
import tv.own.owntv.mobile.ui.screens.live.EpgOffsetDialog

/** Which follow-up a guide sheet handed off to. */
internal enum class GuideChannelDialog { MATCH_EPG, EPG_OFFSET }

/**
 * What auto-matching left uncertain: "✦ Auto-match EPG · 41 channels matched", "Review 3 matches",
 * the line under it, then one row per channel — logo, name, "→ guide channel · 82 %" (the score in the
 * accent), Accept and ✕ — and Skip all · Accept all · Done.
 *
 * The percentage is the matcher's own confidence, and it is worth showing: "BBC One HD → BBC One, 88 %"
 * is obviously right, while "Sky Sports 1 → Sky Sports 10, 84 %" is obviously not.
 */
@Composable
fun EpgReviewSheet(
    suggestions: List<EpgMatchSuggestion>,
    matchedCount: Int?,
    includeLogos: Boolean,
    onIncludeLogos: (Boolean) -> Unit,
    onAccept: (EpgMatchSuggestion) -> Unit,
    onSkip: (EpgMatchSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val title = pluralStringResource(R.plurals.content_epg_review_count, suggestions.size, suggestions.size)
    val sep = stringResource(R.string.content_epg_bits_separator)
    MobileBottomSheet(onDismissRequest = onDone, title = title) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                StageIcon(MobileIcons.AutoAwesome, 13.dp, accent)
                Text(
                    listOfNotNull(
                        stringResource(R.string.content_epg_match_button),
                        matchedCount?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.content_epg_channels_matched, it, it) },
                    ).joinToString(sep),
                    style = stageText(11f, 700),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(title, style = stageText(18f, 800), color = palette.text, modifier = Modifier.padding(top = 3.dp))
            Text(stringResource(R.string.content_epg_review_description), style = stageText(12f, 500), color = palette.muted, modifier = Modifier.padding(top = 1.dp))
        }
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(suggestions, key = { it.channel.id }) { s ->
                StageRow(minHeight = 62.dp, role = null) {
                    Box(Modifier.size(44.dp, 32.dp).clip(RoundedCornerShape(StageRadii.Plate)), contentAlignment = Alignment.Center) {
                        ChannelLogoImage(
                            url = s.channel.displayLogoUrl,
                            modifier = Modifier.fillMaxSize(),
                            plateOn = palette.page,
                            fallback = { StageIcon(MobileIcons.LiveTv, 16.dp, palette.dim) },
                        )
                    }
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(s.channel.name, style = stageText(14.5f, 700), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val percent = (s.score * 100).roundToInt()
                        val line = stringResource(R.string.content_epg_channel_score, s.epgName ?: s.epgChannelId, percent)
                        Text(
                            // The score (the line's last number) in the accent.
                            buildAnnotatedString {
                                append(line)
                                val at = line.lastIndexOf(percent.toString())
                                if (at >= 0) addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), at, line.length)
                            },
                            style = stageText(11.5f, 500),
                            color = palette.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StageButton(onClick = { onAccept(s) }, text = stringResource(R.string.content_epg_accept), icon = MobileIcons.Check, small = true)
                    StageIconAction(MobileIcons.Close, stringResource(R.string.content_epg_skip)) { onSkip(s) }
                }
            }
        }
        // Applies to every row, accepted one by one or all at once.
        StageSettingRow(
            title = stringResource(R.string.content_epg_include_logos),
            minHeight = 48.dp,
            role = Role.Switch,
            onClick = { onIncludeLogos(!includeLogos) },
        ) { StageSwitch(includeLogos) }
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Skip all" and "Done" are the same act — nothing is written for a skipped row.
            StageButton(onClick = onDone, text = stringResource(R.string.content_epg_skip_all), modifier = Modifier.weight(1f))
            StageButton(onClick = onAcceptAll, text = stringResource(R.string.content_epg_accept_all), modifier = Modifier.weight(1f))
            StageButton(onClick = onDone, text = stringResource(R.string.common_done), style = StageButtonStyle.PRIMARY, modifier = Modifier.weight(1f))
        }
    }
}

/** The ✕ of a review row: a bare 34 dp icon button. */
@Composable
private fun StageIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { StageIcon(icon, 16.dp, LocalStagePalette.current.text) }
}

/** The guide's hold on a channel: its logo and name, then GUIDE DATA — Auto-match EPG, Pick EPG manually, EPG time offset. */
@Composable
fun GuideChannelSheet(
    channel: ChannelEntity,
    vm: GuideViewModel,
    onDismiss: () -> Unit,
) {
    val palette = LocalStagePalette.current
    var dialog by remember { mutableStateOf<GuideChannelDialog?>(null) }
    val name = remember(channel.name) { ProviderTags.parse(channel.name).name }
    if (dialog == null) {
        MobileBottomSheet(onDismissRequest = onDismiss, title = name) {
            StageSheetHeader(
                title = name,
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
            StageGroupLabel(stringResource(R.string.content_menu_group_guide_data).uppercase(Locale.current.platformLocale))
            StageMenuItem(stringResource(R.string.content_epg_match_button), onClick = { vm.autoMatchOne(channel); onDismiss() }, icon = MobileIcons.AutoAwesome)
            StageMenuItem(stringResource(R.string.content_epg_pick_manually), onClick = { dialog = GuideChannelDialog.MATCH_EPG }, icon = MobileIcons.Guide)
            StageMenuItem(
                stringResource(R.string.content_epg_time_offset),
                onClick = { dialog = GuideChannelDialog.EPG_OFFSET },
                icon = MobileIcons.Schedule,
                value = vm.currentEpgShift(channel)?.let { epgShiftLabel(it) } ?: stringResource(R.string.content_epg_offset_global_short),
            )
        }
    }
    dialog?.let { GuideChannelFollowUp(it, channel, vm, onDismiss) }
}

/** Pick EPG manually / EPG time offset for [channel], opened from a guide sheet; [onDismiss] ends both. */
@Composable
internal fun GuideChannelFollowUp(dialog: GuideChannelDialog, channel: ChannelEntity, vm: GuideViewModel, onDismiss: () -> Unit) {
    when (dialog) {
        GuideChannelDialog.MATCH_EPG -> EpgMatchSheet(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            search = { vm.availableEpgChannels(channel.name, it) },
            onPick = { vm.setEpgMatch(channel, it) },
            onDismiss = onDismiss,
        )
        GuideChannelDialog.EPG_OFFSET -> EpgOffsetDialog(
            channelName = channel.name,
            currentMinutes = vm.currentEpgShift(channel),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(channel, it) },
            onDismiss = onDismiss,
        )
    }
}

/** The one line an auto-match run leaves behind, whatever happened. */
@Composable
fun epgMatchSummaryText(summary: EpgMatchSummary): String = when (summary) {
    EpgMatchSummary.MatchedNoProgrammes -> stringResource(R.string.content_epg_matched_no_programmes)
    EpgMatchSummary.AddPlaylist -> stringResource(R.string.content_epg_add_playlist_first)
    EpgMatchSummary.NoData -> stringResource(R.string.content_epg_no_match_data)
    EpgMatchSummary.AllMatched -> stringResource(R.string.content_epg_all_matched)
    is EpgMatchSummary.NoMatch -> stringResource(R.string.content_epg_no_match, summary.channelName)
    is EpgMatchSummary.AutoMatched -> if (summary.review == 0) {
        pluralStringResource(
            R.plurals.content_epg_auto_matched_no_review,
            summary.applied,
            summary.applied,
        )
    } else {
        stringResource(
            R.string.content_epg_auto_matched,
            pluralStringResource(R.plurals.content_epg_auto_matched_applied, summary.applied, summary.applied),
            pluralStringResource(R.plurals.content_epg_auto_matched_review, summary.review, summary.review),
        )
    }
}

