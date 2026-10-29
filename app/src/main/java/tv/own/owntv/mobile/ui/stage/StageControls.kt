package tv.own.owntv.mobile.ui.stage

import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion

/*
 * Stage's small controls (`phone.css`): buttons, pills, tools, segmented, tags, the playlist mark, switch,
 * stepper, radio, the search field, progress, the LIVE badge and spec chips. Touch has no focus ladder:
 * idle = nothing, selected / on = accent, pressed = filled accent. Compose widens every tap target under
 * 48 dp by itself, so the drawn sizes stay the design's.
 */

/** A Stage glyph at a design icon size (`1em` of its element). */
@Composable
fun StageIcon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) =
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))

/** PLAIN: no fill until pressed (the wizard's Back, `btn sm` with `background:none`). */
enum class StageButtonStyle { PRIMARY, SECONDARY, DANGER, PLAIN }

/**
 * `.btn`: 44 dp (34 [small]), the ink fill; PRIMARY is the accent with its glow, DANGER the danger text.
 * No [text] makes the round icon button (`.btn.round`) — unless there is a muted [trailing] count after the
 * icon (Home's "versions 3", 14 dp sides). Pressed fills a secondary button with the accent.
 */
@Composable
fun StageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    style: StageButtonStyle = StageButtonStyle.SECONDARY,
    small: Boolean = false,
    enabled: Boolean = true,
    trailing: String? = null,
    /** After the text (the wizard's "Continue ›"). */
    trailingIcon: ImageVector? = null,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = style == StageButtonStyle.PRIMARY || pressed
    val radius = if (small) 12.dp else StageRadii.Button
    val shape = RoundedCornerShape(radius)
    val fg = when {
        filled -> accent.onAccent
        style == StageButtonStyle.DANGER -> palette.danger
        else -> palette.text
    }
    Row(
        modifier
            // Not available yet (a form not filled in): the whole button at 45 %, no glow — as a dimmed menu item.
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .then(
                if (style == StageButtonStyle.PRIMARY && enabled) {
                    Modifier.stageShadow(
                        accent.accent.copy(alpha = if (palette.isLight) 0.3f else 0.4f),
                        if (palette.isLight) 16.dp else 24.dp,
                        if (palette.isLight) 6.dp else 8.dp,
                        shape,
                    )
                } else Modifier,
            )
            .height(if (small) 34.dp else 44.dp)
            .then(if (text == null && trailing == null) Modifier.width(if (small) 34.dp else 44.dp) else Modifier)
            .clip(shape)
            .background(
                when {
                    filled -> accent.accent
                    style == StageButtonStyle.PLAIN -> Color.Transparent
                    else -> palette.ink.copy(alpha = 0.08f)
                },
            )
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = when {
                    text != null -> if (small) 12.dp else 18.dp
                    trailing != null -> 14.dp
                    else -> 0.dp
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { StageIcon(it, 16.dp, fg) }
        text?.let {
            Text(it, style = stageText(if (small) 12.5f else 14f, 700), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // On a filled button the trailing part is the text colour at 75 %, 600 (the series page's "· 12 min left").
        trailing?.let {
            Text(
                it,
                style = stageText(if (small) 12.5f else 14f, if (filled) 600 else 700),
                color = if (filled) fg.copy(alpha = 0.75f) else palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingIcon?.let { StageIcon(it, 14.dp, fg) }
    }
}

/**
 * `.pill`: a 36 dp glass pill of the top cluster — a leading accent icon, the text, an optional trailing
 * accent icon (the chevron of "All playlists ⌄") and an optional playlist [mark]. No [text] makes the
 * 36 dp icon pill, its glyph in the text colour.
 */
@Composable
fun StagePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    mark: PlaylistMark? = null,
    contentDescription: String? = null,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val shape = RoundedCornerShape(StageRadii.Pill)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A lone glyph is the 36 dp square; a mark or a chevron without text is a short pill.
    val square = text == null && mark == null && trailingIcon == null
    Row(
        modifier
            .height(36.dp)
            .then(if (square) Modifier.width(36.dp) else Modifier)
            .then(if (pressed) Modifier.clip(shape).background(accent.accent) else Modifier.stageGlass(shape))
            .clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = if (square) 0.dp else if (text == null) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconTint = if (pressed) accent.onAccent else if (square) palette.text else accent.accent
        icon?.let { StageIcon(it, if (square) 17.dp else 15.dp, iconTint) }
        mark?.let { StagePlaylistMark(it) }
        text?.let {
            Text(it, style = stageText(13f, 700), color = if (pressed) accent.onAccent else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailingIcon?.let { StageIcon(it, 15.dp, iconTint) }
    }
}

/**
 * `.tool`: a quiet 34 dp text button of the tool row under a title — muted [label], then the [value] in
 * the text colour ("Sort **Provider**"). [on] is the accent tint with its ring.
 */
@Composable
fun StageTool(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    on: Boolean = false,
    /** After the text: the ⌄ of a tool that opens choices ("Today ⌄"). */
    trailingIcon: ImageVector? = null,
    contentDescription: String? = null,
    /** History's clear tool: the danger colour. */
    danger: Boolean = false,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(StageRadii.Tool)
    val fill = when {
        pressed -> accent.accent
        on -> accent.accent.copy(alpha = 0.18f)
        else -> palette.controlFill
    }
    val fg = if (pressed) accent.onAccent else if (on) accent.accent else if (danger) palette.danger else palette.muted
    val strong = if (pressed) accent.onAccent else if (on) accent.accent else palette.text
    Row(
        modifier
            .height(34.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (on && !pressed) {
                    Modifier.drawBehind { drawInnerRing(accent.accent.copy(alpha = 0.55f), 1.2.dp.toPx(), StageRadii.Tool.toPx()) }
                } else Modifier,
            )
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { StageIcon(it, 14.dp, fg) }
        label?.let { Text(it, style = stageText(12.5f, 700), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        value?.let { Text(it, style = stageText(12.5f, 700), color = strong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
        trailingIcon?.let { StageIcon(it, 14.dp, fg) }
    }
}

/**
 * `.seg`: options on a 3 dp-padded ink track; the chosen one a lighter chip (white with a shadow in light).
 * [iconOnly] draws only the [icons] (Movies' Grid | List) and gives each its option as the spoken label.
 */
@Composable
fun StageSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    iconOnly: Boolean = false,
    /** Each option takes an equal share of the width (the add form's Xtream · M3U · Stalker). */
    fill: Boolean = false,
) {
    val palette = LocalStagePalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.controlFill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val chip = RoundedCornerShape(9.dp)
            Row(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .height(28.dp)
                    .then(
                        when {
                            !on -> Modifier
                            palette.isLight -> Modifier.stageShadow(palette.shadow.copy(alpha = 0.16f), 3.dp, 1.dp, chip).background(Color.White, chip)
                            else -> Modifier.background(palette.ink.copy(alpha = 0.14f), chip)
                        },
                    )
                    .clip(chip)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, if (fill) Alignment.CenterHorizontally else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) palette.text else palette.muted
                icons.getOrNull(i)?.let { StageIcon(it, 14.dp, fg) }
                if (!iconOnly) Text(label, style = stageText(12f, 700), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** `.row2`: a sheet's choices as equal 40 dp buttons; the chosen one in the accent with its ring (Grid · List). */
@Composable
fun StageChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) accent.copy(alpha = 0.18f) else palette.controlFill)
                    .then(if (on) Modifier.drawBehind { drawInnerRing(accent.copy(alpha = 0.6f), 1.2.dp.toPx(), 12.dp.toPx()) } else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = stageText(13f, 700), color = if (on) accent else palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A size setting drawn as the 4 dp `.prog` bar: [fraction] 0 at the left … 1 at the right; tap or drag. */
@Composable
fun StageSizeBar(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    fun at(x: Float, width: Int) = (x / width).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.x, size.width)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange(at(change.position.x, size.width)) } },
        contentAlignment = Alignment.Center,
    ) {
        StageProgress(fraction, Modifier.fillMaxWidth())
    }
}

enum class StageTagTone { PLAIN, ACCENT, WARN }

/** `.tag`: 9.5 / 800 caps-sized text on a 5 dp chip — ink, accent, or warn; an [icon] leads it (More's "⚠ Never"). */
@Composable
fun StageTag(text: String, modifier: Modifier = Modifier, tone: StageTagTone = StageTagTone.PLAIN, icon: ImageVector? = null) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val (fill, fg) = when (tone) {
        StageTagTone.PLAIN -> palette.ink.copy(alpha = 0.08f) to palette.tagText
        StageTagTone.ACCENT -> accent.copy(alpha = 0.2f) to accent
        StageTagTone.WARN -> (if (palette.isLight) Color(184, 100, 10).copy(alpha = 0.12f) else Color(255, 179, 92).copy(alpha = 0.16f)) to palette.warn
    }
    Row(
        modifier.clip(RoundedCornerShape(StageRadii.Tag)).background(fill).padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { StageIcon(it, 13.dp, fg) }
        Text(text, style = stageText(9.5f, 800, 0.05.em), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.rec`: the RECOMMENDED tag of a choice sheet. */
@Composable
fun StageRecommended(modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    Text(
        stringResource(R.string.settings_panel_recommended).uppercase(),
        style = stageText(9f, 800, 0.06.em),
        color = accent,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(StageRadii.Tag)).background(accent.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** `.pm`: which playlist a row comes from, outlined in its colour; darkened in light to read on white. */
@Composable
fun StagePlaylistMark(mark: PlaylistMark, modifier: Modifier = Modifier, tint: Color? = null) {
    val base = Color(mark.argb)
    val color = tint ?: if (LocalStagePalette.current.isLight) Color(base.red * 0.72f, base.green * 0.72f, base.blue * 0.72f) else base
    Box(
        modifier
            .height(17.dp)
            .defaultMinSize(minWidth = 24.dp)
            .drawBehind { drawInnerRing(color, 1.2.dp.toPx(), StageRadii.Tag.toPx()) }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark.text, style = stageText(9.5f, 800, 0.04.em, lineHeight = 1f), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.sw`: 40 × 24, the knob sliding with the phone's spring (snaps with Animations off). A picture: the row toggles. */
@Composable
fun StageSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    val knob by animateDpAsState(if (checked) 19.dp else 3.dp, LocalMobileMotion.current.fast(), label = "stageSwitch")
    val knobColor = if (checked || palette.isLight) Color.White else Color(0xFFCFD8D4)
    Box(
        modifier
            .size(40.dp, 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) accent else palette.ink.copy(alpha = 0.16f)),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.roundToPx(), 3.dp.roundToPx()) }
                .stageShadow(Color.Black.copy(alpha = 0.25f), 3.dp, 1.dp, CircleShape)
                .size(18.dp)
                .background(knobColor, CircleShape),
        )
    }
}

/** `.step`: − value +, two 28 dp ink keys either side of a 44 dp value. */
@Composable
fun StageStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val palette = LocalStagePalette.current
    @Composable
    fun Key(text: String, enabled: Boolean, onClick: () -> Unit) = Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(palette.ink.copy(alpha = 0.08f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = stageText(15f, 700, lineHeight = 1f), color = if (enabled) palette.text else palette.dim)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Key(stringResource(R.string.common_stepper_minus), minusEnabled, onMinus)
        Text(
            value,
            style = stageText(13f, 800),
            color = palette.text,
            textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Key(stringResource(R.string.common_stepper_plus), plusEnabled, onPlus)
    }
}

/** `.radio`: an 18 dp ring; chosen = accent ring, a page-coloured gap and an accent dot. */
@Composable
fun StageRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Box(
        modifier.size(18.dp).drawBehind {
            val r = size.minDimension / 2f
            if (selected) {
                drawCircle(accent, r)
                drawCircle(palette.page, r - 1.8.dp.toPx())
                drawCircle(accent, r - 5.dp.toPx())
            } else {
                drawCircle(palette.muted, r - 0.9.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
            }
        },
    )
}

/**
 * `.srch`: the 42 dp search field (36 / 12.5 type [inline]); the dim placeholder, the text colour once typing, and the
 * focus ring while it has the keyboard.
 */
@Composable
fun StageSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    onSearch: (() -> Unit)? = null,
    /** Null makes it the plain Stage text field of a name prompt (Rename, New category, Set TMDB name). */
    icon: ImageVector? = MobileIcons.Search,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** [onSearch] runs on this key as well — Done on a name prompt. */
    imeAction: ImeAction = ImeAction.Search,
    /** A backup password: masked, and the keyboard offers no suggestions. */
    password: Boolean = false,
    /** Search's ✕ at the end while there is text; [clearLabel] is its spoken name. */
    onClear: (() -> Unit)? = null,
    clearLabel: String? = null,
    /** At the end of the field, 11 dim: Settings search's "5 results". */
    trailingText: String? = null,
    /** A danger ring: the value cannot be used as it is (a wrong PIN, a name already taken). */
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    var focused by remember { mutableStateOf(false) }
    // Inside a scrolling sheet the keyboard can cover the field it opened for; focus brings it back into view.
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // The inline field (a tool row's) is 36 dp with 12.5 type, the page's 42 dp with 13.5.
    val style = stageText(if (inline) 12.5f else 13.5f, 500)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = style.copy(color = if (enabled) palette.text else palette.dim),
        cursorBrush = SolidColor(accent.accent),
        // A password gets the password keyboard, unless it asks for its own (a PIN's number pad): the text
        // keyboard auto-corrects and capitalises, which turns a correct password into a wrong one.
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password && keyboardType == KeyboardType.Text) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = if (password) false else null,
        ),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch?.invoke() },
            onDone = { onSearch?.invoke() },
        ),
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) scope.launch { runCatching { bringIntoView.bringIntoView() } }
            },
        decorationBox = { field ->
            val ring = when {
                isError -> palette.danger
                focused -> accent.focus
                else -> null
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (inline) 36.dp else 42.dp)
                    .clip(RoundedCornerShape(StageRadii.Search))
                    .background(palette.controlFill)
                    .then(
                        if (ring != null) {
                            Modifier.drawBehind { drawInnerRing(ring, 1.5.dp.toPx(), StageRadii.Search.toPx()) }
                        } else Modifier,
                    )
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let { StageIcon(it, 16.dp, if (focused) palette.text else palette.dim) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
                trailingText?.let { Text(it, style = stageText(11f, 500), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (onClear != null && value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clickable(role = Role.Button, onClickLabel = clearLabel, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) { StageIcon(MobileIcons.Close, 16.dp, palette.text) }
                }
            }
        },
    )
}

