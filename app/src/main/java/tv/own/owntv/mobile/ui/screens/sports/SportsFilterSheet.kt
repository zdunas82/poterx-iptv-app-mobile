package tv.own.owntv.mobile.ui.screens.sports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.SheetScroll
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageTool

/**
 * Filter channels (the television's filter panel): every choice with the number of channels it covers,
 * the list behind changing as soon as a choice does. The choices apply to this match only, unless
 * "Remember for every match" is on; Strict filtering decides whether the others are hidden or listed below.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SportsFilterSheet(state: MatchPageState, onFilter: (WatchFilter) -> Unit, onRemember: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val f = state.filter
    val list = state.list
    val sep = stringResource(R.string.content_epg_bits_separator)
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(stringResource(R.string.sports_filter_title), extra = stringResource(R.string.sports_showing_of, list.shown.size, list.total))
        SheetScroll {
            // Language: every language present (and the preferred ones, at 0), Unknown last.
            StageGroupLabel(stringResource(R.string.settings_language))
            FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val all = list.languageCounts.keys
                list.languageCounts.forEach { (code, n) ->
                    val on = f.languages.isEmpty() || code in f.languages
                    val name = if (code == WatchFilter.UNKNOWN) stringResource(R.string.sports_unknown) else languageName(code)
                    StageTool(
                        onClick = { onFilter(f.copy(languages = toggled(f.languages, all, code))) },
                        label = name + sep + n, on = on, icon = if (on) MobileIcons.Check else null,
                    )
                }
            }
            StageGroupLabel(stringResource(R.string.sports_filter_country))
            FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StageTool(onClick = { onFilter(f.copy(countries = emptySet())) }, label = stringResource(R.string.sports_any), on = f.countries.isEmpty())
                list.countryCounts.forEach { (code, n) ->
                    StageTool(
                        onClick = { onFilter(f.copy(countries = if (code in f.countries) f.countries - code else f.countries + code)) },
                        label = code + sep + n, on = code in f.countries,
                    )
                }
            }
            StageGroupLabel(stringResource(R.string.settings_vp_cat_picture))
            FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MinQuality.entries.forEach { q -> StageTool(onClick = { onFilter(f.copy(minQuality = q)) }, label = qualityLabel(q), on = f.minQuality == q) }
            }
            if (state.playlists.size > 1) {
                StageGroupLabel(stringResource(R.string.settings_sort_playlist))
                FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    StageTool(onClick = { onFilter(f.copy(sourceIds = emptySet())) }, label = stringResource(R.string.sports_all_playlists), on = f.sourceIds.isEmpty())
                    state.playlists.forEach { (id, name) ->
                        StageTool(onClick = { onFilter(f.copy(sourceIds = if (id in f.sourceIds) f.sourceIds - id else f.sourceIds + id)) }, label = name, on = id in f.sourceIds)
                    }
                }
            }
            StageSettingRow(
                title = stringResource(R.string.sports_only_confirmed_mobile), subtitle = stringResource(R.string.sports_only_confirmed_description),
                onClick = { onFilter(f.copy(onlyConfirmed = !f.onlyConfirmed)) }, minHeight = 48.dp, modifier = Modifier.padding(top = 8.dp),
            ) { StageSwitch(f.onlyConfirmed) }
            StageSettingRow(
                title = stringResource(R.string.sports_strict_filter), subtitle = stringResource(R.string.sports_strict_filter_description),
                onClick = { onFilter(f.copy(strict = !f.strict)) }, minHeight = 48.dp,
            ) { StageSwitch(f.strict) }
            StageSettingRow(
                title = stringResource(R.string.sports_remember_filters_mobile), subtitle = stringResource(R.string.sports_remember_filters_description),
                onClick = { onRemember(!state.config.rememberFilters) }, minHeight = 48.dp,
            ) { StageSwitch(state.config.rememberFilters) }
            StageButton(
                onClick = onDismiss,
                text = pluralStringResource(R.plurals.sports_show_channels, list.shown.size, list.shown.size),
                style = StageButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}

/**
 * A language toggled in a set where empty means "every language": turning one off from "all" keeps the
 * others; turning the last missing one back on returns to "all".
 */
private fun toggled(current: Set<String>, all: Set<String>, code: String): Set<String> {
    val expanded = current.ifEmpty { all }
    val next = if (code in expanded) expanded - code else expanded + code
    return if (next.containsAll(all)) emptySet() else next
}
