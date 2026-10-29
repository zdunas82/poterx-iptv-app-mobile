package tv.own.owntv.mobile.ui.screens.settings.customize

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.RenameRules
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.screens.settings.SheetText
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/** Which field of a rule row a nested picker is editing. */
private enum class RuleField { TYPE, PLACEMENT, VALUE }

/**
 * Renames a whole batch of folders or items at once: build a rule or two, look at what every name
 * would become, then keep the ones you want.
 *
 * Nothing is written until the review is finished, so a rule that turns out wrong costs a tap on
 * Decline rather than an undo that does not exist.
 */
@Composable
fun BulkRenameFlow(session: BulkRenameSession) {
    when (session.screen.collectAsStateWithLifecycle().value) {
        BulkRenameSession.Screen.CHOICE -> BulkChoiceSheet(session)
        BulkRenameSession.Screen.BUILDER -> BulkRuleBuilderSheet(session)
        BulkRenameSession.Screen.REVIEW -> BulkReviewSheet(session)
        BulkRenameSession.Screen.RESTORE_CONFIRM -> ConfirmDialog(
            title = stringResource(R.string.settings_bulk_rename_restore_title),
            message = stringResource(R.string.settings_bulk_rename_restore_description),
            confirmLabel = stringResource(R.string.settings_bulk_rename_restore),
            onConfirm = { session.confirmRestore() },
            onDismiss = { session.backToChoice() },
        )
        BulkRenameSession.Screen.REFUSED -> MobileBottomSheet(onDismissRequest = { session.dismissRefused() }) {
            StageSheetHeader(stringResource(R.string.settings_bulk_rename_too_many_title))
            SheetText(stringResource(R.string.settings_bulk_rename_too_many_description))
            StageMenuItem(stringResource(R.string.common_ok), onClick = { session.dismissRefused() }, icon = MobileIcons.Check, filled = true)
        }
        BulkRenameSession.Screen.NONE -> Unit
    }
}

/** The three ways in: write rules, run the cleanup presets, or put the original names back. */
@Composable
private fun BulkChoiceSheet(session: BulkRenameSession) {
    val count = session.entries.collectAsStateWithLifecycle().value.size
    MobileBottomSheet(onDismissRequest = { session.close() }) {
        StageSheetHeader(
            title = stringResource(R.string.settings_bulk_rename_title),
            subtitle = pluralStringResource(R.plurals.settings_bulk_rename_selected, count, count),
        )
        StageMenuItem(stringResource(R.string.settings_bulk_rename_add_rule), onClick = { session.openBuilder() }, icon = MobileIcons.Add)
        StageMenuItem(stringResource(R.string.settings_bulk_rename_auto_cleanup), onClick = { session.autoCleanup() }, icon = MobileIcons.Edit)
        StageMenuItem(stringResource(R.string.settings_bulk_rename_restore_original), onClick = { session.requestRestore() }, icon = MobileIcons.History)
    }
}

