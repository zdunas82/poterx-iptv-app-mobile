package tv.own.owntv.mobile.ui.theme

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.own.owntv.core.theme.GlassConfig
import kotlin.math.ln
import kotlin.math.pow

/** The user's Glass settings — on or off, and the opacity — read from core and provided at the theme root. */
val LocalGlass = staticCompositionLocalOf { GlassConfig() }

/** How deep the frost goes at full strength. */
private val MaxBlurRadius = 44.dp

/** The shallowest rung of the ladder — a hint of diffusion rather than a sharp photograph. */
private val MinBlurRadius = 2.dp

/** Rungs on the frost ladder. Ten, the same count the television builds. */
private const val FROST_LEVELS = 10

/** A low-RAM phone gets a coarser ladder over the same range, not a shallower one. */
private const val LOW_RAM_FROST_LEVELS = 5

/** The two ladder rungs bracketing a requested frost depth, and how far between them it sits. */
@Immutable
data class FrostSelection(
    internal val lower: GraphicsLayer,
    internal val upper: GraphicsLayer?,
    internal val upperWeight: Float,
)

/**
 * The wallpaper, blurred at ten depths: the picture's own Blur draws from it, and so does every glass
 * pane's slice of what is behind it.
 *
 * One ladder for the whole screen rather than one blur per panel: each rung is recorded a single time
 * and each surface merely draws the rung it wants back, shifted so the slice lines up with what is
 * actually behind it.
 *
 * The rungs are geometric — 2 dp, 2.8 dp, 4 dp … 44 dp — because blur reads logarithmically, so equal
 * ratios are equal-looking steps and the shallow end gets the fine resolution.
 */
@Stable
class GlassBackdrop internal constructor(
    internal val levels: List<GraphicsLayer>,
    /** Rung radii in pixels, ascending, parallel to [levels]. */
    internal val radiiPx: FloatArray,
    internal var rootOffset: Offset,
) {
    /**
     * The rungs bracketing [strength] of the full frost depth, cross-faded.
     *
     * The pair is chosen by the radius the caller is really asking for — `MaxBlurRadius * strength` —
     * rather than by ladder index, so a request keeps meaning the same depth however many rungs the
     * device could afford, and the weight is taken in log space because the ladder is geometric.
     */
    fun frostFor(strength: Float): FrostSelection? {
        if (levels.isEmpty() || strength <= 0f) return null
        val target = radiiPx.last() * strength.coerceIn(0f, 1f)
        // Below the shallowest rung there is nothing to fade towards, so it is the whole answer.
        if (target <= radiiPx.first()) return FrostSelection(levels.first(), null, 0f)
        val upper = radiiPx.indexOfFirst { it >= target }
        val weight = ln(target / radiiPx[upper - 1]) / ln(radiiPx[upper] / radiiPx[upper - 1])
        return FrostSelection(levels[upper - 1], levels[upper], weight.coerceIn(0f, 1f))
    }
}

/** The blurred backdrop, or null when there is no wallpaper or the device predates hardware blur. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Real backdrop blur is [android.graphics.RenderEffect], which is API 31. Below that there is no frost —
 * never a per-frame software blur, which on a phone would cost battery on every scroll.
 */
private val supportsBackdropBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The root the whole app sits in: the wallpaper underneath, and a ladder of blurred copies of it kept
 * aside for the glass to sample.
 *
 * With no wallpaper set, or on a device without hardware blur, this is just the page colour and the
 * backdrop local stays null.
 */
@Composable
fun GlassBackdropRoot(content: @Composable () -> Unit) {
    // Glass & background: the picture is Background › Picture's, not the glass's — Glass off keeps it, and
    // Stage or Plain hide it while the file stays for a return to Picture.
    val bg = tv.own.owntv.mobile.ui.stage.LocalStageBackground.current
    val bgImagePath = bg.imagePath
    val hasImage = bg.showsPicture
    // The ladder serves the picture's own Blur as well as the glass that samples it.
    val frosted = hasImage && supportsBackdropBlur

    // Ten full-screen textures is real memory even though only the rungs actually drawn are ever
    // rasterized, so a low-RAM phone gets half the rungs — a coarser ladder over the same range,
    // which costs a little cross-fade accuracy rather than the deep frost the top rung provides.
    val context = LocalContext.current
    val levelCount = remember(context) {
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager?.isLowRamDevice == true) LOW_RAM_FROST_LEVELS else FROST_LEVELS
    }

    // Recorded sharp, drawn sharp behind the content; each rung replays it through its own blur, so
    // the wallpaper is decoded and rasterized exactly once for all of them.
    val sharp = rememberGraphicsLayer()
    val levels = List(levelCount) { rememberGraphicsLayer() }
    val density = LocalDensity.current
    val radiiPx = remember(density, levelCount) {
        val min = with(density) { MinBlurRadius.toPx() }
        val max = with(density) { MaxBlurRadius.toPx() }
        val ratio = (max / min).pow(1f / (levelCount - 1))
        FloatArray(levelCount) { min * ratio.pow(it.toFloat()) }
    }

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember(frosted, radiiPx) {
        if (frosted) GlassBackdrop(levels, radiiPx, Offset.Zero) else null
    }
    backdrop?.rootOffset = rootOffset

    // Clearing the wallpaper takes the image away, and with it the only thing that ever re-records
    // the ladder — so ten full-screen textures would sit in graphics memory for the rest of the
    // session. Re-recording each rung empty at 1×1 is what actually hands them back.
    LaunchedEffect(frosted, density) {
        if (!frosted) {
            levels.forEach { it.record(density, LayoutDirection.Ltr, IntSize(1, 1)) {} }
        }
    }

    val palette = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.page)
            .onGloballyPositioned { rootOffset = it.positionInRoot() },
    ) {
        if (hasImage) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        sharp.record { this@drawWithContent.drawContent() }
                        if (frosted) {
                            levels.forEachIndexed { index, layer ->
                                val radius = radiiPx[index]
                                layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                                layer.record { drawLayer(sharp) }
                            }
                        }
                        // The picture at its Blur, from the same ladder (the TV's BackgroundPicture); sharp at 0
                        // or on a device that cannot blur.
                        val shown = if (bg.blurPct > 0) backdrop?.frostFor(bg.blurPct / 100f) else null
                        if (shown == null) {
                            drawLayer(sharp)
                        } else {
                            shown.lower.alpha = 1f
                            drawLayer(shown.lower)
                            shown.upper?.let { it.alpha = shown.upperWeight; drawLayer(it) }
                        }
                    },
            )
            // Darken. Light: the picture fades into the paper instead, always far enough for dark ink to read.
            val dim = bg.dimPct / 100f
            Box(Modifier.fillMaxSize().background(if (palette.isLight) Color(0xFFF4F7F5).copy(alpha = 0.55f + 0.45f * dim) else Color(0xFF05080A).copy(alpha = dim)))
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            content()
        }
    }
}
