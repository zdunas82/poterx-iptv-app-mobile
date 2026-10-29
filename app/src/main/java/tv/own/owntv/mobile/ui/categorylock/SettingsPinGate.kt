package tv.own.owntv.mobile.ui.categorylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsPinLock
import tv.own.owntv.core.settings.requiresPin
import tv.own.owntv.mobile.R

/**
 * One visit to the pages the Settings PIN guards — Settings and every page under it, Backup & Restore,
 * Local sync. Whether a visit is open is decided when it starts, per profile: leaving those pages
 * ([end]) or switching profile locks them again, and turning the lock on from inside Settings does not
 * throw the user out. Main thread only.
 */
class SettingsPinSession {
    private var profileId: Long? = null
    private val open = mutableStateOf(false)

    /** Whether this visit is open for [lock]'s profile; the first page of a visit decides. */
    fun isOpen(lock: SettingsPinLock): Boolean {
        if (profileId != lock.profileId) {
            profileId = lock.profileId
            open.value = !lock.requiresPin(unlocked = false)
        }
        return open.value
    }

    fun unlock() {
        open.value = true
    }

    /** The user left the guarded pages: the next one starts a new visit. */
    fun end() {
        profileId = null
    }
}

/**
 * Shows [content] only once the profile's Customize PIN has been entered, when the profile asks for it
 * before Settings opens. Nothing shows while the lock is read, so a guarded page never flashes open.
 * Cancel on the PIN sheet calls [onDismiss], which leaves the page. Not [enabled]: [content] as it is.
 */
@Composable
fun SettingsPinGate(onDismiss: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) return content()
    val vm = koinViewModel<SettingsPinViewModel>()
    val session = koinInject<SettingsPinSession>()
    val lock by vm.lock.collectAsStateWithLifecycle()
    if (!lock.loaded) return
    val pin = lock.pin
    if (pin != null && lock.requiresPin(session.isOpen(lock))) {
        PinDoorSheet(
            pin = pin,
            onUnlock = session::unlock,
            onDismiss = onDismiss,
            description = stringResource(R.string.settings_customize_pin_locked),
        )
        return
    }
    content()
}
