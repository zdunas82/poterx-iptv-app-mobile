package tv.own.owntv.mobile.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import tv.own.owntv.core.metadata.CastMember
import tv.own.owntv.core.metadata.MetadataImages
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.mobile.ui.components.MobileIcons

/** The bottom watch-progress line of a poster or still (`.wc`): 4 dp, black 50% under an accent fill. */
@Composable
private fun BoxScope.WatchProgress(fraction: Float) {
    val accent = stageAccent.accent
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .drawWithContent { drawRect(accent, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f))) },
    )
}

/**
 * `.pc`: a 2:3 poster (12 dp corners, its drop shadow) with the rating chip top left, the watched ✓ or the
 * playlist [mark] top right, the watch progress along the bottom, then the title (11.5 / 600 muted) and an
 * optional second line. [selected] is the open item: a 2 dp focus ring, an accent halo and glow, the title
 * in the text colour. Pictures keep their own look in light; only the shadow softens.
 */
@Composable
fun StagePoster(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: String? = null,
    mark: PlaylistMark? = null,
    watched: Boolean = false,
    progress: Float? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val light = palette.isLight
    val shape = RoundedCornerShape(StageRadii.Poster)
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.stageClickable(interaction, onClick, onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (selected) {
                        Modifier
                            .stageShadow(accent.accent.copy(alpha = if (light) 0.25f else 0.3f), if (light) 26.dp else 34.dp, if (light) 12.dp else 14.dp, shape)
                            .drawWithContent {
                                drawContent()
                                val r = StageRadii.Poster.toPx()
                                drawOuterRing(accent.accent.copy(alpha = if (light) 0.22f else 0.3f), 3.dp.toPx(), r, inset = 2.dp.toPx())
                                drawOuterRing(accent.focus, 2.dp.toPx(), r)
                            }
                    } else {
                        Modifier.stageShadow(palette.shadow.copy(alpha = if (light) 0.16f else 0.45f), if (light) 16.dp else 20.dp, if (light) 6.dp else 8.dp, shape)
                    },
                )
                .clip(shape)
                .background(palette.cardSolid),
        ) {
            AsyncImage(imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // The rating chip top left, ✓ or the playlist mark top right — the right one left out when the two
            // would touch (four posters a row, a large font), never drawn over the rating.
            Layout(
                content = {
                    rating?.let { StageRatingChip(it) }
                    when {
                        watched -> Box(
                            Modifier.size(22.dp).background(accent.accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { StageIcon(MobileIcons.Check, 13.dp, accent.onAccent) }
                        mark != null -> StagePlaylistMark(mark)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(5.dp),
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val placeables = measurables.map { it.measure(loose) }
                val left = placeables.getOrNull(0).takeIf { rating != null }
                val right = (if (rating != null) placeables.getOrNull(1) else placeables.getOrNull(0))
                    ?.takeIf { (left?.width ?: 0) + it.width + 4.dp.roundToPx() <= constraints.maxWidth }
                layout(constraints.maxWidth, maxOf(left?.height ?: 0, right?.height ?: 0)) {
                    left?.place(0, 0)
                    right?.place(constraints.maxWidth - right.width, 0)
                }
            }
            progress?.let { WatchProgress(it) }
        }
        Text(
            title,
            style = stageText(11.5f, if (selected) 700 else 600),
            color = if (selected) palette.text else palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        subtitle?.let {
            Text(it, style = stageText(10.5f, 600), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/** `.rt`: the rating chip on a poster — a gold star and the score on near-black, dark in both themes. */
@Composable
fun StageRatingChip(rating: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(20.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(8, 12, 14).copy(alpha = 0.92f))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageIcon(MobileIcons.Star, 10.dp, Color(0xFFFFCC4D))
        Text(rating, style = stageText(10.5f, 800, lineHeight = 1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The cast (`cast()`): a row of 56 dp columns 12 apart — the TMDB photo in a 46 dp circle with a
 * 1.5 dp ink rim, the name under it (10.5 / 600 muted, two lines). Home's ⓘ sheet and the movie page.
 */
@Composable
fun StageCastRow(cast: List<CastMember>, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val palette = LocalStagePalette.current
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = contentPadding) {
        items(cast) { member ->
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    MetadataImages.profile(member.profilePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.6f),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(palette.cardSolid)
                        .border(1.5.dp, palette.ink.copy(alpha = 0.14f), CircleShape),
                )
                Text(
                    member.name,
                    style = stageText(10.5f, 600, lineHeight = 1.2f),
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/**
 * `.still`: a 16:9 picture (12 dp corners, [radius] where a row draws it smaller) — Keep watching, episodes —
 * with an optional watch progress and anything the caller lays [over] it. [dimmed] is a watched episode
 * (`saturate(.55) brightness(.72)`). Light gives it a soft shadow; dark has none.
 */
@Composable
fun StageStill(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    radius: Dp = StageRadii.Still,
    dimmed: Boolean = false,
    over: @Composable BoxScope.() -> Unit = {},
) {
    val palette = LocalStagePalette.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .then(if (palette.isLight) Modifier.stageShadow(palette.shadow.copy(alpha = 0.16f), 16.dp, 6.dp, shape) else Modifier)
            .clip(shape)
            .background(palette.cardSolid),
    ) {
        AsyncImage(
            imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (dimmed) WatchedFilter else null,
            modifier = Modifier.fillMaxSize(),
        )
        progress?.let { WatchProgress(it) }
        over()
    }
}

/** A watched episode's still: saturation 55 %, then brightness 72 %. */
private val WatchedFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.55f)
        timesAssign(ColorMatrix().apply { setToScale(0.72f, 0.72f, 0.72f, 1f) })
    },
)
