package tv.own.owntv.mobile.ui.screens.settings.customize

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.util.Pin
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet

/**
 * The strip that appears while a span is being picked: what to do next, and a way out.
 *
 * On a remote the same job is done by pressing a button on the second row; on a phone the first row
 * is long-pressed and the second is simply tapped, so the instruction has to be on screen.
 */
@Composable
internal fun SpanBar(
    mode: SpanSelector.Mode,
    hasEnd: Boolean,
    selectedCount: Int,
    onMove: (MoveKind) -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tv.own.owntv.mobile.ui.stage.stageAccent.accent.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                mode == SpanSelector.Mode.HIDE ->
                    stringResource(R.string.settings_customize_range_hide_start_touch)
                mode == SpanSelector.Mode.RENAME ->
                    stringResource(R.string.settings_customize_range_rename_start_touch)
                !hasEnd -> stringResource(R.string.settings_customize_range_move_start_touch)
                else -> pluralStringResource(
                    R.plurals.settings_customize_range_selected,
                    selectedCount,
                    selectedCount,
                )
            },
            style = tv.own.owntv.mobile.ui.stage.stageText(13f, 600),
            color = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current.text,
            modifier = Modifier.weight(1f),
        )
        if (mode == SpanSelector.Mode.MOVE && hasEnd) {
            MoveArrow(MobileIcons.KeyboardDoubleArrowUp, R.string.settings_customize_move_top) { onMove(MoveKind.TOP) }
            MoveArrow(MobileIcons.KeyboardArrowUp, R.string.settings_row_menu_move_up) { onMove(MoveKind.UP) }
            MoveArrow(MobileIcons.KeyboardArrowDown, R.string.settings_row_menu_move_down) { onMove(MoveKind.DOWN) }
            MoveArrow(MobileIcons.KeyboardDoubleArrowDown, R.string.settings_customize_move_bottom) { onMove(MoveKind.BOTTOM) }
        }
        tv.own.owntv.mobile.ui.stage.StageButton(onClick = onCancel, text = stringResource(R.string.common_cancel), small = true, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun MoveArrow(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp)) { tv.own.owntv.mobile.ui.screens.settings.MoveKey(icon, labelRes, true, onClick) }
}

/** Hide or show every row in the chosen span — or back out of it. */
@Composable
internal fun SpanHideDialog(
    title: String,
    count: Int,
    isCategories: Boolean,
    onHide: () -> Unit,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A Stage sheet: how many are chosen, then Hide · Show · Cancel.
    tv.own.owntv.mobile.ui.components.MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(
            title,
            subtitle = pluralStringResource(
                if (isCategories) R.plurals.settings_customize_selected_categories else R.plurals.settings_customize_selected_items,
                count,
                count,
            ),
        )
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_hide), onClick = onHide, icon = MobileIcons.VisibilityOff)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_show), onClick = onShow, icon = MobileIcons.Check)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.common_cancel), onClick = onDismiss, icon = MobileIcons.Close)
    }
}

/**
 * The long-press menu a folder or an item shares: rename, hide, the four moves, and the three span
 * starts. [onDelete], [onMoveTo], [onRemoveFromCategory] and [onRename] are left out where they do not apply.
 */
@Composable
internal fun RowActionsSheet(
    title: String,
    onDismiss: () -> Unit,
    hiddenNow: Boolean,
    onToggleHidden: () -> Unit,
    onMove: (MoveKind) -> Unit,
    onSpanHide: () -> Unit,
    onSpanMove: () -> Unit,
    onSpanRename: () -> Unit,
    onRename: (() -> Unit)? = null,
    onMoveTo: (() -> Unit)? = null,
    onRemoveFromCategory: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(title)
        if (onRename != null) {
            tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_rename), onClick = onRename, icon = MobileIcons.Edit)
        }
        tv.own.owntv.mobile.ui.stage.StageMenuItem(
            stringResource(if (hiddenNow) R.string.settings_customize_show else R.string.settings_customize_hide),
            onClick = onToggleHidden,
            icon = if (hiddenNow) MobileIcons.Check else MobileIcons.VisibilityOff,
        )
        if (onMoveTo != null) {
            tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_move_to), onClick = onMoveTo, icon = MobileIcons.Move)
        }
        if (onRemoveFromCategory != null) {
            tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.content_remove_from_category), onClick = onRemoveFromCategory, icon = MobileIcons.Close)
        }
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_move_top), onClick = { onMove(MoveKind.TOP) }, icon = MobileIcons.KeyboardDoubleArrowUp)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_row_menu_move_up), onClick = { onMove(MoveKind.UP) }, icon = MobileIcons.KeyboardArrowUp)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_row_menu_move_down), onClick = { onMove(MoveKind.DOWN) }, icon = MobileIcons.KeyboardArrowDown)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_move_bottom), onClick = { onMove(MoveKind.BOTTOM) }, icon = MobileIcons.KeyboardDoubleArrowDown)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_span_hide), onClick = onSpanHide, icon = MobileIcons.VisibilityOff)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_span_move), onClick = onSpanMove, icon = MobileIcons.SwapVert)
        tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.settings_customize_span_rename), onClick = onSpanRename, icon = MobileIcons.Edit)
        if (onDelete != null) {
            tv.own.owntv.mobile.ui.stage.StageMenuItem(stringResource(R.string.common_delete), onClick = onDelete, icon = MobileIcons.Delete, danger = true)
        }
    }
}

