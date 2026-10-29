package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/**
 * One action in a long-press content menu.
 *
 * [key] is the stable identifier the saved order is written in, and it must match the key core's
 * catalogue uses for the same action — that catalogue is what the settings screen arranges, on
 * either app. [group] puts the action under its group's label, where the sheet has one.
 */
data class SheetAction(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val group: Int = 0,
    /** A menu item's value at the end ("7 days", "Auto", "Global"). */
    val value: String? = null,
    /** Shown but not available here (no trailer, not in history) — the TV's dimmed item. */
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The long-press menu for a channel, movie, series or episode.
 *
 * The TV app draws these as a focused column in the middle of the screen; here they rise as a
 * bottom sheet, because a phone's actions belong under a thumb. What the two share is the order:
 * both read the same saved arrangement and apply it with core's rule, so a user who moved
 * "Download" to the top of the movie menu on the television finds it at the top here too.
 *
 * The item's picture and name are on top, each group under its label (WATCH, LIBRARY…).
 *
 * Tapping an action dismisses the sheet — every one of them either navigates or changes the row
 * being looked at, and a sheet left open over the result is a sheet the user has to close.
 */
@Composable
fun ContentMenuSheet(
    menu: ContentMenu,
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    groupLabels: Map<Int, String> = emptyMap(),
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
) {
    val settings: SettingsRepository = koinInject()
    val order by remember(menu) { settings.menuOrder(menu.name.lowercase()) }
        .collectAsStateWithLifecycle(emptyList())

    MobileBottomSheet(onDismissRequest = onDismiss, title = title, modifier = modifier) {
        StageSheetHeader(title = title, subtitle = subtitle, leading = leading)
        // A long menu (catch-up, Multiview, Move…) scrolls rather than running off the screen.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            var previousGroup: Int? = null
            applyMenuOrder(actions, order) { it.key }.forEach { action ->
                if (action.group != previousGroup) groupLabels[action.group]?.let { StageGroupLabel(it) }
                previousGroup = action.group
                StageMenuItem(
                    label = action.label,
                    onClick = {
                        action.onClick()
                        onDismiss()
                    },
                    icon = action.icon,
                    value = action.value,
                    danger = action.destructive,
                    enabled = action.enabled,
                )
            }
        }
    }
}
