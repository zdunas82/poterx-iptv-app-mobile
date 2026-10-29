package tv.own.owntv.mobile.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.rememberAppliedIcon
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * App icon, from Settings › App and the wizard's Display step: "App" over the title, its line, then the
 * nine icons three to a row — 64 dp tiles, the name under each; the chosen one ringed in the focus colour with a
 * gap, its name in the accent. With [askRestart] a pick the launcher does not show yet turns this sheet into the
 * restart question; the wizard passes false, as the TV does — nothing is on the home screen to update yet.
 */
@Composable
fun AppIconSheet(chosen: AppIcon, onPick: (AppIcon) -> Unit, onDismiss: () -> Unit, askRestart: Boolean = true) {
    val applied = rememberAppliedIcon()
    var restartFor by remember { mutableStateOf<AppIcon?>(null) }
    restartFor?.let { AppIconRestartSheet(it, onDismiss); return }
    val palette = LocalStagePalette.current
    val accent = stageAccent
    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(stringResource(R.string.settings_app_icon), eyebrow = stringResource(R.string.settings_group_app))
        SheetText(stringResource(R.string.settings_app_icon_sheet_mobile))
        SheetOrder.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                row.forEach { icon ->
                    val on = icon == chosen
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.RadioButton) {
                                onPick(icon)
                                if (askRestart && icon != applied) restartFor = icon else onDismiss()
                            }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // `box-shadow: 0 0 0 2px page, 0 0 0 4px focus`: a 2 dp gap, then the 2 dp ring.
                        Box(
                            Modifier
                                .size(72.dp)
                                .then(if (on) Modifier.border(2.dp, accent.focus, RoundedCornerShape(20.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            LauncherIcon(icon, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                        }
                        Text(
                            stringResource(icon.label),
                            style = stageText(11f, 700),
                            color = if (on) accent.accent else palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(bottom = 6.dp))
    }
}

/** In order: the default first, then the colours, the dark ones, Pixel last. */
private val SheetOrder = listOf(
    AppIcon.EGGSHELL, AppIcon.OLIVE, AppIcon.OLIVE_CREAM,
    AppIcon.PETROL, AppIcon.COBALT, AppIcon.TOMATO,
    AppIcon.SUNFLOWER, AppIcon.BOARD, AppIcon.PIXEL,
)

/**
 * The icon exactly as the launcher draws it (its adaptive mipmap), which Compose's painters cannot load, so it is
 * rasterised once at the drawn size.
 */
@Composable
private fun LauncherIcon(icon: AppIcon, modifier: Modifier) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.roundToPx() }
    val bitmap = remember(icon, px) {
        androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, icon.launcherRes(), context.theme)
            ?.toBitmap(px, px)?.asImageBitmap()
    }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap, contentDescription = null, modifier = modifier)
}

private fun AppIcon.launcherRes(): Int = when (this) {
    AppIcon.EGGSHELL -> R.mipmap.owntv_icon_eggshell
    AppIcon.OLIVE -> R.mipmap.owntv_icon_olive
    AppIcon.OLIVE_CREAM -> R.mipmap.owntv_icon_olive_cream
    AppIcon.PETROL -> R.mipmap.owntv_icon_petrol
    AppIcon.COBALT -> R.mipmap.owntv_icon_cobalt
    AppIcon.TOMATO -> R.mipmap.owntv_icon_tomato
    AppIcon.SUNFLOWER -> R.mipmap.owntv_icon_sunflower
    AppIcon.BOARD -> R.mipmap.owntv_icon_board
    AppIcon.PIXEL -> R.mipmap.owntv_icon_pixel
}

/**
 * "Restart OwnTV?" after a new icon was saved (from Settings, or brought in by a restore). Restart now switches the
 * launcher icon and reopens the app through it; Later leaves it to [AppIconSwitcher], which applies it the next
 * time the app is in the background.
 */
@Composable
fun AppIconRestartSheet(icon: AppIcon, onDismiss: () -> Unit) {
    val context = LocalContext.current
    StageConfirmSheet(
        title = stringResource(R.string.app_icon_restart_title),
        text = stringResource(R.string.app_icon_restart_message),
        confirm = stringResource(R.string.app_icon_restart_now),
        onConfirm = { context.findActivity()?.let { AppIconSwitcher.restartWith(it, icon) } },
        onDismiss = onDismiss,
        cancel = stringResource(R.string.update_later),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
