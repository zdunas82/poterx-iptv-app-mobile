package tv.own.owntv.mobile.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.sync.local.AutoSyncAgreement
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageRadio
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * One paired device's auto-sync: the switch, what this device lets travel, whose profiles, how often.
 *
 * Turning the switch on asks the other device (its Local sync page open); this side's terms are written
 * only on its yes, with the choices below as they are then — so they can be set first. Turning it off
 * is local and immediate, and the other device is told. Once on, the choices are a draft until Save.
 */
@Composable
fun LocalAutoSyncSheet(vm: LocalAutoSyncViewModel, device: PairedDevice, onDismiss: () -> Unit) {
    val agreements by vm.agreements.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val enableState by vm.enableState.collectAsStateWithLifecycle()
    val agreement = agreements[device.id]
    val enabled = agreement != null
    var timing by remember(device.id) { mutableStateOf<LocalAutoSyncTiming?>(null) }
    var sections by remember(device.id) { mutableStateOf<Set<BackupManager.Section>?>(null) }
    val asking = enableState is LocalAutoSyncViewModel.EnableState.Asking

    val chosenTiming = timing ?: agreement?.timing ?: LocalAutoSyncTiming.DEFAULT
    val chosenSections = sections ?: agreement?.sections ?: AutoSyncAgreement.DEFAULT_SECTIONS
    val choicesValid = chosenSections.isNotEmpty()

    val dismiss = {
        vm.cancelEnable()
        onDismiss()
    }
    val title = stringResource(R.string.local_auto_sync_title)
    MobileBottomSheet(onDismissRequest = dismiss, title = title) {
        StageSheetHeader(title = device.name, eyebrow = title, subtitle = stringResource(R.string.local_auto_sync_for_device, device.name))
        Column(
            modifier = Modifier.heightIn(max = sheetListHeight()).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            SyncCheckRow(
                label = stringResource(R.string.local_auto_sync_enable),
                description = when {
                    asking -> stringResource(R.string.local_auto_sync_waiting, device.name)
                    enabled -> null
                    else -> stringResource(R.string.local_auto_sync_enable_hint)
                },
                checked = enabled,
                onToggle = { on -> if (on) vm.enable(device, chosenSections, chosenTiming) else vm.disable(device.id) },
                enabled = !asking && (enabled || choicesValid),
            )
            when (val state = enableState) {
                is LocalAutoSyncViewModel.EnableState.Declined -> SyncDangerNote(stringResource(R.string.local_auto_sync_declined, state.deviceName))
                is LocalAutoSyncViewModel.EnableState.Failed -> SyncDangerNote(stringResource(state.reason.messageRes()))
                else -> Unit
            }
            // Which profiles this is between: fixed when it was set up, whatever is on screen later.
            val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
            val hereName = profiles.firstOrNull { it.id == (agreement?.localProfileId ?: activeId) }?.name.orEmpty()
            SyncNote(
                if (agreement != null) stringResource(R.string.local_auto_sync_profiles, hereName, agreement.theirProfileName)
                else stringResource(R.string.local_auto_sync_profile_hint, hereName, device.name),
            )
            agreement?.theirSections?.takeIf { it.isNotEmpty() }?.let { theirs ->
                SyncNote(
                    stringResource(
                        R.string.local_auto_sync_their_sections,
                        device.name,
                        BackupManager.Section.entries.filter { it in theirs }.map { stringResource(it.labelRes()) }
                            .joinToString(stringResource(R.string.content_epg_bits_separator)),
                    ),
                )
            }
            StageGroupLabel(stringResource(R.string.local_auto_sync_what_to_sync))
            BackupManager.Section.entries.forEach { section ->
                SyncCheckRow(
                    label = stringResource(section.labelRes()),
                    description = stringResource(section.descriptionRes()),
                    checked = section in chosenSections,
                    onToggle = { on -> sections = if (on) chosenSections + section else chosenSections - section },
                    enabled = !asking,
                )
            }
            StageGroupLabel(stringResource(R.string.local_auto_sync_when))
            LocalAutoSyncTiming.entries.forEach { option ->
                StageSettingRow(
                    title = stringResource(option.labelRes()),
                    minHeight = 48.dp,
                    onClick = if (!asking) ({ timing = option }) else null,
                    modifier = Modifier.alpha(if (!asking) 1f else 0.45f),
                ) { StageRadio(selected = option == chosenTiming) }
            }
            SyncNote(stringResource(R.string.local_auto_sync_note))
        }
        SyncButtons(
            confirm = stringResource(R.string.common_save),
            confirmEnabled = enabled && !asking && choicesValid,
            onConfirm = {
                vm.save(device.id, chosenSections, chosenTiming)
                onDismiss()
            },
            onDismiss = dismiss,
        )
    }
}

/** The words on the "How often" rows and on the paired device's row. */
internal fun LocalAutoSyncTiming.labelRes(): Int = when (this) {
    LocalAutoSyncTiming.IMMEDIATELY -> R.string.local_auto_sync_timing_immediately
    LocalAutoSyncTiming.HOURS_6 -> R.string.settings_epg_refresh_6h
    LocalAutoSyncTiming.HOURS_24 -> R.string.local_auto_sync_timing_daily
    LocalAutoSyncTiming.DAYS_7 -> R.string.local_auto_sync_timing_weekly
}

/** "3 minutes ago", the same phrasing the paired-device rows use for their last sync. */
internal fun autoSyncWhen(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
