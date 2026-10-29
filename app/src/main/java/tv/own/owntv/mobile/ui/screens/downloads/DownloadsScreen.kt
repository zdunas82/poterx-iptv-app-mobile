package tv.own.owntv.mobile.ui.screens.downloads

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.download.DownloadActivityTracker
import tv.own.owntv.core.download.DownloadQueue
import tv.own.owntv.core.download.DownloadReasons
import tv.own.owntv.core.download.DownloadStorageInfo
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaFolders
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ExportDocument
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.home.durationText
import tv.own.owntv.mobile.ui.screens.home.formatYear
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsScreen
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsViewModel
import tv.own.owntv.mobile.ui.shell.PageCrumb
import tv.own.owntv.mobile.ui.shell.ProvidePageCrumb
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageLabel
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.StageRow
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageStill
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageTool
import tv.own.owntv.mobile.ui.stage.StageTabs
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageGlass
import tv.own.owntv.mobile.ui.stage.stageText
import java.text.NumberFormat
import kotlin.math.ceil

/** Movies and Series hold downloads; Recordings is live TV saved by the recorder — a file of the user's all the same. */
private enum class DownloadsTab(val labelRes: Int) {
    MOVIES(R.string.common_nav_movies),
    SERIES(R.string.common_nav_series),
    RECORDINGS(R.string.recording_title),
}

/**
 * Downloads: the crumb is the volume and its free space, then the tabs Movies ·
 * Series · Recordings with their counts and the folder tool, the folder the open tab saves to, and the list
 * grouped DOWNLOADING (queue order, the running one first) / ON THIS PHONE, each row with its picture, its
 * line, where it got to and its file's folder. The Recordings card closes the list.
 *
 * A tap plays a finished download; a hold opens its sheet (play, save a copy, location, delete). The
 * running, paused or failed download carries its own buttons — Pause, Resume or Retry, and Delete.
 */
