package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.ui.text.style.TextOverflow
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.work.EpgSyncState
import tv.own.owntv.core.util.classifySyncFailure
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.mobile.R
import androidx.compose.foundation.layout.Box
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageChevron
import androidx.compose.ui.semantics.Role
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageField

/**
 * The XMLTV feeds that fill the guide, each with what it actually holds: how many channels, how many
 * programmes, and how many of your own channels can be replayed from the archive.
 *
 * A feed can also lend the guide its own channel logos, which is usually the better picture — a
 * playlist's logo is whatever the provider happened to attach.
 */
@Composable
fun SettingsEpgSourcesPage(
    modifier: Modifier = Modifier,
    vm: EpgSourcesViewModel = koinViewModel(),
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val autoRefresh by vm.autoRefresh.collectAsStateWithLifecycle()
    val useLogos by vm.useLogos.collectAsStateWithLifecycle()
    val deleting by vm.deletingIds.collectAsStateWithLifecycle()

    var menuSource by remember { mutableStateOf<EpgSource?>(null) }
    var editSource by remember { mutableStateOf<EpgSource?>(null) }
    var addSource by remember { mutableStateOf(tv.own.owntv.mobile.ui.setup.SetupHandoff.takeAddEpg()) }
    var refreshFor by remember { mutableStateOf<EpgSource?>(null) }
    var manualDaysFor by remember { mutableStateOf<EpgSource?>(null) }
    var confirmDelete by remember { mutableStateOf<EpgSource?>(null) }

    // "‹ Settings · Sources & guide · 2 feeds", + Add EPG, the feeds, then the note on addresses.
    SettingsCrumb(pluralStringResource(R.plurals.settings_epg_feed_count, sources.size, sources.size), SettingsGroup.SOURCES)
    SettingsStagePage(modifier) {
        item(key = "add") {
            Box(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                StageButton(onClick = { addSource = true }, text = stringResource(R.string.settings_epg_sources_add), icon = MobileIcons.Add, small = true)
            }
        }
        if (sources.isEmpty()) item(key = "empty") { PageNote(stringResource(R.string.settings_epg_sources_empty)) }
        sources.forEach { source ->
            item(key = source.id) {
                EpgRow(
                    source = source,
                    autoRefresh = autoRefresh[source.id] ?: EpgRefresh.OFF,
                    counts = { vm.counts(source.id) },
                    syncState = vm.observeSync(source.id).collectAsStateWithLifecycle(EpgSyncState.Idle).value,
                    isDeleting = source.id in deleting,
                    onClick = { if (source.id !in deleting) menuSource = source },
                )
            }
        }
        item(key = "note") { PageNote(stringResource(R.string.settings_epg_sources_addresses_note)) }
    }

    menuSource?.let { source ->
        val syncing = vm.observeSync(source.id).collectAsStateWithLifecycle(EpgSyncState.Idle).value.isActive
        val close = { menuSource = null }
        MobileBottomSheet(onDismissRequest = close) {
            StageSheetHeader(source.name, eyebrow = stringResource(R.string.settings_epg_sources))
            StageMenuItem(
                stringResource(if (syncing) R.string.settings_sources_cancel else R.string.settings_sync_now),
                onClick = { if (syncing) vm.cancelSync(source) else vm.resync(source); close() },
                icon = if (syncing) MobileIcons.Close else MobileIcons.Refresh,
            )
            StageMenuItem(stringResource(R.string.settings_sources_edit), onClick = { editSource = source; close() }, icon = MobileIcons.Edit)
            StageSettingRow(
                title = stringResource(R.string.settings_epg_sources_use_logos),
                subtitle = stringResource(R.string.settings_epg_sources_logos_description),
                icon = MobileIcons.Image,
                minHeight = 48.dp,
                onClick = { vm.setUseLogos(source, source.id !in useLogos) },
            ) { StageSwitch(source.id in useLogos) }
            StageMenuItem(
                stringResource(R.string.settings_epg_sources_auto_refresh_title),
                onClick = { refreshFor = source; close() },
                icon = MobileIcons.Schedule,
                value = epgRefreshLabel(autoRefresh[source.id] ?: EpgRefresh.OFF),
            )
            StageMenuItem(stringResource(R.string.settings_sources_delete), onClick = { confirmDelete = source; close() }, icon = MobileIcons.Delete, danger = true)
        }
    }

    refreshFor?.let { source ->
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
            choices = EpgAutoRefresh.entries.map { SettingsChoice(it, epgAutoRefreshLabel(it)) },
            selected = (autoRefresh[source.id] ?: EpgRefresh.OFF).mode,
            onSelect = { mode ->
                // "Every N days" needs the N, so it opens a second sheet rather than guessing one.
                if (mode == EpgAutoRefresh.MANUAL) manualDaysFor = source else vm.setAutoRefresh(source, EpgRefresh(mode))
            },
            onDismiss = { refreshFor = null },
        )
    }

    manualDaysFor?.let { source ->
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_sources_refresh_days_title),
            choices = PlaylistRefresh.PRESET_MANUAL_DAYS.map {
                SettingsChoice(it, pluralStringResource(R.plurals.settings_sources_refresh_days, it, it))
            },
            selected = (autoRefresh[source.id] ?: EpgRefresh.OFF).manualDays,
            onSelect = { vm.setAutoRefresh(source, EpgRefresh(EpgAutoRefresh.MANUAL, it)) },
            onDismiss = { manualDaysFor = null },
        )
    }

    confirmDelete?.let { source ->
        StageConfirmSheet(
            title = stringResource(R.string.settings_epg_sources_delete_title, source.name),
            text = stringResource(R.string.settings_epg_sources_delete_message),
            confirm = stringResource(R.string.common_delete),
            confirmIcon = MobileIcons.Delete,
            danger = true,
            onConfirm = { vm.delete(source) },
            onDismiss = { confirmDelete = null },
        )
    }

    if (addSource) {
        EpgSourceSheet(
            initial = null,
            initialAutoRefresh = EpgRefresh.OFF,
            initialUseLogos = false,
            playlistOptions = { vm.playlistEpgOptions() },
            onDismiss = { addSource = false },
            onSave = { name, url, agent, refresh, logos ->
                vm.add(name, url, agent, refresh, logos)
                addSource = false
            },
        )
    }

    editSource?.let { source ->
        EpgSourceSheet(
            initial = source,
            initialAutoRefresh = autoRefresh[source.id] ?: EpgRefresh.OFF,
            initialUseLogos = source.id in useLogos,
            playlistOptions = { vm.playlistEpgOptions() },
            onDismiss = { editSource = null },
            onSave = { name, url, agent, refresh, logos ->
                vm.update(source, name, url, agent)
                vm.setAutoRefresh(source, refresh)
                vm.setUseLogos(source, logos)
                editSource = null
            },
        )
    }
}

