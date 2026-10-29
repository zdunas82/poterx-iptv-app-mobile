package tv.own.owntv.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing for a screen held at arm's length, not across a room. The TV app's values
 * are roughly double these: 48 dp screen padding becomes 16 (there is no overscan to avoid).
 */
object MobileDimens {
    val ScreenPaddingH = 16.dp

    val GapTiny = 4.dp
    val GapSmall = 8.dp
    val GapMedium = 16.dp
    val GapLarge = 24.dp

    val SheetCorner = 28.dp

    /** A row's own margin inside its group, narrower than a screen's because the group is inset. */
    val ListRowPaddingH = 12.dp

    /** The space between the parts of a sheet's scrolling body ([tv.own.owntv.mobile.ui.components.SheetScroll]). */
    val SheetGap = 9.dp
}