@Composable
fun DownloadsScreen(
    onPlayerOpened: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DownloadsViewModel = koinViewModel(),
    recVm: RecordingsViewModel = koinViewModel(),
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val missing by vm.missingFiles.collectAsStateWithLifecycle()
    val root by vm.downloadRoot.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val recordings by recVm.rows.collectAsStateWithLifecycle()

    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val watchChoice by vm.downloadWatchChoice.collectAsStateWithLifecycle()
    val chosenFolder by vm.chosenFolder.collectAsStateWithLifecycle()
    val folderLost by vm.folderLost.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = LocalStagePalette.current

    // The system folder picker. A phone bound for Google Play cannot hold all-files access, so this
    // is the only way it writes to a folder the user chose rather than one Android handed it.
    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree -> if (tree != null) vm.setDownloadTree(tree) }

    var tab by rememberSaveable { mutableStateOf(DownloadsTab.MOVIES) }
    var menuFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var optionsSheet by remember { mutableStateOf(false) }
    var savingCopyOf by remember { mutableStateOf<DownloadEntity?>(null) }

    val saveCopy = rememberLauncherForActivityResult(
        ExportDocument("video/*"),
    ) { uri ->
        val download = savingCopyOf
        savingCopyOf = null
        if (uri != null && download != null) vm.export(download, uri)
    }

    LaunchedEffect(playing) {
        if (playing) {
            vm.playerOpened()
            onPlayerOpened()
        }
    }

    // The crumb (`header('Downloads', …)`): "Kingston USB drive · **57.6 GB free** of 57.7 GB"; a tap opens
    // the folder options, as the tool does.
    val crumb = storage?.let { info ->
        storageLine(info, remember(root, info.usingFallback) { volumeName(context, root.takeUnless { info.usingFallback }) })
    }
    ProvidePageCrumb { owner -> PageCrumb(accent = "", rest = null, chevron = false, owner = owner, styled = crumb) { optionsSheet = true } }

    // Which tab holds which kind is core's rule: an episode download is EPISODE, which files under Series.
    val movies = remember(downloads) { downloads.filter { MediaFolders.folderFor(it.mediaType) == MediaFolders.MOVIES } }
    val series = remember(downloads) { downloads.filter { MediaFolders.folderFor(it.mediaType) == MediaFolders.SERIES } }
    val recorded = recordings.count { it.status == RecordingStatus.COMPLETED || it.status == RecordingStatus.RECORDING }
    val shown = if (tab == DownloadsTab.SERIES) series else movies
    // DOWNLOADING in queue order (the running one first, so "starts after" reads downwards), then ON THIS PHONE.
    val downloading = remember(shown) {
        shown.filter { it.status != DownloadStatus.COMPLETED }
            .sortedWith(compareBy<DownloadEntity> { it.status != DownloadStatus.RUNNING }.thenBy { it.createdAt })
    }
    val onPhone = remember(shown) { shown.filter { it.status == DownloadStatus.COMPLETED } }

    Column(modifier.fillMaxSize()) {
        // The fallback warning: the chosen folder is missing, new downloads go to the app's own folder until it is back.
        if (storage?.usingFallback == true) {
            Text(
                stringResource(R.string.content_storage_folder_fallback),
                style = stageText(11.5f, 500), color = palette.warn, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 4.dp),
            )
        }
        DownloadTabs(
            tabs = listOf(DownloadsTab.MOVIES to movies.size, DownloadsTab.SERIES to series.size, DownloadsTab.RECORDINGS to recorded),
            selected = tab,
            onSelect = { tab = it },
            folderOpen = optionsSheet,
            onFolder = { optionsSheet = true },
        )
        // The folder the open tab saves to: the folder in use plus core's fixed subfolder (TV 40f0ddc1).
        storage?.let { info ->
            val sub = when (tab) {
                DownloadsTab.MOVIES -> MediaFolders.MOVIES
                DownloadsTab.SERIES -> MediaFolders.SERIES
                DownloadsTab.RECORDINGS -> MediaFolders.TV
            }
            val folder = remember(root, info.usingFallback) {
                root.takeUnless { info.usingFallback }?.let(StorageAccess::folderLabel) ?: StorageAccess.defaultRoot(context).absolutePath
            }
            Row(
                Modifier.padding(start = 18.dp, end = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageIcon(MobileIcons.Folder, 11.dp, palette.dim)
                Text(folder.trimEnd('/') + "/" + sub, style = stageText(10.5f, 500), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (tab == DownloadsTab.RECORDINGS) {
            RecordingsScreen(onPlayerOpened = onPlayerOpened, modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                if (downloading.isEmpty() && onPhone.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.content_downloads_empty),
                            style = stageText(13f, 500), color = palette.muted,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp),
                        )
                    }
                }
                if (downloading.isNotEmpty()) {
                    item(key = "hdr_downloading") { StageLabel(stringResource(R.string.content_downloads_group_downloading), Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) }
                    items(downloading, key = { it.id }) { d ->
                        DownloadItem(d, details[d.id], active?.takeIf { it.id == d.id }, downloading, gone = d.filePath in missing, vm = vm, onMenu = { menuFor = d })
                    }
                }
                if (onPhone.isNotEmpty()) {
                    item(key = "hdr_on_phone") {
                        StageLabel(
                            stringResource(R.string.content_downloads_group_on_phone),
                            Modifier.padding(start = 12.dp, end = 12.dp, top = if (downloading.isEmpty()) 8.dp else 14.dp, bottom = 6.dp),
                        )
                    }
                    items(onPhone, key = { it.id }) { d ->
                        DownloadItem(d, details[d.id], null, downloading, gone = d.filePath in missing, vm = vm, onMenu = { menuFor = d })
                    }
                }
                item(key = "recordings") { RecordingsCard(recordings, recorded, onOpenGuide) }
                item(key = "bottom") { Spacer(Modifier.height(22.dp)) }
            }
        }
    }

    menuFor?.let { download ->
        DownloadMenu(
            download = download,
            title = details[download.id]?.title ?: download.title,
            gone = download.filePath in missing,
            vm = vm,
            onSaveCopy = {
                savingCopyOf = download
                saveCopy.launch(download.filePath?.substringAfterLast('/') ?: download.title)
            },
            onDismiss = { menuFor = null },
        )
    }

    if (optionsSheet) {
        DownloadOptionsSheet(
            volumes = vm.volumes,
            current = root,
            wifiOnly = wifiOnly,
            watchChoice = watchChoice,
            chosenFolder = chosenFolder,
            folderLost = folderLost,
            onPick = vm::setDownloadRoot,
            onChooseFolder = {
                // Opens where downloads currently go, when that is somewhere the picker can show.
                chooseFolder.launch(root.takeIf { MediaTarget.isDocument(it) }?.let(Uri::parse))
            },
            onWifiOnly = vm::setWifiOnly,
            onWatchChoice = vm::setDownloadWatchChoice,
            onDismiss = { optionsSheet = false },
        )
    }
}

