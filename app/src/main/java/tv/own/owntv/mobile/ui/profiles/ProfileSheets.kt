package tv.own.owntv.mobile.ui.profiles

import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.PROFILE_AVATAR_COUNT
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent

/** A PIN is four to six digits — long enough to be a lock, short enough to type one-handed. */
private const val PIN_MIN = 4
private const val PIN_MAX = 6

private val AVATAR_PICK_SIZE = 52.dp

/**
 * Ask for a profile's PIN. [onSubmit] returns false when the digits were wrong, which keeps the
 * sheet open and says so rather than dropping the user back where they started with no explanation.
 *
 * A sheet, not a centred dialog: that is what every prompt in this app is, and it is the only form
 * that frosts the same wallpaper the rest of the app frosts — a dialog is its own window and cannot.
 * It also rises to the thumb instead of sitting where the keyboard will cover it.
 */
@Composable
fun ProfilePinSheet(
    profileName: String,
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val autofill = LocalAutofillManager.current
    fun submit() {
        if (pin.length in PIN_MIN..PIN_MAX) {
            // A profile PIN is not a password to keep: ending the autofill session stops a password
            // manager from offering to save it.
            autofill?.cancel()
            if (!onSubmit(pin)) { wrong = true; pin = "" } else keyboard?.hide()
        }
    }
    val title = stringResource(R.string.profiles_enter_pin, profileName)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title)
        StageField(
            value = pin,
            onValueChange = {
                if (it.length <= PIN_MAX && it.all(Char::isDigit)) {
                    pin = it
                    wrong = false
                }
            },
            label = stringResource(R.string.profiles_pin),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            password = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            isError = wrong,
            supportingText = if (wrong) stringResource(R.string.profiles_wrong_pin) else null,
            onImeDone = { submit() },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SheetButtons(
            confirm = stringResource(R.string.common_ok),
            confirmEnabled = pin.length >= PIN_MIN,
            // Deliberately not dismissed here: a wrong PIN has to keep the sheet up to say so.
            onConfirm = { submit() },
            onDismiss = {
                keyboard?.hide()
                focusManager.clearFocus()
                onDismiss()
            },
        )
    }
}

/**
 * Create or edit a profile: [initial] non-null is an edit.
 *
 * [onConfirm]'s pin argument follows core's convention — null leaves the PIN alone, "" removes the
 * lock, anything else sets it.
 *
 * [takenNames] are the other profiles' names, lowercased. Names have to be unique because a backup
 * merges by name, so a collision blocks the button with the reason shown under the field.
 */
