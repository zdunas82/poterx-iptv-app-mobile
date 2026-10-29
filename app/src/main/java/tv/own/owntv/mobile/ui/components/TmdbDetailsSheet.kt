package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.metadata.CastMember
import tv.own.owntv.core.metadata.MetadataCast
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageCastRow
import tv.own.owntv.mobile.ui.stage.StageGroupLabel
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.NumberFormat

/** Provider and TMDB already merged: the sheet shows what it is given and asks nothing. */
data class MediaDetails(
    val title: String,
    val subtitle: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val metaLine: String = "",
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    val cast: List<CastMember> = emptyList(),
)

/**
 * Everything TMDB knows about one title, as a sheet.
 *
 * The TV app shows this as a centred window because a remote cannot dismiss anything else; here it is
 * the same sheet as every other long-press follow-up, so swiping it away is the way out and there is
 * no "press Back" line to print.
 */
@Composable
fun TmdbDetailsSheet(details: MediaDetails, onDismiss: () -> Unit) {
    val palette = LocalStagePalette.current
    // Stage sheet, Home's ⓘ sheet shape: the poster (or an episode's still) beside the title, its line,
    // the meta line and the genres in the accent; then OVERVIEW and the plot, CAST and the cast row.
    MobileBottomSheet(onDismissRequest = onDismiss, title = details.title) {
        Column(Modifier.heightIn(max = detailsHeight()).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val art = details.posterUrl ?: details.backdropUrl
                if (!art.isNullOrBlank()) {
                    val still = details.posterUrl == null
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .then(if (still) Modifier.size(112.dp, 63.dp) else Modifier.size(70.dp, 105.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.cardSolid),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(details.title, style = stageText(19f, 800), color = palette.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    details.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = stageText(12f, 600), color = palette.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (details.metaLine.isNotBlank()) {
                        Text(details.metaLine, style = stageText(12f, 600), color = palette.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (details.genres.isNotEmpty()) {
                        Text(
                            details.genres.joinToString(stringResource(R.string.content_metadata_separator)),
                            style = stageText(12f, 700),
                            color = stageAccent.accent,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            details.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                StageGroupLabel(stringResource(R.string.content_media_overview))
                Text(plot, style = stageText(12.5f, 500, lineHeight = 1.5f), color = palette.body, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
            }
            if (details.cast.isNotEmpty()) {
                StageGroupLabel(stringResource(R.string.content_media_cast))
                StageCastRow(details.cast, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            }
        }
    }
}

/** A film, merged. [tmdbWins] is the user's source-precedence setting. */
@Composable
fun movieDetails(movie: MovieEntity, meta: MetadataCacheEntity?, tmdbWins: Boolean): MediaDetails {
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = MetadataImages.poster(meta?.posterPath)
    val year = if (tmdbWins) meta?.year ?: movie.year else movie.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.positive() ?: movie.rating?.positive()
    else movie.rating?.positive() ?: meta?.rating?.positive()
    val duration = movie.durationSecs?.takeIf { it > 0 }?.let { secs ->
        val hours = secs / 3600
        val minutes = (secs % 3600) / 60
        if (hours > 0) stringResource(R.string.content_duration_hours, hours, minutes)
        else stringResource(R.string.content_duration_minutes, minutes)
    }
    return MediaDetails(
        title = movie.name,
        // Providers carry no backdrop of their own for most films, so TMDB's is the usual one.
        backdropUrl = MetadataImages.backdrop(meta?.backdropPath) ?: movie.backdropUrl?.takeIf { it.isNotBlank() },
        posterUrl = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster,
        metaLine = metaLine(year, rating, duration),
        genres = jsonList(meta?.genresJson),
        plot = if (tmdbWins) meta?.overview ?: movie.plot else movie.plot?.takeIf { it.isNotBlank() } ?: meta?.overview,
        cast = MetadataCast.parse(meta?.castJson),
    )
}

/** A show, merged. */
@Composable
fun seriesDetails(show: SeriesEntity, meta: MetadataCacheEntity?, tmdbWins: Boolean): MediaDetails {
    val providerPoster = show.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = MetadataImages.poster(meta?.posterPath)
    val year = if (tmdbWins) meta?.year ?: show.year else show.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.positive() ?: show.rating?.positive()
    else show.rating?.positive() ?: meta?.rating?.positive()
    return MediaDetails(
        title = show.name,
        backdropUrl = MetadataImages.backdrop(meta?.backdropPath) ?: show.backdropUrl?.takeIf { it.isNotBlank() },
        posterUrl = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster,
        metaLine = metaLine(year, rating, null),
        genres = jsonList(meta?.genresJson),
        plot = if (tmdbWins) meta?.overview ?: show.plot else show.plot?.takeIf { it.isNotBlank() } ?: meta?.overview,
        cast = MetadataCast.parse(meta?.castJson),
    )
}

/** One episode. The still is the hero; an episode has no 2:3 poster of its own. */
@Composable
fun episodeDetails(episode: EpisodeEntity, meta: MetadataCacheEntity?, tmdbWins: Boolean): MediaDetails {
    val fallback = episode.name.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.player_episode_number, episode.episodeNumber)
    return MediaDetails(
        title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: fallback else fallback,
        subtitle = stringResource(R.string.content_season_episode, episode.seasonNumber, episode.episodeNumber),
        backdropUrl = MetadataImages.backdrop(meta?.backdropPath ?: meta?.posterPath),
        // The whole day where either side knows it, falling back to the bare year — on a long-running
        // show the year is shared by hundreds of episodes and identifies none of them.
        metaLine = listOfNotNull(
            rememberAirDateLabel(episode, meta),
            meta?.rating?.positive()?.let { stringResource(R.string.content_rating, it) },
        ).joinToString(stringResource(R.string.content_metadata_separator))
            .ifEmpty { metaLine(meta?.year, meta?.rating?.positive(), null) },
        plot = if (tmdbWins) meta?.overview ?: episode.plot
        else episode.plot?.takeIf { it.isNotBlank() } ?: meta?.overview,
    )
}

private fun Double.positive(): Double? = takeIf { it > 0 }

/** "2026 • ★ 7.6 • 2h 10m", with the year through the locale's own digits. */
@Composable
private fun metaLine(year: Int?, rating: Double?, duration: String?): String {
    val digits = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }
    return listOfNotNull(
        year?.let { digits.format(it) },
        rating?.let { stringResource(R.string.content_rating, it) },
        duration,
    ).joinToString(stringResource(R.string.content_metadata_separator))
}

/** Parse a stored JSON array of strings (genres) back to a list; empty on null, blank or bad JSON. */
/** Shared with Home's trending hero, which merges the same TMDB payload. */
internal fun jsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/** Taller than the pickers: this one is for reading, so it takes three quarters of the screen. */
@Composable
private fun detailsHeight() = (LocalConfiguration.current.screenHeightDp * 3 / 4).dp
