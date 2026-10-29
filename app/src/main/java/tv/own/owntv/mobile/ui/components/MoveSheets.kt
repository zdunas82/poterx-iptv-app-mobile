package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageRadio
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageText

/** One row of a list being reordered: what to write back, and what to show. */
data class ReorderItem(val id: Long, val label: String)

/**
 * Reorder the list an item sits in (Stage sheet).
 *
 * The TV app does this by picking the item up with OK and walking it with the D-pad. There is no
 * D-pad here, so each row carries its own two arrows — and the order is written only on Save, so
 * shuffling five titles is one edit rather than five. The item that was held is the highlighted row.
 *
 * Channels, films and shows all reorder the same way, so they all use this: the caller only says how
 * to fetch the list and what to do with the ids that come back.
 */
@Composable
fun ReorderSheet(
    title: String,
    openAt: Long,
    load: suspend () -> List<ReorderItem>,
    onSave: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember { mutableStateListOf<ReorderItem>() }
    var loaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val palette = LocalStagePalette.current

    LaunchedEffect(Unit) {
        items.addAll(load())
        loaded = true
        // Open on the item the user long-pressed; it is rarely near the top of a long folder.
        items.indexOfFirst { it.id == openAt }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = stringResource(R.string.content_move), subtitle = title)
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = sheetListHeight())) {
            items(items, key = { it.id }) { item ->
                val index = items.indexOf(item)
                StageRow(current = item.id == openAt, height = 52.dp, role = null) {
                    Text(item.label, style = stageText(14.5f, 600), color = palette.item, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    StageButton(
                        onClick = { if (index > 0) items.add(index - 1, items.removeAt(index)) },
                        icon = MobileIcons.KeyboardArrowUp,
                        small = true,
                        enabled = index > 0,
                        modifier = Modifier.semanticsLabel(stringResource(R.string.settings_row_menu_move_up)),
                    )
                    StageButton(
                        onClick = { if (index < items.lastIndex) items.add(index + 1, items.removeAt(index)) },
                        icon = MobileIcons.KeyboardArrowDown,
                        small = true,
                        enabled = index < items.lastIndex,
                        modifier = Modifier.semanticsLabel(stringResource(R.string.settings_row_menu_move_down)),
                    )
                }
            }
        }
        StageConfirmItems(
            confirm = stringResource(R.string.common_save),
            confirmEnabled = loaded && items.isNotEmpty(),
            onConfirm = { onSave(items.map { it.id }) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Put the item into one of the user's own combined categories (Stage sheet): New category… first, the
 * categories as choices, then "Keep it in …" as a switch.
 *
 * Unkept, it leaves where it came from — which for a provider folder means the folder stops
 * showing it while All still does, so nothing is ever actually lost.
 */
@Composable
fun MoveToCategorySheet(
    originName: String,
    targets: List<Pair<String, String>>,
    onNewCategory: () -> Unit,
    onMove: (targetId: String, keepInOrigin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    var keepInOrigin by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_move_category_title)

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title, subtitle = stringResource(R.string.settings_move_category_description, originName))
        // Core's text already starts with "+", so no icon (two plus signs otherwise).
        StageMenuItem(stringResource(R.string.settings_move_category_new), onClick = onNewCategory)
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(targets, key = { it.first }) { (id, name) ->
                StageMenuItem(
                    label = name,
                    onClick = { selectedTarget = id },
                    leading = { StageRadio(selectedTarget == id) },
                    role = Role.RadioButton,
                )
            }
        }
        StageSettingRow(
            title = stringResource(R.string.settings_move_category_keep, originName),
            minHeight = 52.dp,
            role = Role.Switch,
            onClick = { keepInOrigin = !keepInOrigin },
        ) { StageSwitch(keepInOrigin) }
        StageConfirmItems(
            confirm = stringResource(R.string.settings_move_category_action),
            confirmEnabled = selectedTarget != null,
            onConfirm = { selectedTarget?.let { onMove(it, keepInOrigin) } },
            onDismiss = onDismiss,
        )
    }
}

/** Name prompt for a brand-new combined category (Stage sheet). */
@Composable
fun NewCategoryDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val title = stringResource(R.string.settings_move_category_new)
    val save = { if (name.isNotBlank()) { onCreate(name.trim()); onDismiss() } }
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        StageSearchField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.settings_move_category_title),
            icon = null,
            imeAction = ImeAction.Done,
            onSearch = save,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        )
        StageConfirmItems(
            confirm = stringResource(R.string.common_save),
            confirmEnabled = name.isNotBlank(),
            onConfirm = { onCreate(name.trim()) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * How every one of these Stage sheets ends (the Live TV and Guide sheets' shape): the confirm as a filled menu item with ✓,
 * then Cancel with ✕. [onConfirm] is followed by [onDismiss].
 */
@Composable
internal fun StageConfirmItems(
    confirm: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.size(1.dp, 6.dp))
    StageMenuItem(confirm, onClick = { onConfirm(); onDismiss() }, icon = MobileIcons.Check, filled = confirmEnabled, enabled = confirmEnabled)
    StageMenuItem(stringResource(R.string.common_cancel), onClick = onDismiss, icon = MobileIcons.Close)
}

/** The spoken name of an icon-only button. */
private fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
