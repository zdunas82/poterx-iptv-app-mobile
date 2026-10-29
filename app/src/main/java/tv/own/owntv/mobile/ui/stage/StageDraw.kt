package tv.own.owntv.mobile.ui.stage

import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.BackgroundStyle
import tv.own.owntv.mobile.ui.theme.LocalGlass
import tv.own.owntv.mobile.ui.theme.LocalGlassBackdrop

/*
 * The CSS effects the phone's Stage design uses, drawn the same way: `box-shadow` glows and rings, the glass
 * panel, the page background, and the two marks of the selected ladder (fx and the selected bar).
 */

/**
 * CSS `box-shadow: 0 <dy> <blur> <color>` outside [outline]. Like the browser, nothing is drawn under the
 * element itself, so a translucent fill never shows its own shadow through it. A browser blurs with
 * sigma = blur / 2; Android's shadow layer takes a radius Skia turns into sigma = 0.57735 × r + 0.5.
 */
fun DrawScope.drawBoxShadow(outline: Path, color: Color, blur: Float, dy: Float = 0f) {
    if (color.alpha <= 0f) return
    val radius = ((blur / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    clipPath(outline, ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            // The shape itself is painted at alpha 1/255 so only its shadow shows where dy moves it out.
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.copy(alpha = 1f / 255f).toArgb()
                setShadowLayer(radius, 0f, dy, color.toArgb())
            }
            canvas.nativeCanvas.drawPath(outline.asAndroidPath(), paint)
        }
    }
}

/** CSS `box-shadow: inset 0 0 0 <width> <color>`: a ring inside the element's rounded edge. */
fun DrawScope.drawInnerRing(color: Color, width: Float, radius: Float) {
    val half = width / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((radius - half).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}

/** CSS `box-shadow: 0 0 0 <width> <color>`: a solid ring just outside the element, [inset] away from it. */
fun DrawScope.drawOuterRing(color: Color, width: Float, radius: Float, inset: Float = 0f) {
    val grow = inset + width / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(-grow, -grow),
        size = Size(size.width + 2 * grow, size.height + 2 * grow),
        cornerRadius = CornerRadius(radius + grow),
        style = Stroke(width),
    )
}

private fun CacheDrawScope.outlinePath(shape: Shape): Path =
    Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@outlinePath)) }

/**
 * The Stage glass (`.ph .glass`), for chrome only: the bottom bar, sheets, the top cluster's pills.
 * Content stays flat.
 *
 * Glass on: first the slice of what is behind it, blurred — the wallpaper's frost when there is a picture,
 * then the page content from [LocalStageBackdrop] (`backdrop-filter: blur(22px) saturate(150%)`)
 * — then the tint at 0.55 + 0.45 × the user's Glass opacity, a 1 dp top highlight, a 1 dp rim and the
 * drop shadow `0 16 40`. Glass off: the solid glass-off colour, a fainter rim and a stronger shadow.
 */