@Composable
fun ProfileEditorSheet(
    initial: ProfileEntity?,
    takenNames: Set<String>,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?, picture: android.net.Uri?) -> Unit,
    onDismiss: () -> Unit,
    extraAction: (() -> Unit)? = null,
    // A picture of the user's own. On an existing profile it applies immediately rather than on
    // Save — the file is copied the moment it is chosen, so there is nothing sensible to do with it
    // if the sheet is then cancelled. A new profile has no id to store one under yet, so the sheet
    // holds the choice and hands it to [onConfirm] with the rest.
    customPath: String = "",
    onPickPicture: ((android.net.Uri) -> Unit)? = null,
    onClearPicture: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: -1) }
    var isKids by remember { mutableStateOf(initial?.isKids == true) }
    var pin by remember { mutableStateOf("") }
    var pendingPicture by remember { mutableStateOf<android.net.Uri?>(null) }
    val picturePath = pendingPicture?.toString() ?: customPath
    val autofill = LocalAutofillManager.current
    var removePin by remember { mutableStateOf(false) }
    val nameTaken = name.trim().isNotEmpty() && name.trim().lowercase() in takenNames
    val accent = stageAccent.accent
    val title = stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit)

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title)
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            StageField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.profiles_name),
                placeholder = stringResource(R.string.profiles_name_hint),
                imeAction = ImeAction.Next,
                isError = nameTaken,
                supportingText = if (nameTaken) stringResource(R.string.profiles_name_taken) else null,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            StageGroupLabel(stringResource(R.string.profiles_avatar))
            if (onPickPicture != null || initial == null) {
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                ) { uri -> if (uri != null) onPickPicture?.invoke(uri) ?: run { pendingPicture = uri } }
                StageSettingRow(
                    title = stringResource(R.string.profiles_avatar_own_picture),
                    minHeight = 48.dp,
                    leading = {
                        ProfileAvatar(avatarId = avatarId, imagePath = picturePath, modifier = Modifier.size(34.dp).clip(CircleShape))
                    },
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                ) {
                    if (pendingPicture != null || (customPath.isNotBlank() && onClearPicture != null)) {
                        StageButton(
                            onClick = { if (pendingPicture != null) pendingPicture = null else onClearPicture?.invoke() },
                            text = stringResource(R.string.common_clear),
                            style = StageButtonStyle.PLAIN,
                            small = true,
                        )
                    }
                }
            }
            // -1 is "no picture", and it is offered first so a profile can be left plain.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // Count + key instead of a materialized range: no list per recomposition, and an
                // avatar keeps its composition across avatar-count changes.
                items(count = PROFILE_AVATAR_COUNT + 1, key = { it - 1 }) { index ->
                    val id = index - 1
                    Box(
                        modifier = Modifier
                            .size(AVATAR_PICK_SIZE)
                            .clip(CircleShape)
                            .then(if (id == avatarId) Modifier.border(3.dp, accent, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { avatarId = id }
                            .padding(4.dp),
                    ) {
                        ProfileAvatar(
                            avatarId = id,
                            modifier = Modifier.size(AVATAR_PICK_SIZE - 8.dp).clip(CircleShape),
                        )
                    }
                }
            }
            SwitchRow(
                label = stringResource(R.string.profiles_kids),
                description = stringResource(R.string.profiles_kids_description),
                checked = isKids,
                onToggle = { isKids = it },
            )
            if (initial?.pinHash != null) {
                SwitchRow(
                    label = stringResource(R.string.profiles_remove_pin),
                    description = stringResource(R.string.profiles_no_pin),
                    checked = removePin,
                    onToggle = { removePin = it },
                )
            }
            if (!removePin) {
                StageField(
                    value = pin,
                    onValueChange = { if (it.length <= PIN_MAX && it.all(Char::isDigit)) pin = it },
                    label = stringResource(
                        if (initial?.pinHash != null) R.string.profiles_change_pin else R.string.profiles_optional_pin,
                    ),
                    placeholder = stringResource(R.string.profiles_pin_digits),
                    password = true,
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        SheetButtons(
            confirm = stringResource(if (initial == null) R.string.profiles_create else R.string.profiles_save),
            confirmEnabled = name.isNotBlank() && !nameTaken &&
                (removePin || pin.isEmpty() || pin.length >= PIN_MIN),
            onConfirm = {
                autofill?.cancel()
                onConfirm(name.trim(), avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() }, pendingPicture)
                onDismiss()
            },
            onDismiss = onDismiss,
            // Delete is last, under Cancel and away from the fields, where a thumb reaching for the
            // PIN box will not find it.
            destructive = extraAction,
        )
    }
}

/** A label, a line of explanation and a switch — the whole row is the button. */
@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    StageSettingRow(title = label, subtitle = description, minHeight = 48.dp, role = Role.Switch, onClick = { onToggle(!checked) }) {
        StageSwitch(checked)
    }
}

/** Confirm (✓, filled once it can be used), Cancel (✕) and an optional Delete — how these sheets end. */
@Composable
private fun SheetButtons(
    confirm: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: (() -> Unit)? = null,
) {
    StageMenuItem(
        confirm,
        onClick = onConfirm,
        icon = MobileIcons.Check,
        filled = confirmEnabled,
        enabled = confirmEnabled,
        modifier = Modifier.padding(top = 6.dp),
    )
    StageMenuItem(stringResource(R.string.common_cancel), onClick = onDismiss, icon = MobileIcons.Close)
    if (destructive != null) {
        StageMenuItem(stringResource(R.string.common_delete), onClick = destructive, icon = MobileIcons.Delete, danger = true)
    }
}