/**
 * One feed's row. The status line is the whole point of it: a feed that says "synced" but holds no
 * programmes is broken, and only the counts show that.
 */
@Composable
private fun EpgRow(
    source: EpgSource,
    autoRefresh: EpgRefresh,
    counts: suspend () -> Triple<Int, Int, Int>,
    syncState: EpgSyncState,
    isDeleting: Boolean,
    onClick: () -> Unit,
) {
    val count by produceState<Triple<Int, Int, Int>?>(null, source.id, source.lastSyncAt, source.lastError) {
        value = runCatching { counts() }.getOrNull()
    }
    val res = LocalContext.current.resources
    val active = syncState as? EpgSyncState.Syncing
    // "245 channels · 9,076 programs · catch-up on 1,404": counts grouped, one short line.
    val sep = stringResource(R.string.content_epg_bits_separator)
    val channelsText = @Composable { n: Int -> pluralStringResource(R.plurals.sync_count_channels, n, tv.own.owntv.mobile.ui.screens.home.formatCount(n)) }
    val programmesText = @Composable { n: Int -> pluralStringResource(R.plurals.settings_epg_line_programmes, n, tv.own.owntv.mobile.ui.screens.home.formatCount(n)) }
    val catchupText = count?.third?.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.settings_epg_line_catchup, it, tv.own.owntv.mobile.ui.screens.home.formatCount(it))
    }
    val status = when {
        active != null -> when {
            active.programmes > 0 -> channelsText(active.channels) + sep + programmesText(active.programmes)
            active.channels > 0 -> channelsText(active.channels)
            else -> stringResource(R.string.settings_epg_sources_connecting)
        }
        source.lastError != null -> stringResource(
            R.string.settings_epg_sources_error,
            classifySyncFailure(source.lastError, online = true).displayText(res),
        )
        count != null && count!!.second > 0 ->
            listOfNotNull(channelsText(count!!.first), programmesText(count!!.second), catchupText).joinToString(sep)
        source.lastSyncAt != null -> catchupText ?: stringResource(R.string.settings_epg_sources_status_synced)
        else -> stringResource(R.string.settings_epg_sources_not_synced)
    }
    val percent = active?.let {
        if (it.baseProgrammes > 0 && it.programmes > 0) {
            ((it.programmes.toLong() * 100) / it.baseProgrammes).toInt().coerceAtMost(99)
        } else {
            null
        }
    }
    val palette = LocalStagePalette.current
    val portal = tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(source.url) != null
    // Never the address: it can carry the account's user name and password. A portal's own guide says so.
    val first = if (portal && count == null && active == null) stringResource(R.string.settings_epg_sources_portal_guide) else status
    val second = listOfNotNull(
        source.lastSyncAt?.let { stringResource(R.string.settings_synced_at, syncedWhen(it)) },
        epgRefreshLabel(autoRefresh).takeIf { autoRefresh.mode != EpgAutoRefresh.OFF },
    ).joinToString(stringResource(R.string.content_epg_bits_separator))
    StageSettingRow(
        title = source.name,
        icon = MobileIcons.Guide,
        subtitle = listOf(first, second).filter { it.isNotBlank() }.joinToString("\n"),
        onClick = onClick,
        onLongClick = onClick,
    ) {
        val (text, color) = when {
            isDeleting -> stringResource(R.string.settings_epg_sources_deleting) to palette.danger
            active != null -> (percent?.let { stringResource(R.string.settings_epg_sources_syncing_percent, it) }
                ?: stringResource(R.string.settings_epg_sources_syncing_label)) to stageAccent.accent
            source.lastError != null -> stringResource(R.string.settings_playback_kind_error) to palette.danger
            source.lastSyncAt != null -> stringResource(R.string.common_ok) to palette.ok
            else -> null to palette.muted
        }
        text?.let { Text(it, style = stageText(12.5f, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Add or edit a feed: the same four questions the television asks, on one sheet. */
@Composable
private fun EpgSourceSheet(
    initial: EpgSource?,
    initialAutoRefresh: EpgRefresh,
    initialUseLogos: Boolean,
    playlistOptions: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, EpgRefresh, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var agent by remember { mutableStateOf(initial?.userAgent.orEmpty()) }
    var refresh by remember { mutableStateOf(initialAutoRefresh) }
    var logos by remember { mutableStateOf(initialUseLogos) }
    var showRefresh by remember { mutableStateOf(false) }
    var showManualDays by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    MobileBottomSheet(onDismissRequest = onDismiss) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(
            if (initial == null) R.string.settings_epg_sources_add else R.string.settings_epg_sources_edit,
        ))
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            StageField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.settings_epg_sources_name),
                placeholder = stringResource(R.string.settings_epg_sources_name_hint),
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            StageField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.settings_epg_sources_url),
                placeholder = stringResource(R.string.settings_epg_sources_url_hint),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            StageButton(
                text = stringResource(R.string.settings_epg_sources_fill_playlist),
                onClick = { showPlaylists = true },
                modifier = Modifier.fillMaxWidth(),
            )
            StageField(
                value = agent,
                onValueChange = { agent = it },
                label = stringResource(R.string.settings_epg_sources_user_agent),
                placeholder = stringResource(R.string.settings_epg_sources_user_agent_hint),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
            StageSettingRow(
                title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
                onClick = { showRefresh = true },
            ) { StageRowValue(epgRefreshLabel(refresh), StageChevron.DOWN) }
            StageSettingRow(
                title = stringResource(R.string.settings_epg_sources_use_logos),
                subtitle = stringResource(R.string.settings_epg_sources_logos_description),
                role = Role.Switch,
                onClick = { logos = !logos },
            ) { StageSwitch(logos) }
            StageButton(
                text = stringResource(
                    if (initial == null) R.string.settings_epg_sources_add_sync
                    else R.string.settings_epg_sources_save_sync,
                ),
                onClick = { onSave(name.trim(), url.trim(), agent.trim().ifBlank { null }, refresh, logos) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                style = StageButtonStyle.PRIMARY,
            )
        }
    }

    if (showRefresh) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
            choices = EpgAutoRefresh.entries.map { SettingsChoice(it, epgAutoRefreshLabel(it)) },
            selected = refresh.mode,
            onSelect = { mode ->
                if (mode == EpgAutoRefresh.MANUAL) showManualDays = true else refresh = EpgRefresh(mode)
            },
            onDismiss = { showRefresh = false },
        )
    }

    if (showManualDays) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_sources_refresh_days_title),
            choices = PlaylistRefresh.PRESET_MANUAL_DAYS.map {
                SettingsChoice(it, pluralStringResource(R.plurals.settings_sources_refresh_days, it, it))
            },
            selected = refresh.manualDays,
            onSelect = { refresh = EpgRefresh(EpgAutoRefresh.MANUAL, it) },
            onDismiss = { showManualDays = false },
        )
    }

    if (showPlaylists) {
        val options by produceState<List<EpgSourcesViewModel.PlaylistEpg>?>(null) {
            value = runCatching { playlistOptions() }.getOrDefault(emptyList())
        }
        MobileBottomSheet(onDismissRequest = { showPlaylists = false }) {
            tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.settings_epg_sources_fill_playlist))
            val list = options
            if (list != null && list.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_epg_sources_none_playlist),
                    style = tv.own.owntv.mobile.ui.stage.stageText(13.5f, 500),
                    color = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current.dim,
                    modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
                )
            }
            list.orEmpty().forEach { option ->
                // A portal guide has no address to show, and nothing to fill in — say what it is, and
                // add it straight away rather than putting an internal marker in a URL box.
                val portal = tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(option.url) != null
                tv.own.owntv.mobile.ui.stage.StageSettingRow(
                    title = option.name,
                    subtitle = if (portal) stringResource(R.string.settings_epg_sources_portal_guide) else tv.own.owntv.core.network.HttpClient.redactUrl(option.url),
                    onClick = {
                        showPlaylists = false
                        if (portal) {
                            // Nothing to type: save it as it stands and close the sheet.
                            onSave(option.name, option.url, agent.trim().takeIf { it.isNotBlank() }, refresh, logos)
                            onDismiss()
                        } else {
                            url = option.url
                            if (name.isBlank()) name = option.name
                        }
                    },
                ) { tv.own.owntv.mobile.ui.stage.StageRadio(option.url == url) }
            }
        }
    }
}

@Composable
private fun epgRefreshLabel(refresh: EpgRefresh): String =
    if (refresh.mode == EpgAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        epgAutoRefreshLabel(refresh.mode)
    }

@Composable
private fun epgAutoRefreshLabel(mode: EpgAutoRefresh): String = stringResource(
    when (mode) {
        EpgAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        EpgAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        EpgAutoRefresh.HOURS_1 -> R.string.settings_epg_refresh_1h
        EpgAutoRefresh.HOURS_3 -> R.string.settings_epg_refresh_3h
        EpgAutoRefresh.HOURS_6 -> R.string.settings_epg_refresh_6h
        EpgAutoRefresh.HOURS_12 -> R.string.settings_epg_refresh_12h
        EpgAutoRefresh.HOURS_24 -> R.string.settings_epg_refresh_24h
        EpgAutoRefresh.HOURS_48 -> R.string.settings_epg_refresh_48h
        EpgAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)
