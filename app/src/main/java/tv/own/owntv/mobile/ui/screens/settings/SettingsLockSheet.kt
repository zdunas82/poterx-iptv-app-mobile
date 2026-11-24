package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.categorylock.PinDoorSheet
import tv.own.owntv.mobile.ui.categorylock.SettingsPinViewModel
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.settings.customize.TextPromptDialog
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

private sealed interface LockStep {
    data object Choose : LockStep
    data class Check(val then: LockStep) : LockStep
    data object TurnOff : LockStep
    data object NewPin : LockStep
    data class Confirm(val first: String, val mismatch: Boolean = false) : LockStep
}

/**
 * Settings › App › Settings lock. Off: a new PIN, twice, turns it on. On: Change PIN or Turn off, each
 * asking the current Settings PIN first — wrong entries count only on the Settings door.
 */
@Composable
internal fun SettingsLockSheet(vm: SettingsPinViewModel, onDismiss: () -> Unit) {
    val lock by vm.lock.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf<LockStep>(if (lock.on) LockStep.Choose else LockStep.NewPin) }
    when (val current = step) {
        LockStep.Choose -> MobileBottomSheet(onDismissRequest = onDismiss) {
            StageSheetHeader(stringResource(R.string.settings_lock), subtitle = stringResource(R.string.settings_lock_description))
            StageMenuItem(stringResource(R.string.settings_lock_change_pin), onClick = { step = LockStep.Check(LockStep.NewPin) }, icon = MobileIcons.Lock)
            StageMenuItem(stringResource(R.string.settings_lock_turn_off), onClick = { step = LockStep.Check(LockStep.TurnOff) }, icon = MobileIcons.Lock, danger = true)
        }
        is LockStep.Check -> {
            val pin = lock.pin
            if (pin == null) {
                LaunchedEffect(Unit) { onDismiss() }
            } else {
                PinDoorSheet(
                    pin = pin,
                    door = PinDoor.SETTINGS,
                    onUnlock = {
                        if (current.then == LockStep.TurnOff) {
                            vm.setPin(null)
                            onDismiss()
                        } else {
                            step = current.then
                        }
                    },
                    onDismiss = onDismiss,
                )
            }
        }
        LockStep.TurnOff -> LaunchedEffect(Unit) { onDismiss() }
        LockStep.NewPin -> key("first") {
            TextPromptDialog(
                title = stringResource(R.string.settings_customize_new_pin),
                initial = "",
                allowBlank = false,
                confirmLabel = stringResource(R.string.common_ok),
                onConfirm = { entered -> step = LockStep.Confirm(entered) },
                onDismiss = onDismiss,
            )
        }
        is LockStep.Confirm -> key("confirm", current.mismatch) {
            TextPromptDialog(
                title = stringResource(if (current.mismatch) R.string.settings_customize_pin_mismatch else R.string.settings_customize_confirm_pin),
                initial = "",
                allowBlank = false,
                confirmLabel = stringResource(R.string.common_ok),
                onConfirm = { entered ->
                    if (entered == current.first) {
                        vm.setPin(entered)
                        onDismiss()
                    } else {
                        step = current.copy(mismatch = true)
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}
