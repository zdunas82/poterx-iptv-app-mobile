package tv.own.owntv.mobile.ui.profiles

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.screens.settings.SheetButtons
import tv.own.owntv.mobile.ui.screens.settings.SheetText
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * The PIN sheet for a protected profile that is not the one in use: either its profile PIN or its
 * Settings PIN opens it ([verify], core's ProfileProtection, which counts wrong entries on the profile
 * door). [body] says what the PIN is for.
 */
@Composable
internal fun ProtectedProfilePinSheet(
    profileName: String,
    body: String,
    verify: suspend (String) -> PinAttempt,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var waitSeconds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(waitSeconds > 0) {
        while (waitSeconds > 0) {
            delay(1_000)
            waitSeconds--
        }
    }
    fun submit() {
        val entered = pin
        if (entered.isBlank()) return
        scope.launch {
            when (val result = verify(entered)) {
                PinAttempt.Accepted -> { keyboard?.hide(); onUnlocked() }
                PinAttempt.Wrong -> { wrong = true; pin = "" }
                is PinAttempt.Wait -> { wrong = false; waitSeconds = result.seconds; pin = "" }
            }
        }
    }
    val title = stringResource(R.string.settings_backup_profile_locked, profileName)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title)
        SheetText(body)
        StageField(
            value = pin,
            onValueChange = { if (it.all(Char::isDigit)) { pin = it; wrong = false } },
            label = stringResource(R.string.profiles_pin),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            password = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            isError = wrong || waitSeconds > 0,
            supportingText = when {
                waitSeconds > 0 -> pluralStringResource(R.plurals.content_category_pin_wait, waitSeconds, waitSeconds)
                wrong -> stringResource(R.string.profiles_wrong_pin)
                else -> null
            },
            onImeDone = { submit() },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SheetButtons(
            confirm = stringResource(R.string.settings_backup_unlock),
            confirmEnabled = pin.isNotBlank(),
            onConfirm = { submit() },
            onDismiss = { keyboard?.hide(); onDismiss() },
        )
    }
}
