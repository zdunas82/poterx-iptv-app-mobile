package tv.own.owntv.mobile.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.parseAccentHex
import tv.own.owntv.mobile.R
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText

/** An HSV triple (h 0..360, s/v 0..1) as an uppercase "#RRGGBB" string. */
fun hsvToHex(h: Float, s: Float, v: Float): String {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    return "#%06X".format(java.util.Locale.ROOT, argb and 0xFFFFFF)
}

/** The rainbow across the hue bar, 0 to 360 degrees. */
private val HueSpectrum: List<Color> = (0..360 step 30).map {
    Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)))
}

/**
 * Pick a colour: the presets as 38 dp swatches with their names (and "Yours", the colour being made),
 * then a saturation/brightness square, the hue bar, the hex code, Cancel and Use this colour. The square and
 * the bar are dragged; a preset is one tap; a typed hex wins when it is valid.
 *
 * @param presets Swatches as "#RRGGBB". A tap applies one ([onPickPreset] with its index, else [onPick]) and closes.
 * @param presetNames Their names under them; with names the last swatch is "Yours".
 * @param initial The colour to open on, "#RRGGBB" or blank for the default seed.
 */
@Composable
fun ColorPickerSheet(
    title: String,
    presets: List<String>,
    initial: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    eyebrow: String? = null,
    presetNames: List<String> = emptyList(),
    onPickPreset: ((Int) -> Unit)? = null,
) {
    // Seeded from the colour in force, so opening the picker starts where the setting already is.
    val seed = remember(initial) {
        FloatArray(3).also { out ->
            val argb = parseAccentHex(initial)?.toInt() ?: DEFAULT_SEED
            android.graphics.Color.colorToHSV(argb, out)
        }
    }
    // Keyed on the seed: the setting arrives a frame after the sheet opens, and the picker must move to it
    // rather than stay on the default (it opened on Teal over a custom green).
    var hue by remember(seed) { mutableFloatStateOf(seed[0]) }
    var sat by remember(seed) { mutableFloatStateOf(seed[1]) }
    var value by remember(seed) { mutableFloatStateOf(seed[2]) }
    var hexInput by remember(seed) { mutableStateOf(hsvToHex(seed[0], seed[1], seed[2])) }
    var hexError by remember { mutableStateOf(false) }
    // The square is dragged inside a column that also scrolls; without this the scroller wins the touch.
    var dragging by remember { mutableStateOf(false) }
    val palette = LocalStagePalette.current

    val picked = hsvToHex(hue, sat, value)
    fun syncHex() {
        hexInput = picked
        hexError = false
    }

    MobileBottomSheet(onDismissRequest = onDismiss) {
        StageSheetHeader(title, eyebrow = eyebrow)
        Column(Modifier.heightIn(max = sheetListHeight()).verticalScroll(rememberScrollState(), enabled = !dragging)) {
            if (presets.isNotEmpty()) {
                StageGroupLabel(stringResource(R.string.settings_presets))
                val named = presetNames.size == presets.size
                val pickedIsPreset = presets.any { it.equals(picked, true) }
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    presets.forEachIndexed { i, hex ->
                        val swatch = parseAccentHex(hex)?.let { Color(it) } ?: return@forEachIndexed
                        Swatch(swatch, presetNames.getOrNull(i), ringed = hex.equals(picked, true)) {
                            if (onPickPreset != null) onPickPreset(i) else onPick(hex)
                            onDismiss()
                        }
                    }
                    if (named) {
                        Swatch(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))),
                            stringResource(R.string.settings_accent_yours),
                            ringed = !pickedIsPreset,
                        ) {}
                    }
                }
            }
            Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp)) {
                SatValSquare(hue = hue, sat = sat, value = value, onDragging = { dragging = it }) { s, v -> sat = s; value = v; syncHex() }
            }
            Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
                HueBar(hue = hue, onDragging = { dragging = it }) { hue = it; syncHex() }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageSearchField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(7); hexError = false },
                    placeholder = HEX_PLACEHOLDER,
                    icon = null,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    modifier = Modifier.width(112.dp),
                )
                Spacer(Modifier.weight(1f))
                StageButton(onClick = onDismiss, text = stringResource(R.string.common_cancel), small = true)
                StageButton(
                    onClick = {
                        // The typed code wins when it is valid; an unparseable one says so instead.
                        val typed = "#" + hexInput.trim().removePrefix("#").uppercase()
                        if (parseAccentHex(typed) != null) {
                            onPick(typed)
                            onDismiss()
                        } else {
                            hexError = true
                        }
                    },
                    text = stringResource(R.string.settings_use_color),
                    style = StageButtonStyle.PRIMARY,
                    small = true,
                )
            }
            if (hexError) {
                Text(
                    stringResource(R.string.settings_hex_error),
                    style = stageText(11.5f, 600),
                    color = palette.danger,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}

/** The hex field's example, a colour code rather than words. */
private const val HEX_PLACEHOLDER = "#52DBC8"

/** A 38 dp preset with its name under it; the chosen one ringed in white with a page-coloured gap. */
@Composable
private fun Swatch(color: Color, name: String?, ringed: Boolean, onClick: () -> Unit) {
    val palette = LocalStagePalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(38.dp)
                .drawBehind {
                    if (ringed) {
                        drawCircle(Color.White, size.minDimension / 2f + 4.dp.toPx())
                        drawCircle(palette.page, size.minDimension / 2f + 2.dp.toPx())
                    }
                    drawCircle(color, size.minDimension / 2f)
                }
                .pointerInput(color) { detectTapGestures { onClick() } },
        )
        name?.let { Text(it, style = stageText(10.5f, 700), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)) }
    }
}

/** The default seed when nothing is set yet — the app's own teal. */
private const val DEFAULT_SEED = 0xFF52DBC8.toInt()

/** The rainbow strip: drag anywhere along it to set the hue. */
@Composable
private fun HueBar(hue: Float, onDragging: (Boolean) -> Unit, onHue: (Float) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient(HueSpectrum)))
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val set = { x: Float -> onHue((x / widthPx).coerceIn(0f, 1f) * 360f) }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx) {
                    detectTapGestures { set(it.x) }
                }
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { onDragging(true) },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                    ) { change, _ -> change.consume(); set(change.position.x) }
                },
        )
        Box(
            modifier = Modifier
                .offset(x = maxWidth * (hue / 360f) - 4.dp)
                .align(Alignment.CenterStart)
                .size(8.dp, 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White),
        )
    }
}

/** Saturation across, brightness up: drag the dot to anywhere in the square. */
@Composable
private fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onDragging: (Boolean) -> Unit,
    onChange: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))))),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // Black over the hue wash: across is saturation, down is darkness — the arrangement every
        // colour picker uses, so the dot ends up where a user expects to find it.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        val set = { x: Float, y: Float ->
            onChange((x / widthPx).coerceIn(0f, 1f), 1f - (y / heightPx).coerceIn(0f, 1f))
        }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures { set(it.x, it.y) }
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { onDragging(true) },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                    ) { change, _ ->
                        change.consume()
                        set(change.position.x, change.position.y)
                    }
                },
        )
        Box(
            modifier = Modifier
                .offset(
                    x = maxWidth * sat - 8.dp,
                    y = maxHeight * (1f - value) - 8.dp,
                )
                .size(16.dp)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}
