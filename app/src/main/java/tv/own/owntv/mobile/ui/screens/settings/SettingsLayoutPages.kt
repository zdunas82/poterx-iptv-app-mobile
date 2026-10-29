package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.menu.catalogue
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.parseAccentHex
import tv.own.owntv.core.theme.roles
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ColorPickerSheet
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStepper
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.theme.labelRes

/**
 * Appearance › Selection highlight: the ring's thickness as a stepper, then its colour — the accent, each
 * preset, or any colour from the picker. Blank colour means "follow the accent", the default.
 */
@Composable
fun SettingsHighlightPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val isDark = vm.settings.themeMode.pref(ThemeMode.DARK) != ThemeMode.LIGHT
    val current = vm.settings.focusHighlight.pref("")
    val width = vm.settings.focusHighlightWidth.pref(2)
    var picker by remember { mutableStateOf(false) }
    val hexes = AccentColor.entries.map { "#%06X".format(it.roles(isDark).primary and 0xFFFFFF) }
    val accent = stageAccent.accent
    SettingsStagePage(modifier) {
        item(key = "width") {
            SettingItemRow(
                SettingItem(
                    "focus_width", MobileIcons.AutoAwesome, stringResource(R.string.settings_focus_thickness), focusWidthLabel(width),
                    SettingValue.Custom {
                        StageStepper(
                            focusWidthLabel(width),
                            { vm.edit { setFocusHighlightWidth((width - 1).coerceAtLeast(1)) } },
                            { vm.edit { setFocusHighlightWidth((width + 1).coerceAtMost(6)) } },
                            minusEnabled = width > 1, plusEnabled = width < 6,
                        )
                    },
                ),
                {}, null,
            )
        }
        item(key = "shd") { tv.own.owntv.mobile.ui.stage.StageSectionHead(stringResource(R.string.settings_panel_choices)) }
        item(key = "accent") { ColourRow(stringResource(R.string.settings_accent), accent, current.isBlank()) { vm.edit { setFocusHighlight("") } } }
        AccentColor.entries.forEachIndexed { i, color ->
            item(key = color.name) {
                ColourRow(stringResource(color.labelRes), Color(color.roles(isDark).primary), current.equals(hexes[i], true)) { vm.edit { setFocusHighlight(hexes[i]) } }
            }
        }
        item(key = "picker") {
            val custom = current.isNotBlank() && hexes.none { it.equals(current, true) }
            ColourRow(
                stringResource(R.string.settings_color_picker),
                parseAccentHex(current)?.let { Color(it) }.takeIf { custom },
                custom,
            ) { picker = true }
        }
    }
    if (picker) {
        ColorPickerSheet(
            title = stringResource(R.string.settings_selection_highlight),
            eyebrow = stringResource(SettingsGroup.APPEARANCE.titleRes),
            // Gold and white lead: what people ask for when they want the ring to shout.
            presets = listOf("#F5B400", "#FFFFFF") + hexes,
            initial = current,
            onPick = { hex -> vm.edit { setFocusHighlight(hex) } },
            onDismiss = { picker = false },
        )
    }
}

/** A radio row with the colour as a dot. */
@Composable
private fun ColourRow(label: String, color: Color?, on: Boolean, onClick: () -> Unit) {
    StageMenuItem(
        label,
        onClick = onClick,
        selected = on,
        leading = { tv.own.owntv.mobile.ui.stage.StageRadio(on) },
        tags = color?.let { { Box(Modifier.size(14.dp).background(it, CircleShape)) } },
        role = androidx.compose.ui.semantics.Role.RadioButton,
    )
}

/**
 * Layout › Browsing & lists: for Live TV, Movies and Series, whether a section reopens on the category and
 * the item last used.
 */
