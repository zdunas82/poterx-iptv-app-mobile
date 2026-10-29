package tv.own.owntv.mobile.ui.stage

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tv.own.owntv.core.theme.BackgroundConfig

/*
 * Design tokens of Stage for phone. At UI Zoom 100% one design pixel is one dp, so every value is
 * the design's number unchanged: a 66 px channel row is `66.dp`, 14.5 px type is `14.5.sp`.
 */

/**
 * Stage's neutrals for one theme (`.ph` / `.ph.light`), the same shape as the television's. Accent,
 * on-accent and focus are the user's own ([LocalStageAccent]).
 */
@Immutable
data class StagePalette(
    val isLight: Boolean,
    val text: Color,
    val muted: Color,
    val dim: Color,
    /** `--body`: plots, descriptions, panel text; the second line of a current ("fx") row. */
    val body: Color,
    /** `--item`: idle menu items in sheets. */
    val item: Color,
    val tagText: Color,
    /** `--navtxt`: idle bottom-bar labels. */
    val navText: Color,
    /** `--ink-rgb`: every flat surface, at the alphas the dark theme uses for white. */
    val ink: Color,
    /** `--page`: the page colour, also the gap inside a chosen radio. */
    val page: Color,
    /** `--card-solid`: what a poster or still shows before its picture arrives. */
    val cardSolid: Color,
    val glassTint: Color,
    val glassOff: Color,
    /** `.scrim`: the wash behind a sheet. */
    val scrim: Color,
    /** `--wash-rgb`: what a backdrop photo fades into. */
    val wash: Color,
    /** `--wm-own`: the "own" of the wordmark. */
    val wordmarkOwn: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** Drop shadows: black in dark; a soft green-grey in light. */
    val shadow: Color,
) {
    /** The one idle control fill: tools, search fields, segmented, setting icons. */
    val controlFill: Color get() = ink.copy(alpha = 0.06f)

    companion object {
        // ok / warn / danger are the television's StageColors.
        val Dark = StagePalette(
            isLight = false,
            text = Color(0xFFF1F5F3),
            muted = Color(0xFFA7B3AE),
            dim = Color(0xFF6D7A75),
            body = Color(0xFFC9D3CF),
            item = Color(0xFFDBE4E0),
            tagText = Color(0xFFC8D3CE),
            navText = Color(0xFFC4CFCA),
            ink = Color.White,
            page = Color(0xFF05080A),
            cardSolid = Color(0xFF111A1E),
            glassTint = Color(18, 26, 30),
            glassOff = Color(0xFF121A1C),
            scrim = Color(2, 5, 6).copy(alpha = 0.55f),
            wash = Color(5, 8, 10),
            wordmarkOwn = Color(0xFFFFF6EE),
            ok = Color(0xFF7BE3A4),
            warn = Color(0xFFFFB86B),
            danger = Color(0xFFFF8A7A),
            shadow = Color.Black,
        )
        val Light = StagePalette(
            isLight = true,
            text = Color(0xFF0F1A16),
            muted = Color(0xFF4C5A54),
            dim = Color(0xFF7B8883),
            body = Color(0xFF2D3A35),
            item = Color(0xFF1C2824),
            tagText = Color(0xFF3D4B45),
            navText = Color(0xFF55635D),
            ink = Color(12, 24, 20),
            page = Color(0xFFF4F7F5),
            cardSolid = Color(0xFFE4EAE7),
            glassTint = Color.White,
            glassOff = Color.White,
            scrim = Color(20, 32, 28).copy(alpha = 0.28f),
            wash = Color(244, 247, 245),
            wordmarkOwn = Color(0xFF13201B),
            ok = Color(0xFF2B8A3E),
            warn = Color(0xFFB8640A),
            danger = Color(0xFFD23B2E),
            shadow = Color(20, 40, 32),
        )
    }
}

/** The palette of the resolved Theme (System / Dark / Light), provided by `MobileTheme`. */
val LocalStagePalette = staticCompositionLocalOf { StagePalette.Dark }

/**
 * The accent triple every Stage treatment is built from: the user's accent, the text on it, and the focus
 * highlight (Settings › Appearance; blank = the accent). In light the ring is the accent itself, because
 * the pale focus colour vanishes on white.
 */
@Immutable
data class StageAccent(val accent: Color, val onAccent: Color, val focus: Color)

/** The user's focus highlight, or null for "same as the accent"; provided by `MobileTheme`. */
val LocalFocusHighlight = staticCompositionLocalOf<Color?> { null }

val stageAccent: StageAccent
    @Composable
    @ReadOnlyComposable
    get() {
        val scheme = MaterialTheme.colorScheme
        val focus = if (LocalStagePalette.current.isLight) scheme.primary else LocalFocusHighlight.current ?: scheme.primary
        return StageAccent(scheme.primary, scheme.onPrimary, focus)
    }

/** Glass & background, resolved by core (Stage / Picture / Plain). Read here and drawn by the backdrop. */
val LocalStageBackground = staticCompositionLocalOf { BackgroundConfig() }

/** Corner radii. */
object StageRadii {
    val BottomBar = 26.dp
    val Sheet = 28.dp
    val Video = 20.dp
    val Row = 16.dp
    val Button = 16.dp
    val Search = 14.dp
    val MenuItem = 13.dp
    val Pill = 13.dp
    val Tool = 12.dp
    val Poster = 12.dp
    val Still = 12.dp
    val SettingIcon = 11.dp
    val Plate = 9.dp
    val Tag = 5.dp
}

/**
 * Stage type: a design size (px = sp) and weight in the user's main font (Plus Jakarta Sans by default).
 * The design's `line-height: 1.35`, with the leading split above and below the text as a browser does.
 */
@Composable
@ReadOnlyComposable
fun stageText(size: Float, weight: Int, letterSpacing: TextUnit = TextUnit.Unspecified, lineHeight: Float = 1.35f) = TextStyle(
    fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacing,
    lineHeight = (size * lineHeight).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** `.label` / `.grp` / `.shd`: 10.5 / 800, +0.12em, upper case (the caller upper-cases). */
@Composable
@ReadOnlyComposable
fun stageLabel() = stageText(10.5f, 800, 0.12.em)
