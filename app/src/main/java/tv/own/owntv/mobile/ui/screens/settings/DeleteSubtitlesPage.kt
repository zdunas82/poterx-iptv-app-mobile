package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.owntv.mobile.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import androidx.compose.foundation.layout.Column
import tv.own.owntv.mobile.ui.screens.settings.customize.ConfirmDialog

/**
 * The subtitle files already on the phone, and the four ways to remove them: one file, all films,
 * all shows, or the lot.
 *
 * Every delete is confirmed, because a downloaded subtitle costs a download from a daily allowance
 * that is small — deleting one by accident is not free.
 */
@Composable
fun DeleteSubtitlesPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DeleteSubtitlesViewModel = koinViewModel(),
) {
    val section by vm.section.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val movieCount by vm.movieCount.collectAsStateWithLifecycle()
    val seriesCount by vm.seriesCount.collectAsStateWithLifecycle()

    var deletingOne by remember { mutableStateOf<LinkedSubtitle?>(null) }
    var deletingSection by remember { mutableStateOf<DeleteSubtitlesViewModel.Section?>(null) }
    var deletingAll by remember { mutableStateOf(false) }

    val sections = DeleteSubtitlesViewModel.Section.entries
    val counts = mapOf(
        DeleteSubtitlesViewModel.Section.MOVIES to movieCount,
        DeleteSubtitlesViewModel.Section.SERIES to seriesCount,
    )

    SettingsPage(modifier) {
        settingsGroup(key = "header") {
            // An in-page step of OpenSubtitles, so it carries its own way back.
            StageSettingRow(title = stringResource(R.string.settings_delete_subtitles), icon = MobileIcons.ArrowBack, onClick = onBack)
        }
        settingsNote(R.string.player_subtitles_delete_description)
        settingsGroup(key = "sections") {
            Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { s ->
                    StageTool(
                        onClick = { vm.selectSection(s) },
                        label = stringResource(R.string.settings_section_count, stringResource(s.labelRes()), counts.getValue(s)),
                        on = s == section,
                    )
                }
            }
        }

        if (items.isEmpty()) {
            settingsNote(R.string.settings_no_downloaded_subtitles)
        }
        items(items, key = { it.cacheId }) { item ->
            StageSettingRow(
                title = item.contentTitle,
                subtitle = listOfNotNull(item.languageName ?: item.language, item.releaseName)
                    .joinToString(stringResource(R.string.content_metadata_separator))
                    .ifBlank { null },
                onClick = { deletingOne = item },
            ) {
                StageButton(onClick = { deletingOne = item }, text = stringResource(R.string.common_delete), style = StageButtonStyle.DANGER, small = true)
            }
        }

        settingsGroup(key = "delete-section") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StageButton(
                    onClick = { deletingSection = section },
                    text = stringResource(section.deleteAllRes()),
                    style = StageButtonStyle.DANGER,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                StageButton(
                    onClick = { deletingAll = true },
                    text = stringResource(R.string.settings_delete_all),
                    style = StageButtonStyle.DANGER,
                    enabled = movieCount + seriesCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    deletingOne?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_subtitle),
            message = stringResource(R.string.settings_delete_subtitle_message, item.contentTitle),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteOne(item); deletingOne = null },
            onDismiss = { deletingOne = null },
        )
    }

    deletingSection?.let { target ->
        ConfirmDialog(
            title = stringResource(target.deleteAllRes()),
            message = stringResource(R.string.settings_delete_all_subtitles),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteAllInSection(target); deletingSection = null },
            onDismiss = { deletingSection = null },
        )
    }

    if (deletingAll) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all),
            message = stringResource(R.string.settings_delete_all_subtitles),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteAll(); deletingAll = false },
            onDismiss = { deletingAll = false },
        )
    }
}

private fun DeleteSubtitlesViewModel.Section.labelRes() = when (this) {
    DeleteSubtitlesViewModel.Section.MOVIES -> R.string.settings_movies
    DeleteSubtitlesViewModel.Section.SERIES -> R.string.settings_series
}

private fun DeleteSubtitlesViewModel.Section.deleteAllRes() = when (this) {
    DeleteSubtitlesViewModel.Section.MOVIES -> R.string.settings_delete_all_movies
    DeleteSubtitlesViewModel.Section.SERIES -> R.string.settings_delete_all_series
}
