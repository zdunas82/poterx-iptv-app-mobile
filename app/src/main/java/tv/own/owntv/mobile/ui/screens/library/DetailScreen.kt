package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.koin.androidx.compose.koinViewModel

/**
 * A film or a show, opened: the Stage movie page or the series page. One view model for
 * both, told which item once.
 */
@Composable
fun DetailScreen(
    tab: LibraryTab,
    itemId: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Another title opened from this one ("More in …"). */
    onOpenItem: (LibraryTab, Long) -> Unit = { _, _ -> },
    /** The versions button: a search for the title. */
    onOpenSearch: (String) -> Unit = {},
    vm: DetailViewModel = koinViewModel(),
) {
    LaunchedEffect(tab, itemId) { vm.open(tab, itemId) }
    when (tab) {
        LibraryTab.MOVIES -> MoviePage(vm = vm, onPlay = onPlay, onOpenItem = onOpenItem, onOpenSearch = onOpenSearch, modifier = modifier)
        LibraryTab.SERIES -> SeriesPage(vm = vm, onPlay = onPlay, modifier = modifier)
    }
}