/**
 * A form field inside a sheet: the [label] above (11.5 / 600 muted), the 42 dp [StageSearchField] without its
 * glyph, and [supportingText] under it — in the danger colour when [isError]. A field on a page is a
 * `StageTextRow` instead. Done runs [onImeDone], then puts the keyboard away; Next moves to the next field.
 */
@Composable
fun StageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    onImeDone: (() -> Unit)? = null,
) {
    val palette = LocalStagePalette.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = stageText(11.5f, 600), color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        StageSearchField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            icon = null,
            password = password,
            keyboardType = keyboardType,
            imeAction = imeAction,
            isError = isError,
            enabled = enabled,
            onSearch = {
                onImeDone?.invoke()
                keyboard?.hide()
                focusManager.clearFocus()
            },
        )
        supportingText?.let {
            Text(
                it,
                style = stageText(11.5f, 500, lineHeight = 1.3f),
                color = if (isError) palette.danger else palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/**
 * Progress. `.prog` (default): 4 dp on ink 12%, the fill an accent → focus sweep (plain accent in light).
 * [thin] is the channel row's `.pb`: 3 dp on ink 10%, a plain accent fill.
 */
@Composable
fun StageProgress(fraction: Float, modifier: Modifier = Modifier, thin: Boolean = false) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val sweep = !thin && !palette.isLight
    Box(
        modifier
            .fillMaxWidth()
            .height(if (thin) 3.dp else 4.dp)
            .drawBehind {
                val r = CornerRadius(2.dp.toPx())
                drawRoundRect(palette.ink.copy(alpha = if (thin) 0.10f else 0.12f), cornerRadius = r)
                val w = size.width * fraction.coerceIn(0f, 1f)
                if (w <= 0f) return@drawBehind
                val fill = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, size.height, r)) }
                if (sweep) {
                    drawPath(fill, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.accent, accent.focus), endX = w))
                } else {
                    drawPath(fill, accent.accent)
                }
            },
    )
}

