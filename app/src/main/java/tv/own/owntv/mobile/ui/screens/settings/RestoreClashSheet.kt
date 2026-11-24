package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.backup.BackupManager.ProfileRestoreChoice
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.profiles.ProtectedProfilePinSheet
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * A restore's profiles that have the name of one already here, asked about one at a time: Merge into it,
 * Replace it, or Keep both (the file's comes in as "<name> (restored)"). Merging or replacing a protected
 * profile other than the one in use asks one of its PINs first; dismissing the PIN returns to the choice.
 * Dismissing a choice cancels the restore.
 */
@Composable
internal fun RestoreClashSheet(
    ask: BackupViewModel.ClashAsk,
    profiles: List<ProfileEntity>,
    activeId: Long,
    verify: suspend (ProfileEntity, String) -> PinAttempt,
    onDone: (Map<Long, ProfileRestoreChoice>) -> Unit,
    onCancel: () -> Unit,
) {
    var index by remember(ask) { mutableIntStateOf(0) }
    var choices by remember(ask) { mutableStateOf(emptyMap<Long, ProfileRestoreChoice>()) }
    var pinFor by remember(ask) { mutableStateOf<ProfileRestoreChoice?>(null) }
    val clash = ask.clashes.getOrNull(index)
    if (clash == null) {
        LaunchedEffect(ask) { onDone(choices) }
        return
    }
    val here = profiles.firstOrNull { it.id == clash.deviceProfileId }
    val needsPin = here != null && clash.deviceProfileId != activeId && clash.deviceProfileId in ask.protectedIds
    val keepName = stringResource(R.string.settings_backup_profile_restored_name, clash.name)
    val take = { choice: ProfileRestoreChoice ->
        choices = choices + (clash.fileProfileId to choice)
        pinFor = null
        index++
    }
    val pick = { choice: ProfileRestoreChoice -> if (needsPin && choice !is ProfileRestoreChoice.KeepBoth) pinFor = choice else take(choice) }
    key(clash.fileProfileId) {
        val pending = pinFor
        if (pending != null && here != null) {
            ProtectedProfilePinSheet(
                profileName = here.name,
                body = stringResource(R.string.profiles_protected_edit),
                verify = { pin -> verify(here, pin) },
                onUnlocked = { take(pending) },
                onDismiss = { pinFor = null },
            )
        } else {
            val title = stringResource(R.string.settings_backup_profile_clash_title, clash.name)
            MobileBottomSheet(onDismissRequest = onCancel, title = title) {
                StageSheetHeader(title = title, subtitle = stringResource(R.string.settings_backup_profile_clash_body))
                StageSettingRow(
                    title = stringResource(R.string.settings_backup_profile_merge),
                    subtitle = stringResource(R.string.settings_backup_profile_merge_description),
                    onClick = { pick(ProfileRestoreChoice.Merge) },
                )
                StageSettingRow(
                    title = stringResource(R.string.settings_backup_profile_replace),
                    subtitle = stringResource(R.string.settings_backup_profile_replace_description),
                    onClick = { pick(ProfileRestoreChoice.Replace) },
                )
                StageSettingRow(
                    title = stringResource(R.string.settings_backup_profile_keep_both),
                    subtitle = stringResource(R.string.settings_backup_profile_keep_both_description, keepName),
                    onClick = { pick(ProfileRestoreChoice.KeepBoth(keepName)) },
                )
            }
        }
    }
}
