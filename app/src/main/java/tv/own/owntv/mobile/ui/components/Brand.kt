package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.mobile.R

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * The icon's flat mark (no shadow, no next card); at 32 dp and below the simpler small drawing. With Settings ›
 * Accent-colored logo on (the default) the play triangle takes the accent, as on the TV: Pixel draws its dots
 * with an accent triangle, a card gets a tinted triangle over its own. [followAccent] false draws the icon as
 * the launcher shows it (the icon picker).
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    val small = size <= 32.dp
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val on by settings.brandAccentTriangle.collectAsStateWithLifecycle(initialValue = true)
    val accent = tv.own.owntv.mobile.ui.stage.stageAccent.accent.takeIf { followAccent && on }
    if (icon == AppIcon.PIXEL && !small && accent != null) {
        PixelMark(accent, modifier.size(size))
        return
    }
    Box(modifier.size(size)) {
        Image(painterResource(if (small) icon.markSmall else icon.mark), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (accent != null && icon.isCard(small)) {
            Image(
                painterResource(if (small) R.drawable.owntv_mark_play_small else R.drawable.owntv_mark_play),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent),
            )
        }
    }
}

/** "Pixel": core's dot-matrix TV set, the play triangle's dots in [triangle]. */
@Composable
private fun PixelMark(triangle: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 100f
        val m = tv.own.owntv.core.brand.PixelMark
        m.dots.forEach { d ->
            val color = when (d.kind) {
                tv.own.owntv.core.brand.PixelMark.Kind.BODY -> Color(m.BODY)
                tv.own.owntv.core.brand.PixelMark.Kind.TRIANGLE -> triangle
                tv.own.owntv.core.brand.PixelMark.Kind.SIDE -> Color(m.SIDE)
            }
            drawCircle(color, m.DOT_RADIUS * u, androidx.compose.ui.geometry.Offset(d.x * u, d.y * u))
        }
        drawCircle(Color(m.POWER), m.POWER_RADIUS * u, androidx.compose.ui.geometry.Offset(m.POWER_X * u, m.POWER_Y * u))
    }
}

/**
 * The wordmark (the TV's `Wordmark`): lowercase "own" in cream on a dark page (the
 * text colour on a light one), "tv" in the accent, drawn from core's two vectors at [width]; its height
 * follows the 988 × 182 drawing.
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier) {
    val palette = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current
    val own = if (palette.isLight) palette.text else WordmarkCream
    val tv = tv.own.owntv.mobile.ui.stage.stageAccent.accent
    androidx.compose.foundation.layout.Box(modifier.size(width, width * 182f / 988f)) {
        Image(painterResource(R.drawable.owntv_wordmark_own), null, Modifier.matchParentSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(own))
        Image(painterResource(R.drawable.owntv_wordmark_tv), null, Modifier.matchParentSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tv))
    }
}

private val WordmarkCream = Color(0xFFFFF6EE)
