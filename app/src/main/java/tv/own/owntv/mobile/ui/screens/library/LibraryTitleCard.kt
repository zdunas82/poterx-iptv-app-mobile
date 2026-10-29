package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.metadata.HeadlineTitle
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.trending.ProviderVariantParser
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.jsonList
import tv.own.owntv.mobile.ui.player.rememberResumeGate
import tv.own.owntv.mobile.ui.screens.home.durationText
import tv.own.owntv.mobile.ui.screens.home.formatRating
import tv.own.owntv.mobile.ui.screens.home.formatYear
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageTitleArt
import tv.own.owntv.mobile.ui.stage.stageGlass
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Beside the grid: the picked title in a glass card — its title art (48 dp), the name in 16 / 800, the
 * meta line in 10.5, four lines of plot and Play. A show's button opens its page, where the episodes are.
 */
@Composable
internal fun LibraryTitleCard(
    tab: LibraryTab,
    itemId: Long,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DetailViewModel = koinViewModel(key = "card"),
) {
    LaunchedEffect(tab, itemId) { vm.open(tab, itemId) }
    val movie by vm.movie.collectAsStateWithLifecycle()
    val show by vm.show.collectAsStateWithLifecycle()
    val page by vm.titlePage.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val resumeGate = rememberResumeGate()
    val meta = page.meta
    val film = movie.takeIf { tab == LibraryTab.MOVIES }
    val series = show.takeIf { tab == LibraryTab.SERIES }
    val name = film?.name ?: series?.name ?: return
    val title = HeadlineTitle.of(name)
    val ownPlot = film?.plot ?: series?.plot
    val ownRating = film?.rating ?: series?.rating
    val plot = listOf(meta?.overview, ownPlot).let { if (page.tmdbWins) it else it.reversed() }.firstOrNull { !it.isNullOrBlank() }
    val rating = listOf(meta?.rating, ownRating).let { if (page.tmdbWins) it else it.reversed() }.firstOrNull { it != null && it > 0 }
    Column(
        modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 16.dp)
            .stageGlass(RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        var logoFailed by remember(meta?.logoPath) { mutableStateOf(false) }
        MetadataImages.logo(meta?.logoPath)?.takeIf { !logoFailed }?.let { logo ->
            StageTitleArt(url = logo, contentDescription = title, onFailed = { logoFailed = true }, modifier = Modifier.fillMaxWidth(0.7f).height(48.dp))
        }
        Text(title, style = stageText(16f, 800), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        MovieMeta(
            parts = listOfNotNull(
                ((film?.year ?: series?.year) ?: meta?.year)?.takeIf { it > 0 }?.let(::formatYear),
                jsonList(meta?.genresJson).take(3).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.content_epg_bits_separator)),
                film?.durationSecs?.takeIf { it >= 60 }?.let { durationText(it / 60L) },
            ),
            rating = rating?.let(::formatRating),
            tags = ProviderVariantParser.displaySignals(name).let { listOfNotNull(it.quality.label) + it.capabilities },
            size = 10.5f,
        )
        plot?.let {
            Text(it, style = stageText(11f, 500, lineHeight = 1.45f), color = palette.body, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        if (film != null) {
            StageButton(
                onClick = { resumeGate(progress?.takeIf { it.durationMs > 1L }?.positionMs ?: 0L) { vm.play(it, onPlay) } },
                text = stringResource(R.string.content_play),
                icon = MobileIcons.PlayArrow,
                style = StageButtonStyle.PRIMARY,
                small = true,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            StageButton(
                onClick = onOpen,
                text = stringResource(R.string.home_trending_open_episodes),
                icon = MobileIcons.PlayArrow,
                style = StageButtonStyle.PRIMARY,
                small = true,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
