package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A channel's logo, with something to show when there isn't one.
 *
 * The reason this exists rather than a bare [AsyncImage]: **a logo URL that exists is not a logo that
 * arrives.** Playlists are full of links that 404, time out or point at a host that is long gone, and
 * every copy of this in the app tested only whether the URL was null — so a broken link drew nothing
 * at all, and the channel list, the guide, the home rows and the player's own plate all showed blank
 * gaps where a logo should be. Only a missing URL fell back; a failed one did not.
 *
 * [fallback] is drawn for both cases, which is the point: to a user there is no difference between a
 * channel with no logo and a channel whose logo will not load.
 */
@Composable
fun ChannelLogoImage(
    url: String?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /**
     * The surface the logo sits on, for Stage's `.plate.ink`: a logo whose ink would vanish on it gets a
     * plate of the opposite luminance behind it (the TV's ChannelLogoTile rule). Null = never a plate.
     */
    plateOn: Color? = null,
) {
    // Keyed on the URL so zapping to a channel whose logo is fine tries again rather than inheriting
    // the previous channel's failure.
    var failed by remember(url) { mutableStateOf(url != null && (url in deadLogos || hostOf(url)?.let { it in deadHosts } == true)) }
    if (url.isNullOrBlank() || failed) {
        fallback()
        return
    }
    var ink by remember(url) { mutableIntStateOf(logoInk[url] ?: LOGO_INK_NONE) }
    var pending by remember(url) { mutableStateOf<Bitmap?>(null) }
    // Measuring reads every sampled pixel, so it runs off the main thread, once per logo per run.
    LaunchedEffect(url, pending) {
        val bitmap = pending ?: return@LaunchedEffect
        val measured = withContext(Dispatchers.Default) { if (bitmap.isRecycled) LOGO_INK_NONE else bitmap.measureInk() }
        if (logoInk.size > MAX_MEASURED_LOGOS) logoInk.clear()
        logoInk[url] = measured
        ink = measured
        pending = null
    }
    val plate = plateOn?.takeIf { ink != LOGO_INK_NONE }?.let { surface ->
        val s = surface.luminance()
        if (contrastRatio(Color(ink).luminance(), s) >= MIN_CONTRAST) null
        else if (s < 0.5f) LightPlate else DarkPlate
    }
    // Hardware bitmaps cannot be read back pixel by pixel; logos are tiny, so measuring ones give it up.
    val context = LocalPlatformContext.current
    val model = remember(url, plateOn != null, context) {
        if (plateOn == null) url else ImageRequest.Builder(context).data(url).allowHardware(false).build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = if (plate != null) modifier.background(plate) else modifier,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Error -> {
                    deadLogos += url
                    hostOf(url)?.let { deadHosts += it }
                    failed = true
                }
                is AsyncImagePainter.State.Success ->
                    if (plateOn != null && !logoInk.containsKey(url)) pending = (state.result.image as? BitmapImage)?.bitmap
                else -> Unit
            }
        },
    )
}

/** Mean ink colour per logo URL, measured once per run. [LOGO_INK_NONE] = leave it alone. */
private val logoInk = java.util.concurrent.ConcurrentHashMap<String, Int>()

private const val LOGO_INK_NONE = 0

/** Below this contrast between a logo's ink and the surface, the logo gets a plate. */
private const val MIN_CONTRAST = 3f

private const val MAX_MEASURED_LOGOS = 4096

/** `.plate.ink` on the dark page; the TV's dark plate on the light one. */
private val LightPlate = Color.White.copy(alpha = 0.92f)
private val DarkPlate = Color(0xFF161C24)

/**
 * The logo's mean colour, weighted by alpha so the transparent surround does not drag it towards black;
 * [LOGO_INK_NONE] for essentially opaque artwork, which brings its own background.
 */
private fun Bitmap.measureInk(): Int {
    val stepX = max(1, width / 48)
    val stepY = max(1, height / 48)
    var samples = 0
    var opaque = 0
    var alphaSum = 0.0
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            samples++
            if (alpha > 240) opaque++
            if (alpha > 0) {
                alphaSum += alpha.toDouble()
                r += ((pixel shr 16) and 0xFF).toDouble() * alpha
                g += ((pixel shr 8) and 0xFF).toDouble() * alpha
                b += (pixel and 0xFF).toDouble() * alpha
            }
            x += stepX
        }
        y += stepY
    }
    if (samples == 0 || alphaSum == 0.0 || opaque > samples * 0.9) return LOGO_INK_NONE
    return android.graphics.Color.rgb(
        (r / alphaSum).toInt().coerceIn(0, 255),
        (g / alphaSum).toInt().coerceIn(0, 255),
        (b / alphaSum).toInt().coerceIn(0, 255),
    )
}

/** WCAG contrast ratio between two relative luminances. */
private fun contrastRatio(a: Float, b: Float): Float {
    val lighter = max(a, b)
    val darker = if (lighter == a) b else a
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Logo URLs that have already failed once this run.
 *
 * Without this the fallback is correct but arrives *late*: a dead host is a connect timeout, so the
 * plate sits empty for the best part of half a minute — and because nothing remembers the outcome,
 * it does it again every time the list is scrolled back or the screen is reopened. A playlist whose
 * logo host has gone away therefore looks broken on every visit rather than the first.
 *
 * Deliberately in memory only, and never removed from: a URL that timed out on this launch is not
 * worth another twenty seconds on this launch. Restarting the app retries everything, which is the
 * right granularity for "the host came back".
 */
private val deadLogos = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

/**
 * Hosts that have already failed once this run — the same memory as [deadLogos], one level up.
 *
 * Per-URL is not enough, because a playlist's logos overwhelmingly come from one host, and when that
 * host is *gone* (measured: no ICMP, no TCP/80, a ten-second connect timeout per request) remembering
 * URLs one at a time means every channel scrolled to pays the ten seconds itself, for ever. One
 * failure is enough to know the rest are coming: the first channel waits, the whole list after it
 * falls back at once.
 *
 * Same lifetime and same reasoning as [deadLogos]: in memory, never removed from, cleared by a restart.
 */
private val deadHosts = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

private fun hostOf(url: String?): String? = runCatching { java.net.URI(url).host }.getOrNull()
