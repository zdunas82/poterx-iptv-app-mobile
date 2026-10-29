package tv.own.owntv.mobile.ui.categorylock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.core.categorylock.CategoryLockClosed
import tv.own.owntv.core.categorylock.CategoryLockFlow
import tv.own.owntv.core.categorylock.CategoryLockUi
import tv.own.owntv.core.categorylock.PinAttemptLimiter
import tv.own.owntv.core.categorylock.SealedCategoryAction
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/** Core's [CategoryLockFlow] for the Live TV, Guide, Movies and Series screens, which each show their own prompts. */
class CategoryLockViewModel(
    settings: SettingsRepository,
    profileDao: ProfileDao,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
    session: CategoryUnlockSession,
    limiter: PinAttemptLimiter,
) : ViewModel() {
    private val flow = CategoryLockFlow(viewModelScope, settings, profileDao, categoryDao, customize, session, limiter)

    val ui: StateFlow<CategoryLockUi> = flow.ui
    val closed: SharedFlow<CategoryLockClosed> = flow.closed

    fun open(type: MediaType, key: LiveKey, then: SealedCategoryAction? = null) = flow.open(type, key, then)
    fun toggleLock(type: MediaType, key: LiveKey) = flow.toggleLock(type, key)
    fun submit(pin: String) = flow.submit(pin)
    fun dismiss() = flow.dismiss()
}
