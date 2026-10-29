package tv.own.owntv.mobile.poterx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.core.update.UpdateManager

/**
 * Poterx: the update machinery the screens talk to. A stock build delegates to core's
 * [UpdateManager] (GitHub Releases); a locked build with an update server built in runs
 * [PoterxUpdater] against that server instead. Both speak core's [UpdateManager.State], so the
 * update sheet, the startup check and the About page need no second set of states.
 */
interface AppUpdater {
    val state: StateFlow<UpdateManager.State>
    val currentVersion: String

    /** True while the server says the installed version is below the minimum it still serves. */
    val required: StateFlow<Boolean>

    fun check()
    fun downloadAndInstall()
    fun retry()
    fun reset()
}

/** Core's GitHub Releases updater behind the shared interface. */
class CoreUpdater(private val manager: UpdateManager) : AppUpdater {
    override val state: StateFlow<UpdateManager.State> get() = manager.state
    override val currentVersion: String get() = manager.currentVersion
    override val required: StateFlow<Boolean> = MutableStateFlow(false)
    override fun check() = manager.check()
    override fun downloadAndInstall() = manager.downloadAndInstall()
    override fun retry() = manager.retry()
    override fun reset() = manager.reset()
}
