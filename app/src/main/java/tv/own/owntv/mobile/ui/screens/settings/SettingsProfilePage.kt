package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.profiles.ProfileAvatar
import tv.own.owntv.mobile.ui.profiles.ProfileEditorSheet
import tv.own.owntv.mobile.ui.profiles.ProfileGateSession
import tv.own.owntv.mobile.ui.profiles.ProfilePinSheet
import tv.own.owntv.mobile.ui.profiles.ProfilesViewModel

/**
 * Settings › Profile: who is watching, and who may. A row per profile — its avatar, its name, a line
 * saying how it is set (Kids mode, PIN lock or No lock) and IN USE on the one in use — then Add a profile.
 * Tapping a profile switches to it, asking for the PIN first if it has one; tapping the one in use, or holding
 * any, opens it for editing. The last profile cannot be deleted: an app with no profile has nobody to show to.
 * More's profile card opens this same page.
 */
@Composable
internal fun profileSections(
    vm: ProfilesViewModel = koinViewModel(),
    // The shell's own session (the Activity's), not one of this page's: an unlock recorded on a copy never
    // reached the shell, so a switch from here asked "Who's watching?" again.
    gateSession: ProfileGateSession = koinViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity),
): List<SettingSection> {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.profiles_default_name)
    val locale = LocalConfiguration.current.locales[0]
    val sep = stringResource(R.string.content_epg_bits_separator)

    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    val list = profiles.orEmpty()

    // Switching here is the same authentication boundary as the launch chooser, so the session is told.
    fun switchTo(profile: ProfileEntity) = vm.switchTo(profile) { gateSession.unlock(profile.id) }

    // A locked profile is unlocked before it is switched into: its library never appears without the PIN.
    pinFor?.let { locked ->
        ProfilePinSheet(
            profileName = locked.name,
            onSubmit = { pin -> vm.verifyPin(locked, pin).also { if (it) { pinFor = null; switchTo(locked) } } },
            onDismiss = { pinFor = null },
        )
    }
    if (adding) {
        ProfileEditorSheet(
            initial = null,
            takenNames = list.map { it.name.lowercase() }.toSet(),
            onConfirm = { name, avatarId, isKids, pin, picture -> vm.create(name, avatarId, isKids, pin, fallbackName, picture) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { profile ->
        ProfileEditorSheet(
            initial = profile,
            takenNames = list.filter { it.id != profile.id }.map { it.name.lowercase() }.toSet(),
            onConfirm = { name, avatarId, isKids, pin, _ -> vm.edit(profile, name, avatarId, isKids, pin) },
            onDismiss = { editing = null },
            customPath = profile.avatarPath.orEmpty(),
            onPickPicture = { uri -> vm.setPicture(profile, uri) },
            onClearPicture = { vm.clearPicture(profile) },
            extraAction = if (list.size > 1) ({ deleting = profile }) else null,
        )
    }
    deleting?.let { profile ->
        StageConfirmSheet(
            title = stringResource(R.string.profiles_delete_title, profile.name),
            text = stringResource(R.string.profiles_delete_message),
            confirm = stringResource(R.string.common_delete),
            confirmIcon = MobileIcons.Delete,
            danger = true,
            onConfirm = { vm.delete(profile); editing = null },
            onDismiss = { deleting = null },
        )
    }

    val inUse = stringResource(R.string.settings_profile_in_use)
    val kidsTag = stringResource(R.string.profiles_kids_tag)
    val pinTag = stringResource(R.string.profiles_locked_tag)
    val rows = list.map { profile ->
        val active = profile.id == activeId
        val lock = stringResource(if (profile.pinHash != null) R.string.settings_profile_pin_lock else R.string.settings_profile_no_lock)
        val line = listOfNotNull(
            stringResource(R.string.settings_profile_kids).takeIf { profile.isKids },
            lock,
            inUse.takeIf { active },
        ).joinToString(sep)
        val tags = listOfNotNull(kidsTag.takeIf { profile.isKids }, pinTag.takeIf { profile.pinHash != null })
        SettingItem(
            key = "profile_${profile.id}",
            icon = MobileIcons.Person,
            title = profile.name,
            line = line,
            value = when {
                active -> SettingValue.Tag(inUse.uppercase(locale))
                // One tag: "KIDS · PIN".
                tags.isNotEmpty() -> SettingValue.Tag(tags.joinToString(sep) { it.uppercase(locale) })
                else -> SettingValue.Opens(null)
            },
            onClick = {
                when {
                    active -> editing = profile
                    profile.pinHash != null -> pinFor = profile
                    else -> switchTo(profile)
                }
            },
            pinnable = false,
            leading = {
                ProfileAvatar(
                    avatarId = profile.avatarId,
                    imagePath = profile.avatarPath.orEmpty(),
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
            },
            onLongClick = { editing = profile },
        )
    }
    val add = SettingItem(
        "profile_add", MobileIcons.Add, stringResource(R.string.settings_profile_add), stringResource(R.string.settings_profile_add_line),
        SettingValue.Opens(null), onClick = { adding = true }, pinnable = false,
    )
    return listOf(SettingSection(null, rows + add))
}