/** "Kingston USB drive · **57.6 GB free** of 57.7 GB": the volume muted, the free space in the accent. */
@Composable
private fun storageLine(info: DownloadStorageInfo, volume: String?): AnnotatedString {
    val accent = stageAccent.accent
    // Formatted with markers, so the free part is found in any language's word order rather than guessed.
    val template = stringResource(R.string.content_downloads_storage_free, FREE_MARK, TOTAL_MARK)
    val free = gigabytes(info.freeBytes)
    val total = gigabytes(info.totalBytes)
    val freeAt = template.indexOf(FREE_MARK)
    val totalAt = template.indexOf(TOTAL_MARK)
    // The accented part runs from the free figure to the word before the total ("57.6 GB free" | "of 57.7 GB");
    // where the total comes first, from the free figure to the end.
    val accentEnd = if (totalAt > freeAt) {
        template.substring(0, totalAt).trimEnd().lastIndexOf(' ').takeIf { it > freeAt } ?: totalAt
    } else {
        template.length
    }
    val sep = stringResource(R.string.content_epg_bits_separator)
    return buildAnnotatedString {
        volume?.let { append(it); append(sep) }
        fun plain(s: String) = append(s.replace(FREE_MARK, free).replace(TOTAL_MARK, total))
        plain(template.substring(0, freeAt))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { plain(template.substring(freeAt, accentEnd)) }
        plain(template.substring(accentEnd))
    }
}