@Composable
fun Modifier.stageGlass(shape: Shape): Modifier {
    val config = LocalGlass.current
    val on = config.enabled
    val palette = LocalStagePalette.current
    val light = palette.isLight
    val fill = if (on) palette.glassTint.copy(alpha = 0.55f + 0.45f * config.alpha.coerceIn(0f, 1f)) else palette.glassOff
    val highlight = Color.White.copy(alpha = if (light) 0.9f else 0.10f)
    val rim = palette.ink.copy(alpha = if (light) (if (on) 0.07f else 0.08f) else (if (on) 0.07f else 0.05f))
    // Dark: 0 16 40 black 45% (50% off). Light: 0 14 34 at 14% (0 10 28 at 12% off).
    val shadow = palette.shadow.copy(alpha = if (light) (if (on) 0.14f else 0.12f) else (if (on) 0.45f else 0.5f))
    val shadowBlur = if (light) (if (on) 34.dp else 28.dp) else 40.dp
    val shadowDy = if (light) (if (on) 14.dp else 10.dp) else 16.dp
    val backdrop = if (on) LocalGlassBackdrop.current else null
    val frost = backdrop?.frostFor(22f / 44f)
    val page = if (on) LocalStageBackdrop.current else null
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .then(if (frost != null || page != null) Modifier.onGloballyPositioned { position = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val outline = outlinePath(shape)
            val shifted = Path().apply { addPath(outline, Offset(0f, 1.dp.toPx())) }
            val px = 1.dp.toPx()
            onDrawBehind {
                drawBoxShadow(outline, shadow, shadowBlur.toPx(), shadowDy.toPx())
                clipPath(outline) {
                    if (frost != null) {
                        val slice = position - backdrop.rootOffset
                        translate(-slice.x, -slice.y) {
                            frost.lower.alpha = 1f
                            drawLayer(frost.lower)
                            frost.upper?.let { it.alpha = frost.upperWeight; drawLayer(it) }
                        }
                    }
                    if (page != null) {
                        val slice = position - page.origin
                        translate(-slice.x, -slice.y) { drawLayer(page.blurred) }
                    }
                    drawPath(outline, fill)
                    // inset 0 1px 0: the sliver its own copy, moved 1 dp down, does not cover.
                    if (on) clipPath(shifted, ClipOp.Difference) { drawRect(highlight) }
                    // inset 0 0 0 1px: a 2 dp stroke on the edge, its outer half clipped away.
                    drawPath(outline, rim, style = Stroke(px * 2f))
                }
            }
        }
}

/**
 * What Stage glass blurs: a copy of the page content under the chrome, recorded as the page draws and
 * kept blurred once for every glass panel to take its slice of. Only chrome reads it (the header, the
 * bar, sheets) — never anything inside the page, which would draw its own blurred copy into itself.
 */
@Stable
class StageBackdrop internal constructor(internal val sharp: GraphicsLayer, internal val blurred: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** The page backdrop chrome blurs, or null: below Android 12 (no RenderEffect) and with glass off. */
val LocalStageBackdrop = staticCompositionLocalOf<StageBackdrop?> { null }

/** A backdrop to record into, when glass is on and the device can blur. */
@Composable
fun rememberStageBackdrop(): StageBackdrop? {
    val sharp = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val on = LocalGlass.current.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return remember(on) { if (on) StageBackdrop(sharp, blurred) else null }
}

/**
 * Records what this element draws into [backdrop] — sharp, and a 22 dp blur of it at 150% saturation —
 * while drawing it as usual.
 */
fun Modifier.stageBackdropSource(backdrop: StageBackdrop?): Modifier =
    if (backdrop == null) {
        this
    } else {
        this
            .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                backdrop.sharp.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.sharp)
                val radius = 22.dp.toPx()
                backdrop.blurred.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                backdrop.blurred.colorFilter = Saturate150
                backdrop.blurred.record { drawLayer(backdrop.sharp) }
            }
    }

private val Saturate150 = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/**
 * The page behind a Stage screen (`.ph .bg`), following Glass & background:
 * - Stage colours: three soft lights over a dark wash, re-anchored for a tall screen (the accent light is
 *   the switchable one);
 * - Plain: a flat page, with Accent light a faint accent glow at the top left;
 * - Picture: only the Accent light — the picture itself is the backdrop root's.
 * The 170° wash runs between near-identical stops, so it is drawn as a vertical fade.
 */