/** Provider order or A–Z — the same per-section setting the browse screens use. */
@Composable
internal fun SortSheet(
    current: SettingsRepository.SortMode,
    onSelect: (SettingsRepository.SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.settings_customize_sort_categories))
        listOf(
            SettingsRepository.SortMode.PLAYLIST to R.string.content_provider,
            SettingsRepository.SortMode.ALPHA to R.string.settings_sort_alpha,
        ).forEach { (mode, labelRes) ->
            tv.own.owntv.mobile.ui.screens.settings.ChoiceRow(stringResource(labelRes), mode == current) { onSelect(mode); onDismiss() }
        }
    }
}

/** Show everything, only what is visible, or only what is hidden. */
@Composable
internal fun FilterSheet(
    current: CustomizeVisibilityFilter,
    onSelect: (CustomizeVisibilityFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.settings_customize_filter_title))
        CustomizeVisibilityFilter.entries.forEach { filter ->
            tv.own.owntv.mobile.ui.screens.settings.ChoiceRow(stringResource(filter.labelRes()), filter == current) { onSelect(filter); onDismiss() }
        }
    }
}

/** What happens to a folder a playlist adds the next time it is refreshed. */
@Composable
internal fun NewCategoryBehaviorSheet(
    hideNew: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.settings_customize_new_category_behavior))
        listOf(
            false to R.string.settings_customize_behavior_show,
            true to R.string.settings_customize_behavior_hide,
        ).forEach { (hide, labelRes) ->
            tv.own.owntv.mobile.ui.screens.settings.ChoiceRow(stringResource(labelRes), hide == hideNew) { onSelect(hide); onDismiss() }
        }
    }
}

/** Setting, changing or removing the lock on this screen. */
internal enum class PinEdit { SET, CHANGE, REMOVE }

/**
 * Set, change or remove the PIN. Setting and changing are one flow — type it, then type it again —
 * because reaching either means the screen is already unlocked.
 */
@Composable
internal fun PinEditFlow(mode: PinEdit, onSetPin: (String?) -> Unit, onDone: () -> Unit) {
    var firstPin by remember { mutableStateOf("") }
    var confirmStage by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    when (mode) {
        PinEdit.REMOVE -> ConfirmDialog(
            title = stringResource(R.string.settings_customize_remove_pin_title),
            message = stringResource(R.string.settings_customize_remove_pin_message),
            confirmLabel = stringResource(R.string.settings_customize_remove),
            onConfirm = { onSetPin(null); onDone() },
            onDismiss = onDone,
        )
        PinEdit.SET, PinEdit.CHANGE -> if (!confirmStage) {
            PinPromptDialog(
                title = stringResource(R.string.settings_customize_new_pin),
                onSubmit = { firstPin = it; confirmStage = true; mismatch = false },
                onDismiss = onDone,
            )
        } else {
            PinPromptDialog(
                title = stringResource(
                    if (mismatch) {
                        R.string.settings_customize_pin_mismatch
                    } else {
                        R.string.settings_customize_confirm_pin
                    },
                ),
                onSubmit = { if (it == firstPin) { onSetPin(it); onDone() } else mismatch = true },
                onDismiss = onDone,
            )
        }
    }
}

/** The screen shown instead of Customize until the right PIN is typed. */
@Composable
internal fun CustomizePinGate(storedPin: String, onUnlock: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }
    PinPromptDialog(
        title = stringResource(
            if (wrong) R.string.settings_customize_wrong_pin else R.string.settings_customize_enter_pin,
        ),
        description = stringResource(R.string.settings_customize_pin_locked),
        onSubmit = { entered ->
            if (entered == storedPin || Pin.verify(entered, storedPin)) onUnlock() else wrong = true
        },
        onDismiss = { },
    )
}

@Composable
private fun PinPromptDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
) {
    // Keyed on the title so a rejected attempt clears the field instead of leaving the wrong PIN in it.
    key(title) {
        TextPromptDialog(
            title = title,
            initial = "",
            description = description,
            allowBlank = false,
            confirmLabel = stringResource(R.string.common_ok),
            onConfirm = onSubmit,
            onDismiss = onDismiss,
        )
    }
}