/** The tabs (14 / 800 and the 3 dp accent line under the open one, 14 / 700 muted otherwise, counts 11 dim) and the folder tool. */
@Composable
private fun DownloadTabs(
    tabs: List<Pair<DownloadsTab, Int>>,
    selected: DownloadsTab,
    onSelect: (DownloadsTab) -> Unit,
    folderOpen: Boolean,
    onFolder: () -> Unit,
) {
    StageTabs(
        tabs = tabs.map { (t, n) -> stringResource(t.labelRes) to n },
        selected = setOf(tabs.indexOfFirst { it.first == selected }),
        onSelect = { onSelect(tabs[it].first) },
        modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        StageTool(
            onClick = onFolder,
            icon = MobileIcons.Folder,
            on = folderOpen,
            contentDescription = stringResource(R.string.settings_download_folder),
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/**
 * One download (`.chrow` 78, taller with its buttons): the 104 dp picture, the title, its line ("2026 · 1 h 38 min
 * · FHD · 3.1 GB"), where it got to, the folder it is saved in, then the progress and the buttons while it is
 * coming down, or ✓ Ready at the end once it is here. The running one is the current row (`.fx`).
 */
@Composable
private fun DownloadItem(
    d: DownloadEntity,
    info: DownloadDetails?,
    live: DownloadActivityTracker.ActiveDownload?,
    queue: List<DownloadEntity>,
    gone: Boolean,
    vm: DownloadsViewModel,
    onMenu: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val done = d.status == DownloadStatus.COMPLETED
    StageRow(
        onClick = if (done && !gone) ({ vm.play(d) }) else onMenu,
        onLongClick = onMenu,
        current = d.status == DownloadStatus.RUNNING,
        minHeight = 78.dp,
    ) {
        StageStill(info?.artUrl ?: d.posterUrl, Modifier.width(104.dp), radius = 10.dp)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(info?.title ?: d.title, style = stageText(13.5f, 700), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            downloadLine(d, info).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = stageText(12f, 500), color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
            if (gone) {
                Text(stringResource(R.string.content_downloads_file_missing), style = stageText(12f, 500), color = palette.danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (!done) {
                DownloadStatusText(d, live, queue)
            }
            // Only a real folder trail: a file moved out with Export is a bare document id ("3272"), which says nothing.
            MediaFolders.crumb(d.filePath, "/")?.takeIf { '/' in it }?.let {
                Text(PATH_ELLIPSIS + it, style = stageText(10f, 500), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.PAUSED) {
                val total = live?.totalBytes?.takeIf { it > 0 } ?: d.totalBytes
                val got = live?.downloadedBytes ?: d.downloadedBytes
                // `.prog` at 3 dp: the accent → focus sweep, a little thinner than the default.
                StageProgress(if (total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else 0f, Modifier.padding(top = 6.dp).height(3.dp))
            }
            // A queued one has none: its hold menu pauses or deletes it.
            if (!done && d.status != DownloadStatus.QUEUED) {
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (d.status) {
                        DownloadStatus.RUNNING ->
                            StageButton(onClick = { vm.pause(d) }, text = stringResource(R.string.content_downloads_pause), icon = MobileIcons.Pause, small = true)
                        DownloadStatus.PAUSED ->
                            StageButton(onClick = { vm.resume(d) }, text = stringResource(R.string.common_resume), icon = MobileIcons.PlayArrow, small = true)
                        DownloadStatus.FAILED ->
                            StageButton(onClick = { vm.retry(d) }, text = stringResource(R.string.common_retry), icon = MobileIcons.Refresh, small = true)
                        DownloadStatus.COMPLETED, DownloadStatus.QUEUED -> Unit
                    }
                    StageButton(onClick = { vm.delete(d) }, text = stringResource(R.string.common_delete), icon = MobileIcons.Delete, small = true)
                }
            }
        }
        if (done && !gone) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                StageIcon(MobileIcons.Check, 12.dp, palette.ok)
                Text(stringResource(R.string.content_downloads_ready_short), style = stageText(11f, 700), color = palette.ok, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "2026 · 1 h 38 min · FHD · 3.1 GB" for a film, "Season 1 · Episode 3 · 2.4 GB" for an episode. */
@Composable
private fun downloadLine(d: DownloadEntity, info: DownloadDetails?): String = buildList {
    info?.year?.takeIf { it > 0 }?.let { add(formatYear(it)) }
    info?.season?.let { add(if (it == 0) stringResource(R.string.content_season_specials) else stringResource(R.string.content_season, it)) }
    info?.episode?.let { add(stringResource(R.string.player_episode_number, it)) }
    if (info?.season == null) info?.runtimeSecs?.takeIf { it >= 60 }?.let { add(durationText(it / 60L)) }
    info?.quality?.let { add(it) }
    if (d.totalBytes > 0) add(fileSize(d.totalBytes))
}.joinToString(stringResource(R.string.content_epg_bits_separator))

/**
 * Where a download not yet here got to: "**64%** · 12.4 MB/s · 3 min left" while it runs (speed and time from
 * core's tracker), "Queued · starts after …", Paused, or why it failed — core's wording, the same on the TV.
 */
@Composable
private fun DownloadStatusText(d: DownloadEntity, live: DownloadActivityTracker.ActiveDownload?, queue: List<DownloadEntity>) {
    val palette = LocalStagePalette.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val reason = DownloadReasons.displayTextOf(d, LocalContext.current.resources)
    val style = stageText(12f, 500)
    when (d.status) {
        DownloadStatus.FAILED -> Text(
            listOfNotNull(stringResource(R.string.content_downloads_failed_group), reason).joinToString(sep),
            style = style, color = palette.danger, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        DownloadStatus.QUEUED -> Text(
            reason
                ?: DownloadQueue.startsAfter(queue, d.id)?.let { stringResource(R.string.content_downloads_starts_after, it.title) }
                ?: stringResource(R.string.content_downloads_queued),
            style = style, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        DownloadStatus.RUNNING, DownloadStatus.PAUSED -> {
            val got = live?.downloadedBytes ?: d.downloadedBytes
            val total = live?.totalBytes?.takeIf { it > 0 } ?: d.totalBytes
            val fraction = if (total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else null
            val rest = buildList {
                if (d.status == DownloadStatus.PAUSED) add(reason ?: stringResource(R.string.content_downloads_paused))
                live?.bytesPerSecond?.takeIf { it > 0 }?.let { add(stringResource(R.string.content_downloads_speed, decimal(it / 1_048_576.0))) }
                live?.secondsLeft?.let { add(stringResource(R.string.home_time_left, durationText(maxOf(1L, ceil(it / 60.0).toLong())))) }
                if (fraction == null) add(fileSize(got))
            }
            Text(
                buildAnnotatedString {
                    fraction?.let {
                        withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.ExtraBold)) {
                            append(stringResource(R.string.content_progress_percent, (it * 100).toInt()))
                        }
                    }
                    rest.forEachIndexed { i, s -> if (i > 0 || fraction != null) append(sep); append(s) }
                },
                style = style, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        DownloadStatus.COMPLETED -> Unit
    }
}

/**
 * The Recordings card (`.glass`, 18 corners): ● Recordings, "Nothing recorded yet" (or how many there are),
 * how to record, Open TV Guide, and the next scheduled recording at the end of the button row.
 */
@Composable
private fun RecordingsCard(recordings: List<RecordingEntity>, recorded: Int, onOpenGuide: () -> Unit) {
    val palette = LocalStagePalette.current
    val next = remember(recordings) { recordings.filter { it.status == RecordingStatus.SCHEDULED }.minByOrNull { it.programmeStartMs } }
    val context = LocalContext.current
    Column(
        Modifier
            .padding(start = 8.dp, end = 8.dp, top = 16.dp)
            .fillMaxWidth()
            .stageGlass(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        // `.eyebrow` at 11: the record dot, then "Recordings" in the accent.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StageIcon(MobileIcons.Record, 11.dp, stageAccent.accent)
            Text(stringResource(R.string.recording_title), style = stageText(11f, 700), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (recorded == 0) stringResource(R.string.recording_card_empty_title) else pluralStringResource(R.plurals.recording_card_count, recorded, recorded),
            style = stageText(15f, 800), color = palette.text, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(stringResource(R.string.recording_card_body), style = stageText(11.5f, 500, lineHeight = 1.45f), color = palette.muted)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StageButton(onClick = onOpenGuide, text = stringResource(R.string.recording_open_guide), icon = MobileIcons.Guide, small = true)
            next?.let {
                val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.programmeStartMs))
                Text(
                    stringResource(R.string.shell_rail_detail_next, it.title + stringResource(R.string.content_epg_bits_separator) + time),
                    style = stageText(11f, 500), color = palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A hold on a download: Play / Save a copy, or Pause / Resume / Retry; its location, copied on a tap; Delete. */
@Composable
private fun DownloadMenu(
    download: DownloadEntity,
    title: String,
    /** Its file is no longer on the drive: nothing to play or export. */
    gone: Boolean,
    vm: DownloadsViewModel,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        fun act(block: () -> Unit) = { block(); onDismiss() }
        when (download.status) {
            DownloadStatus.COMPLETED -> if (!gone) {
                StageMenuItem(stringResource(R.string.content_downloads_play), act { vm.play(download) }, icon = MobileIcons.PlayArrow)
                StageMenuItem(stringResource(R.string.settings_export), act(onSaveCopy), icon = MobileIcons.Save)
            }
            DownloadStatus.FAILED -> StageMenuItem(stringResource(R.string.common_retry), act { vm.retry(download) }, icon = MobileIcons.Refresh)
            DownloadStatus.PAUSED -> StageMenuItem(stringResource(R.string.common_resume), act { vm.resume(download) }, icon = MobileIcons.PlayArrow)
            else -> StageMenuItem(stringResource(R.string.content_downloads_pause), act { vm.pause(download) }, icon = MobileIcons.Pause)
        }
        // Where the file actually is, spelled out in full and copied on a tap. When it is in the app's own
        // storage a phone will not open that folder for you, so the useful act is handing over the location.
        download.filePath?.let { stored ->
            val path = StorageAccess.folderLabel(stored) ?: stored
            StageSettingRow(
                title = stringResource(R.string.settings_backup_location),
                subtitle = path,
                icon = MobileIcons.Folder,
                onClick = act { vm.copyPath(path) },
            )
        }
        StageMenuItem(stringResource(R.string.common_delete), act { vm.delete(download) }, icon = MobileIcons.Delete, danger = true)
    }
}

/**
 * The folder tool's sheet: which volume downloads are written to (each with the path it writes to, the one in
 * use ticked), another folder through the system picker, Wi-Fi only, and what playing does to a download on a
 * one-stream playlist.
 */
@Composable
private fun DownloadOptionsSheet(
    volumes: List<StorageAccess.StorageRoot>,
    current: String,
    wifiOnly: Boolean,
    watchChoice: SettingsRepository.DownloadWatchChoice,
    /** The folder picked through the system picker, ready to read, or null when none is. */
    chosenFolder: String?,
    /** True when that folder can no longer be written to — revoked permission, or storage removed. */
    folderLost: Boolean,
    onPick: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onWatchChoice: (SettingsRepository.DownloadWatchChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val title = stringResource(R.string.content_downloads_options)
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        StageSheetHeader(title = title)
        // Said before the list: the folder being unreachable is the reason the user is most likely here.
        if (folderLost) {
            Text(
                stringResource(R.string.content_storage_folder_lost),
                style = stageText(12f, 500), color = palette.danger,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
        }
        volumes.forEach { volume ->
            val path = volume.file.absolutePath
            // An unset folder means the default, which is the first volume in the list.
            val chosen = if (current.isBlank()) volume == volumes.firstOrNull() else current == path
            StageSettingRow(
                title = stringResource(
                    if (volume.kind == StorageAccess.RootKind.REMOVABLE) R.string.content_storage_removable else R.string.content_storage_internal,
                ),
                subtitle = path,
                icon = if (volume.kind == StorageAccess.RootKind.REMOVABLE) MobileIcons.SdStorage else MobileIcons.Folder,
                onClick = { onPick(path) },
                current = chosen,
                role = Role.RadioButton,
            ) { if (chosen) StageIcon(MobileIcons.Check, 18.dp, stageAccent.accent) }
        }
        // Anywhere else on the device: once picked, that folder is a row of its own, ticked like the volumes.
        if (chosenFolder != null) {
            StageSettingRow(
                title = stringResource(R.string.content_storage_chosen_folder),
                subtitle = chosenFolder,
                icon = MobileIcons.Folder,
                onClick = onChooseFolder,
                current = !folderLost,
            ) { if (!folderLost) StageIcon(MobileIcons.Check, 18.dp, stageAccent.accent) }
        }
        StageMenuItem(stringResource(R.string.content_storage_choose_folder), onChooseFolder, icon = MobileIcons.Add)
        StageSettingRow(
            title = stringResource(R.string.settings_downloads_wifi_only),
            subtitle = stringResource(R.string.settings_downloads_wifi_only_description),
            icon = MobileIcons.Wifi,
            onClick = { onWifiOnly(!wifiOnly) },
            role = Role.Switch,
        ) { StageSwitch(wifiOnly) }
        // A tap steps through the three answers.
        StageSettingRow(
            title = stringResource(R.string.content_download_watch_setting),
            subtitle = stringResource(R.string.content_download_watch_setting_description),
            icon = MobileIcons.PlayArrow,
            onClick = {
                val all = SettingsRepository.DownloadWatchChoice.entries
                onWatchChoice(all[(watchChoice.ordinal + 1) % all.size])
            },
        ) {
            StageRowValue(
                stringResource(
                    when (watchChoice) {
                        SettingsRepository.DownloadWatchChoice.ASK -> R.string.content_download_watch_ask
                        SettingsRepository.DownloadWatchChoice.PAUSE -> R.string.content_download_watch_pause
                        SettingsRepository.DownloadWatchChoice.KEEP -> R.string.content_download_watch_keep
                    },
                ),
                chevron = StageChevron.NONE,
            )
        }
    }
}

/**
 * The volume's own name as Android words it, in the user's language — "USB drive", "Internal shared storage",
 * a card's label (the TV's). [root] is the download folder (a path or a document-tree URI); blank or null = the
 * app's own folder. Null when the system cannot say, and the crumb then shows the free space alone.
 */
internal fun volumeName(context: Context, root: String?): String? = runCatching {
    val sm = context.getSystemService(StorageManager::class.java)
    val volume = if (root != null && MediaTarget.isDocument(root)) {
        val id = Uri.decode(root.substringAfterLast('/')).substringBefore(':')
        sm.storageVolumes.firstOrNull { if (id == "primary") it.isPrimary else it.uuid.equals(id, ignoreCase = true) }
    } else {
        sm.getStorageVolume(java.io.File(root?.takeIf { it.isNotBlank() } ?: StorageAccess.defaultRoot(context).absolutePath))
    }
    volume?.getDescription(context)
}.getOrNull()

/** 3.1 GB from a gigabyte up, 840.2 MB below it. */
@Composable
private fun fileSize(bytes: Long): String =
    if (bytes >= 1_073_741_824L) stringResource(R.string.common_size_gb, decimal(bytes / 1_073_741_824.0))
    else stringResource(R.string.common_size_mb, decimal(bytes / 1_048_576.0))

private fun gigabytes(bytes: Long): String = decimal(bytes.coerceAtLeast(0) / 1_073_741_824.0)

private fun decimal(value: Double): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}.format(value)

/** Stand-ins for the two figures while the storage line is split; never shown. */
private const val FREE_MARK = "\u0001"
private const val TOTAL_MARK = "\u0002"

/** A row's folder is shown from the media folder down (`…/Series/My Sad Dead/Season 1`). */
private const val PATH_ELLIPSIS = "…/"