@Composable
fun Modifier.stageBackground(): Modifier {
    val bg = LocalStageBackground.current
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val light = palette.isLight
    if (bg.showsPicture && !bg.accentLight) return this
    // Built once per size (drawWithCache); each frame only replays the cached brushes.
    return drawWithCache {
        val w = size.width
        val h = size.height
        val base: Brush?
        val plain: Color?
        val glows = mutableListOf<Glow>()
        when {
            bg.showsPicture -> {
                base = null; plain = null
                glows += glow(accent, 0.22f, Offset.Zero, 420.dp.toPx(), 360.dp.toPx(), 0.62f)
            }
            bg.style == BackgroundStyle.PLAIN -> {
                base = null; plain = if (light) palette.page else Color(0xFF070B0A)
                if (bg.accentLight) glows += glow(accent, if (light) 0.06f else 0.07f, Offset.Zero, 500.dp.toPx(), 400.dp.toPx(), 0.60f)
            }
            else -> {
                plain = null
                base = Brush.verticalGradient(
                    *if (light) {
                        arrayOf(0f to Color(0xFFF8FAF9), 0.55f to Color(0xFFF1F5F3), 1f to Color(0xFFEEF1F6))
                    } else {
                        arrayOf(0f to Color(0xFF0B1216), 0.55f to Color(0xFF06090B), 1f to Color(0xFF070A10))
                    },
                )
                glows += glow(Color(255, 160, 120), if (light) 0.10f else 0.07f, Offset(w * 0.85f, h * 0.22f), 320.dp.toPx(), 300.dp.toPx(), 0.60f)
                glows += glow(Color(96, 120, 255), if (light) 0.10f else 0.16f, Offset(w, h), 520.dp.toPx(), 520.dp.toPx(), 0.60f)
                if (bg.accentLight) glows += glow(accent, if (light) 0.16f else 0.22f, Offset.Zero, 420.dp.toPx(), 360.dp.toPx(), 0.62f)
            }
        }
        onDrawBehind {
            plain?.let { drawRect(it) }
            base?.let { drawRect(it) }
            glows.forEach { g ->
                scale(1f, g.ry / g.rx, pivot = g.center) {
                    drawRect(g.brush, topLeft = Offset(g.center.x - g.rx, g.center.y - g.rx), size = Size(2 * g.rx, 2 * g.rx))
                }
            }
        }
    }
}

/** CSS `radial-gradient(<rx> <ry> at <center>, color, transparent <end>)`: a circle squashed to an ellipse. */
private class Glow(val brush: Brush, val center: Offset, val rx: Float, val ry: Float)

private fun glow(color: Color, alpha: Float, center: Offset, rx: Float, ry: Float, end: Float) = Glow(
    Brush.radialGradient(0f to color.copy(alpha = alpha), end to color.copy(alpha = 0f), center = center, radius = rx),
    center, rx, ry,
)

/**
 * `.fx`, the current / open item: a horizontal accent sweep, a 1.5 dp inner ring in the focus colour and
 * a soft accent glow. In light the sweep is paler, the ring is the accent and the glow drops 6 dp.
 */
@Composable
fun Modifier.stageFx(radius: Dp): Modifier {
    val accent = stageAccent
    val light = LocalStagePalette.current.isLight
    val sweep = Brush.horizontalGradient(
        listOf(accent.accent.copy(alpha = if (light) 0.16f else 0.30f), accent.accent.copy(alpha = if (light) 0.05f else 0.12f)),
    )
    return drawWithCache {
        val r = radius.toPx()
        val outline = Path().apply { addRoundRect(RoundRect(size.toRect(), CornerRadius(r))) }
        onDrawBehind {
            if (light) {
                drawBoxShadow(outline, accent.accent.copy(alpha = 0.16f), 18.dp.toPx(), 6.dp.toPx())
            } else {
                drawBoxShadow(outline, accent.accent.copy(alpha = 0.22f), 24.dp.toPx())
            }
            drawRoundRect(sweep, cornerRadius = CornerRadius(r))
            drawInnerRing(accent.focus, 1.5.dp.toPx(), r)
        }
    }
}

/** `.selbar`: the selected item's 3 dp accent bar at the left, 12 dp in from top and bottom, glowing. */
@Composable
fun Modifier.stageSelectedBar(): Modifier {
    val accent = stageAccent.accent
    return drawBehind {
        val w = 3.dp.toPx()
        val inset = 12.dp.toPx()
        val bar = Path().apply {
            addRoundRect(RoundRect(0f, inset, w, size.height - inset, CornerRadius(2.dp.toPx())))
        }
        drawBoxShadow(bar, accent, 8.dp.toPx())
        drawPath(bar, accent)
    }
}

/** A plain `box-shadow` behind an element of [shape] (poster and button glows). */
fun Modifier.stageShadow(color: Color, blur: Dp, dy: Dp = 0.dp, shape: Shape = RectangleShape): Modifier =
    drawWithCache {
        val outline = outlinePath(shape)
        onDrawBehind { drawBoxShadow(outline, color, blur.toPx(), dy.toPx()) }
    }