/** `.live`: the LIVE badge over video — a glowing red dot and [text] on black 55%, dark in both themes. */
@Composable
fun StageLiveBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val red = Color(0xFFFF4D4D)
        Box(Modifier.stageShadow(red, 8.dp, shape = CircleShape).size(6.dp).background(red, CircleShape))
        Text(text, style = stageText(10.5f, 800, 0.08.em, lineHeight = 1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.spec`: the stream's spec chips over video (4K, HDR, 5.1), dark in both themes. */
@Composable
fun StageSpecChips(specs: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        specs.forEach {
            Text(
                it,
                style = stageText(9.5f, 800, 0.05.em, lineHeight = 1f),
                color = Color(0xFFE7EFEC),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** `.label`: a small upper-case heading in dim (`.grp` in sheets and `.shd` on pages build on it). */
@Composable
fun StageLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalStagePalette.current.dim) =
    Text(text.uppercase(), style = stageLabel(), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

/** `.eyebrow`: the trend glyph and the rank line, 12.5 / 700 in the accent. */
@Composable
fun StageEyebrow(text: String, modifier: Modifier = Modifier, icon: ImageVector = MobileIcons.Trend) {
    val accent = stageAccent.accent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        StageIcon(icon, 12.5.dp, accent)
        Text(text, style = stageText(12.5f, 700), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `.tabs` (Downloads, Search): each tab its label (14 / 800 open, 14 / 700 muted otherwise) and
 * count (11 dim), the 3 dp accent line under each one in [selected] (Downloads: the open tab; Search's filter
 * buttons: any number on), a 1 dp rule under them all; [trailing] at the end.
 */
@Composable
fun StageTabs(
    tabs: List<Pair<String, Int>>,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    Row(
        modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawRect(palette.ink.copy(alpha = 0.1f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
            },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        tabs.forEachIndexed { i, (label, n) ->
            val on = i in selected
            val interaction = remember { MutableInteractionSource() }
            Column(Modifier.clickable(interaction, indication = null, role = Role.Tab, onClick = { onSelect(i) }).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(label)
                        append(" ")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = palette.dim, fontSize = stageText(11f, 700).fontSize)) {
                            append(java.text.NumberFormat.getIntegerInstance().format(n))
                        }
                    },
                    style = stageText(14f, if (on) 800 else 700),
                    color = if (on) palette.text else palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) accent else Color.Transparent))
            }
        }
        trailing()
    }
}
