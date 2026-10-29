package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * "Add subtitles": search OpenSubtitles for the film or episode playing, and attach the chosen one
 * without leaving the picture.
 *
 * Every dead end says what to do about it — signed out, out of downloads for today, nothing found in
 * the chosen language — because a search that simply comes back empty is indistinguishable from a
 * search that is broken.
 */
@Composable
fun SubtitleSearchSheet(onDismiss: () -> Unit, vm: SubtitleSearchViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()
    val quota by vm.quotaNote.collectAsStateWithLifecycle()
    // The sheet is opened and closed many times over one film, and the view model outlives it, so the
    // search is started on each open rather than once at construction.
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.applied.collect { onDismiss() } }

    var query by remember { mutableStateOf(vm.initialQuery) }
    val accent = stageAccent.accent

    val title = stringResource(R.string.player_subtitles_search_title)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title)
        StageField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.player_subtitles_title_label),
            imeAction = ImeAction.Search,
            onImeDone = { vm.editSearch(query) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StageButton(onClick = { vm.editSearch(query) }, text = stringResource(R.string.player_subtitles_search), small = true)
            if ((state as? SubtitleSearchViewModel.UiState.Results)?.showingAllLanguages == false ||
                (state as? SubtitleSearchViewModel.UiState.Empty)?.showingAllLanguages == false
            ) {
                StageButton(onClick = vm::showAllLanguages, text = stringResource(R.string.player_subtitles_show_all_languages), small = true)
            }
        }

        quota?.let { q ->
            Note(
                if (q.reset != null) {
                    pluralStringResource(R.plurals.player_subtitles_remaining_reset, q.remaining, q.remaining, q.reset)
                } else {
                    pluralStringResource(R.plurals.player_subtitles_remaining_count, q.remaining, q.remaining)
                },
            )
        }

        when (val s = state) {
            is SubtitleSearchViewModel.UiState.Loading -> Box(
                Modifier.fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), color = accent)
            }

            is SubtitleSearchViewModel.UiState.SignedOut -> Note(
                stringResource(
                    if (s.sessionExpired) {
                        R.string.player_subtitles_session_expired
                    } else {
                        R.string.player_subtitles_account_needed
                    },
                ),
            )

            is SubtitleSearchViewModel.UiState.Empty -> Note(
                stringResource(
                    if (s.showingAllLanguages) {
                        R.string.player_subtitles_no_matches_all_languages
                    } else {
                        R.string.player_subtitles_no_matches_chosen_language
                    },
                ),
            )

            is SubtitleSearchViewModel.UiState.Error -> {
                Note(
                    stringResource(
                        when (s.kind) {
                            SubtitleSearchViewModel.UiState.ErrorKind.LIMIT_REACHED ->
                                R.string.player_subtitles_limit_reached
                            SubtitleSearchViewModel.UiState.ErrorKind.NETWORK ->
                                R.string.player_subtitles_network_error
                        },
                    ),
                )
                StageButton(
                    onClick = vm::retry,
                    text = stringResource(R.string.player_subtitles_try_again),
                    small = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            is SubtitleSearchViewModel.UiState.Results -> LazyColumn(
                Modifier.heightIn(max = sheetListHeight()),
            ) {
                items(s.results, key = { it.fileId }) { result ->
                    val separator = stringResource(R.string.player_subtitles_tags_separator)
                    val tags = buildList {
                        if (result.hearingImpaired) add(stringResource(R.string.player_subtitles_sdh))
                        if (result.aiTranslated) add(stringResource(R.string.player_subtitles_ai))
                        if (result.fromTrusted) add(stringResource(R.string.player_subtitles_trusted))
                        if (result.downloads > 0) {
                            add(
                                pluralStringResource(
                                    R.plurals.player_subtitles_download_count,
                                    result.downloads,
                                    result.downloads,
                                ),
                            )
                        }
                    }.joinToString(separator)
                    StageSettingRow(
                        title = result.languageName
                            ?: result.language
                            ?: stringResource(R.string.player_subtitles_subtitle),
                        subtitle = listOfNotNull(result.releaseName, tags.takeIf { it.isNotEmpty() })
                            .joinToString(separator)
                            .takeIf { it.isNotEmpty() },
                        minHeight = 48.dp,
                        onClick = { vm.select(result) },
                    ) {
                        if (applying == result.fileId) CircularProgressIndicator(Modifier.size(20.dp), color = accent)
                    }
                }
            }
        }
        Note(stringResource(R.string.player_subtitles_api_notice))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = stageText(11.5f, 500, lineHeight = 1.4f),
        color = LocalStagePalette.current.muted,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