/** Rows of "add/remove this text before/after the name", plus the two matching options. */
@Composable
private fun BulkRuleBuilderSheet(session: BulkRenameSession) {
    val rules by session.rules.collectAsStateWithLifecycle()
    val options by session.options.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(rules) }
    var trim by remember { mutableStateOf(options.trimLeftovers) }
    var ignoreCase by remember { mutableStateOf(options.ignoreCase) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Pair<Int, RuleField>?>(null) }

    fun submit() {
        val out = mutableListOf<RenameRules.Rule>()
        for (rule in draft) {
            if (rule.pattern != null) {
                out += rule
            } else if (rule.action == RenameRules.Action.ADD) {
                if (RenameRules.tokensOf(rule).size != 1) {
                    errorRes = R.string.settings_bulk_rename_add_single_value_error
                    return
                }
                out += rule
            } else if (RenameRules.tokensOf(rule).isNotEmpty()) {
                out += rule
            }
        }
        if (out.isEmpty()) {
            errorRes = R.string.settings_bulk_rename_rule_required
            return
        }
        errorRes = null
        session.submitRules(out, RenameRules.Options(trimLeftovers = trim, ignoreCase = ignoreCase))
    }

    MobileBottomSheet(onDismissRequest = { session.backToChoice() }) {
        StageSheetHeader(stringResource(R.string.settings_bulk_rename_rules_title))
        SheetText(stringResource(R.string.settings_bulk_rename_rules_description))
        Column(
            Modifier.heightIn(max = sheetListHeight()).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            draft.forEachIndexed { i, rule ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StageTool(
                        onClick = { editing = i to RuleField.TYPE },
                        value = stringResource(
                            if (rule.action == RenameRules.Action.ADD) {
                                R.string.settings_bulk_rename_action_add
                            } else {
                                R.string.settings_bulk_rename_action_remove
                            },
                        ),
                    )
                    StageTool(
                        onClick = { editing = i to RuleField.PLACEMENT },
                        value = stringResource(
                            if (rule.placement == RenameRules.Placement.PREFIX) {
                                R.string.settings_bulk_rename_before
                            } else {
                                R.string.settings_bulk_rename_after
                            },
                        ),
                    )
                    StageTool(
                        onClick = { editing = i to RuleField.VALUE },
                        modifier = Modifier.weight(1f),
                        value = rule.autoLabel?.let { stringResource(it.labelRes()) }
                            ?: rule.value.ifBlank { stringResource(R.string.settings_bulk_rename_value_example) },
                    )
                    StageTool(
                        onClick = { draft = draft.toMutableList().apply { removeAt(i) } },
                        icon = MobileIcons.Close,
                        contentDescription = stringResource(R.string.common_delete),
                    )
                }
            }
            StageMenuItem(
                stringResource(R.string.settings_bulk_rename_add_another_rule),
                onClick = {
                    draft = draft + RenameRules.Rule(
                        RenameRules.Action.ADD,
                        RenameRules.Placement.PREFIX,
                        "",
                    )
                },
                icon = MobileIcons.Add,
            )
            StageSettingRow(
                title = stringResource(R.string.settings_bulk_rename_trim_spaces),
                minHeight = 48.dp,
                role = Role.Switch,
                onClick = { trim = !trim },
            ) { StageSwitch(trim) }
            StageSettingRow(
                title = stringResource(R.string.settings_bulk_rename_ignore_case),
                minHeight = 48.dp,
                role = Role.Switch,
                onClick = { ignoreCase = !ignoreCase },
            ) { StageSwitch(ignoreCase) }
            errorRes?.let {
                Text(
                    stringResource(it),
                    style = stageText(11.5f, 600),
                    color = LocalStagePalette.current.danger,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        // Apply moves on to the review itself, so it is not followed by the sheet's dismiss (that is "back").
        StageMenuItem(
            stringResource(R.string.settings_bulk_rename_apply),
            onClick = { submit() },
            icon = MobileIcons.Check,
            filled = true,
            modifier = Modifier.padding(top = 6.dp),
        )
        StageMenuItem(stringResource(R.string.common_cancel), onClick = { session.backToChoice() }, icon = MobileIcons.Close)
    }

    val target = editing
    when (target?.second) {
        RuleField.TYPE -> OptionsDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_type),
            options = listOf(
                RenameRules.Action.ADD to stringResource(R.string.settings_bulk_rename_action_add),
                RenameRules.Action.REMOVE to stringResource(R.string.settings_bulk_rename_action_remove),
            ),
            selected = draft[target.first].action,
            onSelect = { action ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(action = action, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.PLACEMENT -> OptionsDialog(
            title = stringResource(R.string.settings_bulk_rename_where),
            options = listOf(
                RenameRules.Placement.PREFIX to stringResource(R.string.settings_bulk_rename_before),
                RenameRules.Placement.SUFFIX to stringResource(R.string.settings_bulk_rename_after),
            ),
            selected = draft[target.first].placement,
            onSelect = { placement ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(placement = placement, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.VALUE -> TextPromptDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_value),
            initial = draft[target.first].value,
            hint = stringResource(R.string.settings_bulk_rename_value_hint),
            onConfirm = { value ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(value = value, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

/** Every proposed name, with Apply and Decline on each row and on the batch as a whole. */
@Composable
private fun BulkReviewSheet(session: BulkRenameSession) {
    val rows by session.preview.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    MobileBottomSheet(onDismissRequest = { session.done() }) {
        StageSheetHeader(
            title = stringResource(R.string.settings_bulk_rename_review),
            subtitle = stringResource(R.string.settings_bulk_rename_review_description),
        )
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val changed = rows.count { !it.unchanged }
            val unchanged = rows.count { it.unchanged }
            val duplicates = rows.count { it.duplicate }
            Text(
                pluralStringResource(R.plurals.settings_bulk_rename_will_change, changed, changed),
                style = stageText(12.5f, 700),
                color = palette.text,
            )
            Text(
                pluralStringResource(R.plurals.settings_bulk_rename_unchanged_count, unchanged, unchanged),
                style = stageText(12.5f, 700),
                color = palette.muted,
            )
            Text(
                pluralStringResource(R.plurals.settings_bulk_rename_duplicates_count, duplicates, duplicates),
                style = stageText(12.5f, 700),
                color = palette.danger,
            )
            LazyColumn(
                Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.oldName,
                                style = stageText(13.5f, 600),
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    row.blankRejected -> stringResource(R.string.settings_bulk_rename_blank_rejected)
                                    row.unchanged -> stringResource(R.string.settings_bulk_rename_unchanged)
                                    else -> stringResource(R.string.settings_bulk_rename_result, row.newName)
                                },
                                style = stageText(11.5f, 500),
                                color = when {
                                    row.duplicate -> palette.danger
                                    row.unchanged -> palette.muted
                                    else -> accent
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.duplicate) {
                                Text(
                                    stringResource(R.string.settings_bulk_rename_duplicate),
                                    style = stageText(11.5f, 500),
                                    color = palette.danger,
                                )
                            }
                        }
                        if (!row.unchanged) {
                            StageButton(onClick = { session.applyRows(setOf(row.key)) }, text = stringResource(R.string.settings_bulk_rename_apply), small = true)
                            StageButton(
                                onClick = { session.declineRows(setOf(row.key)) },
                                text = stringResource(R.string.settings_bulk_rename_decline),
                                style = StageButtonStyle.PLAIN,
                                small = true,
                            )
                        }
                    }
                }
            }
            // Three batch actions are wider than a phone held upright: the row scrolls rather than cutting one off.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StageButton(onClick = { session.applyAll() }, text = stringResource(R.string.settings_bulk_rename_apply_all), small = true)
                StageButton(onClick = { session.declineAll() }, text = stringResource(R.string.settings_bulk_rename_decline_all), small = true)
                StageButton(onClick = { session.editRules() }, text = stringResource(R.string.settings_bulk_rename_edit_rules), small = true)
            }
        }
        StageMenuItem(
            stringResource(R.string.common_done),
            onClick = { session.done() },
            icon = MobileIcons.Check,
            filled = true,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun RenameRules.AutoLabel.labelRes() = when (this) {
    RenameRules.AutoLabel.COUNTRY_PROVIDER -> R.string.settings_bulk_rename_auto_country_provider
    RenameRules.AutoLabel.QUALITY_CODEC -> R.string.settings_bulk_rename_auto_quality_codec
    RenameRules.AutoLabel.EMOJI_SYMBOLS -> R.string.settings_bulk_rename_auto_emoji_symbols
}
