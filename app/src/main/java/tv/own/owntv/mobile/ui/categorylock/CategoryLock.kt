package tv.own.owntv.mobile.ui.categorylock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.categorylock.SealedCategoryAction
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * One screen's category locks for section [type]: Lock with PIN / Remove lock, Enter PIN on a sealed
 * category, and a hold-menu action on a sealed category, which runs only once the PIN has opened it.
 * The prompts themselves come with [rememberCategoryLock]. Main thread only.
 */
@Stable
class CategoryLock internal constructor(val type: MediaType, private val vm: CategoryLockViewModel) {
    private var pending: (() -> Unit)? = null

    /** Lock with PIN, or Remove lock (asks the PIN). */
    fun toggleLock(key: LiveKey) = vm.toggleLock(type, key)

    /** Enter PIN on a sealed category: it opens for the rest of the session. */
    fun enterPin(key: LiveKey) = vm.open(type, key)

    /** Runs [block] on [key] now, or, while the category is [sealed], once the PIN opens it. */
    fun guarded(key: LiveKey, sealed: Boolean, action: SealedCategoryAction, block: () -> Unit) {
        if (!sealed) return block()
        pending = block
        vm.open(type, key, action)
    }

    internal fun runPending() {
        pending?.invoke()
        pending = null
    }

    internal fun dropPending() {
        pending = null
    }
}

/** [CategoryLock] for section [type], with its PIN sheets placed here. */
@Composable
internal fun rememberCategoryLock(type: MediaType): CategoryLock {
    val vm: CategoryLockViewModel = koinViewModel()
    val lock = remember(vm, type) { CategoryLock(type, vm) }
    CategoryLockPrompts(
        type = type,
        onAction = { _, _ -> lock.runPending() },
        onClosed = { _, _ -> lock.dropPending() },
        vm = vm,
    )
    return lock
}

/**
 * Whether a category's hold menu offers Lock with PIN (false) or Remove lock (true); null offers neither.
 * Only provider folders can be locked: every other list finds a locked item by its provider folder, so a
 * lock on a custom category would guard that one list and nothing else. A custom one locked already (by a
 * restored backup) still offers Remove lock.
 */
fun lockMenuState(key: LiveKey, locked: Boolean): Boolean? = when (key) {
    is LiveKey.Folder -> locked
    is LiveKey.Custom -> true.takeIf { locked }
    else -> null
}

/** What a sealed category shows instead of its channels or titles: the sentence and Enter PIN. */
@Composable
internal fun LockedCategoryState(onEnterPin: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.content_category_locked_placeholder),
                style = stageText(13.5f, 500),
                color = LocalStagePalette.current.muted,
                textAlign = TextAlign.Center,
            )
            StageButton(
                onClick = onEnterPin,
                text = stringResource(R.string.settings_customize_enter_pin),
                icon = MobileIcons.Lock,
            )
        }
    }
}
