package tv.own.owntv.mobile.poterx

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.core.sync.work.EpgSyncState
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.settings.EpgSourcesViewModel
import tv.own.owntv.mobile.ui.screens.settings.PageNote
import tv.own.owntv.mobile.ui.screens.settings.SettingsChoice
import tv.own.owntv.mobile.ui.screens.settings.SettingsChoiceSheet
import tv.own.owntv.mobile.ui.screens.settings.SettingsCrumb
import tv.own.owntv.mobile.ui.screens.settings.SettingsGroup
import tv.own.owntv.mobile.ui.screens.settings.SettingsStagePage
import tv.own.owntv.mobile.ui.screens.settings.SettingsViewModel
import tv.own.owntv.mobile.ui.setup.labelRes
import tv.own.owntv.mobile.ui.stage.StageChevron
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageSettingRow

/**
 * Poterx: Settings › Sources & guide › Playlists on a locked build. The playlist is built in, so there
 * is nothing to add, edit or delete and the server is never printed. What remains is what a user
 * legitimately needs: refresh the channel list now, and choose how often it refreshes by itself.
 */
@Composable
fun LockedSourcesPage(modifier: Modifier = Modifier, vm: SettingsViewModel = koinViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val autoRefresh by vm.settings.playlistAutoRefresh.collectAsStateWithLifecycle(emptyMap())
    val modes = remember { listOf(PlaylistAutoRefresh.OFF, PlaylistAutoRefresh.STARTUP, PlaylistAutoRefresh.HOURS_6, PlaylistAutoRefresh.HOURS_12) }
    var refreshFor by remember { mutableStateOf<SourceEntity?>(null) }

    SettingsCrumb(stringResource(R.string.poterx_sources_title), SettingsGroup.SOURCES)
    SettingsStagePage(modifier) {
        if (sources.isEmpty()) item(key = "empty") { PageNote(stringResource(R.string.settings_sources_empty)) }
        sources.forEach { source ->
            item(key = "refresh_${source.id}") {
                val syncState by vm.syncState(source.id).collectAsStateWithLifecycle(CatalogSyncState.Idle)
                val syncing = syncState.isActive
                val lastSync = source.lastSyncAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
                // "Refresh now": the same enqueue the stock sources page uses; a tap again cancels a running one.
                StageSettingRow(
                    title = source.name,
                    subtitle = when {
                        syncing -> stringResource(R.string.sync_progress_syncing)
                        lastSync != null -> stringResource(R.string.poterx_last_refresh, lastSync)
                        else -> stringResource(R.string.poterx_last_refresh, stringResource(R.string.common_never))
                    },
                    icon = MobileIcons.Refresh,
                    onClick = { if (syncing) vm.cancelResync(source) else vm.resync(source) },
                ) { StageRowValue(stringResource(if (syncing) R.string.settings_sources_cancel else R.string.poterx_refresh_now)) }
            }
            item(key = "auto_${source.id}") {
                val refresh = autoRefresh[source.id] ?: PlaylistRefresh.OFF
                StageSettingRow(
                    title = stringResource(R.string.poterx_refresh_auto),
                    subtitle = stringResource(R.string.poterx_refresh_auto_description),
                    icon = MobileIcons.Schedule,
                    onClick = { refreshFor = source },
                ) { StageRowValue(stringResource(refresh.mode.labelRes()), StageChevron.DOWN) }
            }
        }
        item(key = "note") { PageNote(stringResource(R.string.poterx_locked_body, stringResource(R.string.app_name))) }
    }
    refreshFor?.let { source ->
        val current = autoRefresh[source.id] ?: PlaylistRefresh.OFF
        SettingsChoiceSheet(
            title = stringResource(R.string.poterx_refresh_auto),
            choices = modes.map { SettingsChoice(it, stringResource(it.labelRes())) },
            selected = current.mode,
            onSelect = { mode -> vm.edit { setPlaylistAutoRefresh(source.id, PlaylistRefresh(mode, current.manualDays)) } },
            onDismiss = { refreshFor = null },
        )
    }
}

/**
 * Poterx: Settings › Sources & guide › EPG on a locked build. The guide feed is the panel's own and is
 * registered by the app, so there is nothing to add, edit or delete, and its address (which carries
 * the account and names the panel) is never printed. What remains: refresh now, and how often.
 */
@Composable
fun LockedGuidePage(modifier: Modifier = Modifier, vm: EpgSourcesViewModel = koinViewModel()) {
    val feeds by vm.sources.collectAsStateWithLifecycle()
    val autoRefresh by vm.autoRefresh.collectAsStateWithLifecycle()
    val modes = remember { listOf(EpgAutoRefresh.OFF, EpgAutoRefresh.STARTUP, EpgAutoRefresh.HOURS_6, EpgAutoRefresh.HOURS_12, EpgAutoRefresh.HOURS_24) }
    var refreshFor by remember { mutableStateOf<EpgSource?>(null) }

    SettingsCrumb(stringResource(R.string.settings_epg_sources), SettingsGroup.SOURCES)
    SettingsStagePage(modifier) {
        if (feeds.isEmpty()) item(key = "empty") { PageNote(stringResource(R.string.poterx_guide_empty)) }
        feeds.forEach { feed ->
            item(key = "refresh_${feed.id}") {
                val syncState by vm.observeSync(feed.id).collectAsStateWithLifecycle(EpgSyncState.Idle)
                val syncing = syncState.isActive
                val lastSync = feed.lastSyncAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
                StageSettingRow(
                    title = stringResource(R.string.poterx_guide_feed),
                    subtitle = when {
                        syncing -> stringResource(R.string.sync_progress_syncing)
                        lastSync != null -> stringResource(R.string.poterx_last_refresh, lastSync)
                        else -> stringResource(R.string.poterx_last_refresh, stringResource(R.string.common_never))
                    },
                    icon = MobileIcons.Refresh,
                    onClick = { if (syncing) vm.cancelSync(feed) else vm.resync(feed) },
                ) { StageRowValue(stringResource(if (syncing) R.string.settings_sources_cancel else R.string.poterx_refresh_now)) }
            }
            item(key = "auto_${feed.id}") {
                val refresh = autoRefresh[feed.id] ?: EpgRefresh.OFF
                StageSettingRow(
                    title = stringResource(R.string.poterx_refresh_auto),
                    subtitle = stringResource(R.string.poterx_guide_refresh_description),
                    icon = MobileIcons.Schedule,
                    onClick = { refreshFor = feed },
                ) { StageRowValue(lockedEpgRefreshLabel(refresh), StageChevron.DOWN) }
            }
        }
        item(key = "note") { PageNote(stringResource(R.string.poterx_guide_body)) }
    }
    refreshFor?.let { feed ->
        val current = autoRefresh[feed.id] ?: EpgRefresh.OFF
        SettingsChoiceSheet(
            title = stringResource(R.string.poterx_refresh_auto),
            choices = modes.map { SettingsChoice(it, lockedEpgRefreshLabel(EpgRefresh(it))) },
            selected = current.mode,
            onSelect = { mode -> vm.setAutoRefresh(feed, EpgRefresh(mode, current.manualDays)) },
            onDismiss = { refreshFor = null },
        )
    }
}

@Composable
private fun lockedEpgRefreshLabel(refresh: EpgRefresh): String =
    if (refresh.mode == EpgAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        stringResource(
            when (refresh.mode) {
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
    }