@Composable
fun SettingsBrowsingPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val s = vm.settings
    val sections = listOf(
        Triple(R.string.common_nav_live_tv, s.rememberCategoryLive.pref(true) to s.rememberLastLive.pref(false),
            (({ on: Boolean -> vm.edit { setRememberCategoryLive(on) } }) to ({ on: Boolean -> vm.edit { setRememberLastLive(on) } }))),
        Triple(R.string.common_nav_movies, s.rememberCategoryMovies.pref(true) to s.rememberLastMovies.pref(false),
            (({ on: Boolean -> vm.edit { setRememberCategoryMovies(on) } }) to ({ on: Boolean -> vm.edit { setRememberLastMovies(on) } }))),
        Triple(R.string.common_nav_series, s.rememberCategorySeries.pref(true) to s.rememberLastSeries.pref(false),
            (({ on: Boolean -> vm.edit { setRememberCategorySeries(on) } }) to ({ on: Boolean -> vm.edit { setRememberLastSeries(on) } }))),
    )
    val note = stringResource(R.string.settings_browsing_description_full)
    val built = sections.map { (titleRes, values, setters) ->
        SettingSection(
            stringResource(titleRes),
            listOf(
                switchItem("browse_cat_$titleRes", MobileIcons.Folder, stringResource(R.string.settings_browsing_last_category), null, values.first, setters.first),
                switchItem("browse_item_$titleRes", MobileIcons.History, stringResource(R.string.settings_browsing_last_item), null, values.second, setters.second),
            ),
        )
    }
    val pins = rememberQuickPins(vm)
    SettingsStagePage(modifier) {
        item(key = "note") { PageNote(note) }
        // These rows are not Quick toggles; a hold does nothing.
        settingSections(built.map { sec -> SettingSection(sec.title, sec.items.map { it.unpinnable() }) }, pins) {}
    }
}

private fun SettingItem.unpinnable() = SettingItem(key, icon, title, line, value, onClick, sheet, pinnable = false)

/** Layout › Long-press menus: Live TV, Movies, Series, Episodes — each opens its actions to rearrange. */
@Composable
fun SettingsMenusPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    var menu by remember { mutableStateOf<ContentMenu?>(null) }
    val note = stringResource(R.string.settings_content_menus_description)
    val rows = ContentMenu.entries.map { m ->
        SettingItem("menu_${m.name}", MobileIcons.MoreHoriz, stringResource(m.titleRes()), null, SettingValue.Opens(null), onClick = { menu = m }, pinnable = false)
    }
    SettingsStagePage(modifier) {
        item(key = "note") { PageNote(note) }
        rows.forEach { row -> item(key = row.key) { SettingItemRow(row, {}, null) } }
    }
    menu?.let { MenuOrderSheet(vm, it) { menu = null } }
}

/** One long-press menu's actions, in the order they will appear, with ↑ ↓ to rearrange them. */
@Composable
private fun MenuOrderSheet(vm: SettingsViewModel, menu: ContentMenu, onDismiss: () -> Unit) {
    val key = menu.name.lowercase()
    val saved = remember(menu) { vm.settings.menuOrder(key) }.pref(emptyList())
    val actions = applyMenuOrder(catalogue(menu), saved) { it.key }
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(stringResource(menu.titleRes()), eyebrow = stringResource(R.string.settings_content_menus_title))
        androidx.compose.foundation.layout.Column(Modifier.sheetScroll()) {
            actions.forEachIndexed { index, action ->
                StageMenuItem(
                    stringResource(action.labelRes),
                    onClick = {},
                    tags = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MoveKey(MobileIcons.KeyboardArrowUp, R.string.settings_row_menu_move_up, index > 0) { vm.edit { setMenuOrder(key, actions.movedKeys(index, -1)) } }
                            MoveKey(MobileIcons.KeyboardArrowDown, R.string.settings_row_menu_move_down, index < actions.lastIndex) { vm.edit { setMenuOrder(key, actions.movedKeys(index, 1)) } }
                        }
                    },
                )
            }
        }
    }
}

/** A 28 dp ink key with an arrow (the stepper's key shape). */
@Composable
internal fun MoveKey(icon: androidx.compose.ui.graphics.vector.ImageVector, @StringRes labelRes: Int, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    val label = stringResource(labelRes)
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(palette.ink.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { tv.own.owntv.mobile.ui.stage.StageIcon(icon, 16.dp, if (enabled) palette.text else palette.dim) }
}

private fun List<tv.own.owntv.core.menu.MenuActionRef>.movedKeys(index: Int, delta: Int): List<String> =
    map { it.key }.toMutableList().apply { add(index + delta, removeAt(index)) }

private fun ContentMenu.titleRes() = when (this) {
    ContentMenu.LIVE -> R.string.common_nav_live_tv
    ContentMenu.MOVIE -> R.string.common_nav_movies
    ContentMenu.SERIES -> R.string.common_nav_series
    ContentMenu.EPISODE -> R.string.content_episodes
}
