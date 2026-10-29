package tv.own.owntv.mobile.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.restoreCategoryToDefault
import tv.own.owntv.core.customize.planCategoryRestore
import tv.own.owntv.core.customize.CategoryRestorePlan
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.liveCountFlow
import tv.own.owntv.core.live.livePagingSource
import tv.own.owntv.core.live.parseLiveKey
import tv.own.owntv.core.live.serialize
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.player.ExternalPlayerLauncher
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SourceOverrides
import tv.own.owntv.core.stalker.StreamUrlResolver
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.ui.stage.PlaylistMark
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.mobile.ui.components.ReorderItem
import tv.own.owntv.mobile.ui.shell.StartupLiveSelection

/** One chip in the strip above the channel list. [builtIn] labels are translated; the rest are the
 *  user's or the provider's own names, so they are carried as text. */
data class LiveCategory(
    val key: LiveKey,
    val title: String? = null,
    val builtIn: BuiltIn? = null,
    /** The playlist a provider folder comes from, for its mark in the categories sheet. */
    val sourceId: Long? = null,
) {
    enum class BuiltIn { ALL, FAVORITES, HISTORY, CATCHUP }
}

/**
 * Live TV for the phone.
 *
 * Every query, and the rule for which channels survive the profile's customizations, comes from
 * core — the same functions the TV app's Live screen calls, so hiding a channel or reordering a
 * folder on one device means the same thing on the other. What differs is only what the screen
 * needs: there is no preview pane to keep fed and no focused row to follow, so the guide is read in
 * one batch for the rows actually on screen instead of per channel as focus moves.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class LiveViewModel(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customCategoryDao: CustomCategoryDao,
    private val contentOrderDao: ContentOrderDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val epgRepository: EpgRepository,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val streamUrlResolver: StreamUrlResolver,
    private val syncScheduler: CatalogSyncScheduler,
    private val startupSelection: StartupLiveSelection,
    epgDao: EpgDao,
    /** The app-wide reader the Guide uses too — one now/next cache, not two. */
    private val epgReader: LiveEpgReader,
) : ViewModel() {


    /** The same candidate set the Guide's picker uses — filtered by no source. */
    private val guideCandidates = tv.own.owntv.core.epg.GuideCandidates(epgDao)

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionCustomizations())

    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.SortMode.PLAYLIST)

    private val liveCategories = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyList())
            else categoryDao.observe(c.liveSourceIds, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Category DB ids this profile does not see — its hidden categories, plus every adult category
     * when the profile is a kids one. Resolved to ids so All / History / Catch-up can drop those
     * channels too, exactly as the TV app does: hiding a group has to hide its channels everywhere,
     * not just remove its chip.
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> =
        combine(liveCategories, custom, ctx.flatMapLatest { profileDao.observeById(it.profileId) }) { cats, cust, profile ->
            AdultCategoryClassifier.hiddenCategoryIds(cats, cust.hiddenCategories, profile?.isKids == true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val folderContextKeys: StateFlow<Map<Long, String>> = liveCategories
        .map { cats -> cats.associateBy({ it.id }, { CustomizeKeys.category(it) }) }
        // Eagerly: only ever read as `.value` (the menu's Move, the pager's manual order), never collected —
        // shared while-subscribed it stayed empty, and Move / Move to category never appeared in a folder.
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually carry a manual order — core's paging query takes the cheap path for
     *  the rest, and the pager is rebuilt when a folder gains or loses one. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyList())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.LIVE)
        }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The chip strip: the built-in lists, then the profile's categories in its own arranged order.
     *  Catch-up only appears when the provider actually advertises an archive. */
    val categories: StateFlow<List<LiveCategory>> = combine(
        liveCategories,
        custom,
        sortMode,
        ctx.flatMapLatest { channelDao.observeCatchupCount(it.liveSourceIds.ifEmpty { listOf(-1L) }) }.distinctUntilChanged(),
        ctx.flatMapLatest { profileDao.observeById(it.profileId) },
    ) { cats, cust, sort, catchupCount, profile ->
        val folders = cats.railCategories(
            cust,
            kids = profile?.isKids == true,
            alphaRest = sort == SettingsRepository.SortMode.ALPHA,
        )
        buildList {
            add(LiveCategory(LiveKey.All, builtIn = LiveCategory.BuiltIn.ALL))
            add(LiveCategory(LiveKey.Favorites, builtIn = LiveCategory.BuiltIn.FAVORITES))
            add(LiveCategory(LiveKey.History, builtIn = LiveCategory.BuiltIn.HISTORY))
            if (catchupCount > 0) add(LiveCategory(LiveKey.Catchup, builtIn = LiveCategory.BuiltIn.CATCHUP))
            val sources = cats.associate { it.id to it.sourceId }
            folders.forEach { e ->
                add(
                    LiveCategory(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName,
                        sourceId = e.categoryId?.let(sources::get),
                    ),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The channel count beside every category in the sheet (the TV's railCounts): each fixed entry and
     * custom category by the crumb's own query, every provider folder from one grouped query. Only
     * collected while the sheet is open.
     */
    val categoryCounts: StateFlow<Map<LiveKey, Int>> = combine(ctx, hiddenCategoryIds, categories) { c, hidden, items -> Triple(c, hidden, items) }
        .flatMapLatest { (c, hidden, items) ->
            if (c.profileId < 0) return@flatMapLatest flowOf(emptyMap())
            val single = items.filter { it.key !is LiveKey.Folder }.map { item ->
                liveCountFlow(item.key, c.profileId, c.liveSourceIds, hidden, channelDao, customCategoryDao).map { mapOf(item.key to it) }
            }
            val folders = channelDao.observeCountsByCategory(c.liveSourceIds.ifEmpty { listOf(-1L) })
                .map { rows -> rows.associate { LiveKey.Folder(it.categoryId) as LiveKey to it.itemCount } }
            combine(single + folders) { parts -> parts.fold(emptyMap<LiveKey, Int>()) { acc, m -> acc + m } }.throttleLatest()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Settings › Layout › Navigation › Show counts. */
    val sheetCounts: StateFlow<Boolean> = settings.sheetCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** List or the guide grid — core's "Live TV opens in", remembered from the Guide view tool. */
    val liveView: StateFlow<SettingsRepository.LiveView> = settings.liveView
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.LiveView.LIST)

    fun toggleLiveView() {
        val next = if (liveView.value == SettingsRepository.LiveView.GUIDE) SettingsRepository.LiveView.LIST else SettingsRepository.LiveView.GUIDE
        viewModelScope.launch { settings.setLiveView(next) }
    }

    /** What restoring [key]'s category to the playlist default would undo (empty when nothing). */
    suspend fun categoryRestorePlan(key: LiveKey): CategoryRestorePlan {
        val contextKey = categoryContextKey(key) ?: return CategoryRestorePlan()
        val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return CategoryRestorePlan()
        return planCategoryRestore(customize, contentOrderDao, pid, MediaType.LIVE, contextKey, customCategoryDao) {
            when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, pid, contextKey, Int.MAX_VALUE)
                    .mapTo(HashSet()) { CustomizeKeys.channel(it) }
                else -> customCategoryDao.stableItemKeys(pid, contextKey).toSet()
            }
        }
    }

    /** Back to the playlist's order and contents (core's restoreCategoryToDefault). */
    fun restoreCategory(key: LiveKey) {
        val contextKey = categoryContextKey(key) ?: return
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            restoreCategoryToDefault(customize, contentOrderDao, pid, MediaType.LIVE, contextKey, categoryRestorePlan(key))
        }
    }

    /** The content_order / movedFromOrigin context behind a category, or null for the fixed entries. */
    private fun categoryContextKey(key: LiveKey): String? = when (key) {
        is LiveKey.Folder -> folderContextKeys.value[key.id]
        is LiveKey.Custom -> key.id
        else -> null
    }

    /** Hide and reorder a category from the strip, through the same editor Settings → Customize uses. */
    private val categoryEditor = CategoryRailEditor(customize, categoryDao, profileDao)

    /** Hide the long-pressed category. It comes back from Settings → Customize. */
    fun hideCategory(key: LiveKey) {
        viewModelScope.launch {
            val c = ctx.value
            if (c.profileId < 0) return@launch
            categoryEditor.hide(c.profileId, MediaType.LIVE, key)
            // The strip is about to lose this chip; leaving it selected would show a folder that is
            // no longer there.
            if (selected.value == key) select(LiveKey.All)
        }
    }

    /** Move the long-pressed category one step through the strip, and keep it there. */
    fun moveCategory(key: LiveKey, kind: MoveKind) {
        viewModelScope.launch {
            val c = ctx.value
            if (c.profileId < 0) return@launch
            categoryEditor.move(
                profileId = c.profileId,
                sourceIds = c.liveSourceIds,
                type = MediaType.LIVE,
                alphaRest = sortMode.value == SettingsRepository.SortMode.ALPHA,
                key = key,
                kind = kind,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selected: StateFlow<LiveKey> = _selected

    /** Playlist names, but only when more than one is active — a single-playlist user does not need
     *  every row to repeat where it came from. */
    val providerNames: StateFlow<Map<Long, String>> = ctx
        .map { c -> if (c.sources.size > 1) c.sources.associate { it.id to it.name } else emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Channel numbers are drawn only when the setting the TV app reads is on. */
    val showChannelNumbers: StateFlow<Boolean> = settings.directTune
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * The Stage playlist mark per source, coloured by the playlist's position in the
     * profile's list — the TV's rule. Empty with one playlist, like [providerNames].
     */
    val playlistMarks: StateFlow<Map<Long, PlaylistMark>> = ctx
        .map { c ->
            if (c.liveSourceIds.size < 2) emptyMap()
            else c.sources.withIndex().associate { (i, s) -> s.id to PlaylistMark.of(s.name, i) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The order tool: Provider (the playlist's own) or A–Z, one tap between them as on the TV. */
    val sort: StateFlow<SettingsRepository.SortMode> = sortMode

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.ALPHA) SettingsRepository.SortMode.PLAYLIST
                else SettingsRepository.SortMode.ALPHA,
            )
        }
    }

    private val _query = MutableStateFlow("")

    /** The search field above the list: filters the open category. */
    val query: StateFlow<String> = _query

    fun setQuery(text: String) {
        _query.value = text
    }

    /** The open category's channel count, for the crumb ("· 9 channels"). */
    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) ->
            if (c.profileId < 0) flowOf(0)
            else liveCountFlow(key, c.profileId, c.liveSourceIds, hidden, channelDao, customCategoryDao).throttleLatest()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _nowPlaying = MutableStateFlow<Map<Long, XtEpgEntry>>(emptyMap())

    /** What is on now for the rows on screen, with its start and stop, keyed by channel id. */
    val nowPlaying: StateFlow<Map<Long, XtEpgEntry>> = _nowPlaying

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private data class Args(val key: LiveKey, val ctx: ActiveProfileSources, val cust: SectionCustomizations, val hidden: Set<Long>)

    val channels: Flow<PagingData<ChannelEntity>> =
        combine(_selected, ctx, custom, hiddenCategoryIds, ::Args)
            // A folder gaining or losing a manual order changes which query is correct, and the
            // cheap one does not observe content_order — so the pager has to be rebuilt. So does the
            // order tool, and the search field (settled for a moment, so each letter is not a query).
            .combine(orderedContexts) { args, _ -> args }
            .combine(sortMode) { args, _ -> args }
            .combine(_query.debounce(SEARCH_DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged()) { args, q -> args to q }
            .flatMapLatest { (args, q) ->
                val (key, c, cust, hidden) = args
                if (c.profileId < 0) {
                    flowOf(PagingData.empty())
                } else {
                    // Placeholders off — see the Guide's pager: a not-yet-loaded channel draws a row
                    // with no height, and a list of those composes the whole lineup.
                    // maxSize drops distant pages, so a 50 000-channel lineup cannot pin the whole
                    // catalogue in memory while flinging; initialLoadSize fills the viewport at once.
                    Pager(
                        PagingConfig(
                            pageSize = PAGE_SIZE,
                            prefetchDistance = PAGE_SIZE / 2,
                            initialLoadSize = PAGE_SIZE * 2,
                            maxSize = 400,
                            enablePlaceholders = false,
                        ),
                    ) {
                        livePagingSource(
                            key = key,
                            profileId = c.profileId,
                            sourceIds = c.liveSourceIds,
                            query = q,
                            sort = sortMode.value,
                            channelDao = channelDao,
                            customCategoryDao = customCategoryDao,
                            contextKey = { folderContextKeys.value[it] },
                            hasManualOrder = { it in orderedContexts.value },
                        )
                    }.flow.map { paging -> paging.applyCustomizations(key, cust, hidden) }
                }
            }
            .cachedIn(viewModelScope)

    /**
     * Hidden channels and hidden categories drop out, renames are applied, and a channel moved into
     * a custom category leaves the folder it came from (but stays in All).
     *
     * Applied inside the pager chain, on each fresh `PagingData`: Paging forbids transforming one the
     * UI has already collected, so a customization change re-creates the pager instead.
     */
    private fun PagingData<ChannelEntity>.applyCustomizations(
        key: LiveKey,
        cust: SectionCustomizations,
        hidden: Set<Long>,
    ): PagingData<ChannelEntity> {
        if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hidden.isEmpty() && cust.movedFromOrigin.isEmpty()) return this
        return filter { ch ->
            tv.own.owntv.core.live.isChannelVisible(ch, cust, hidden, key) &&
                (cust.movedFromOrigin[CustomizeKeys.channel(ch)]?.let { origin ->
                    key !is LiveKey.Folder || origin != folderContextKeys.value[key.id]
                } ?: true)
        }.map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    init {
        viewModelScope.launch {
            if (settings.rememberCategoryLive.first()) {
                parseLiveKey(settings.lastLiveCategory.first())?.let {
                    if (!startupApplied) _selected.value = it
                }
            }
        }
    }

    private var startupApplied = false

    /**
     * "Start on: Favorites" outranks the remembered folder — it is what the user asked this launch to
     * open on, and it applies to this launch only. Called by the Live TV tab itself: the TV Guide borrowing
     * this list of categories must not eat it.
     */
    fun applyStartupSelection() {
        if (startupSelection.consumeFavorites()) {
            startupApplied = true
            _selected.value = LiveKey.Favorites
        }
    }

    fun select(key: LiveKey) {
        if (_selected.value == key) return
        _selected.value = key
        _query.value = ""
        viewModelScope.launch { settings.setLastLiveCategory(key.serialize()) }
    }

    /**
     * Read the guide for the rows the user can see, in one query.
     *
     * Channels already answered are dropped first, so scrolling one row costs one row's worth of
     * guide rather than a screenful.
     */
    fun loadNowPlaying(visible: List<ChannelEntity>) {
        // An entry whose programme has ended is as good as missing: the next one is read in its place.
        val now = System.currentTimeMillis()
        val missing = visible.filter { ch -> _nowPlaying.value[ch.id]?.let { it.stopMs <= now } ?: true }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val found = epgReader.nowProgrammesFor(missing, custom.value, epgOffset.value)
            if (found.isEmpty()) return@launch
            // Capped like the guide's rowCache: scrolled-past channels are forgotten, and a
            // revisit simply re-reads them through the `missing` check above.
            _nowPlaying.value = (_nowPlaying.value + found).toList()
                .takeLast(MAX_NOW_PLAYING).toMap()
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            if (channel.id in favoriteIds.value) userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
        }
    }

    /** Hide the channel from every list (undone in Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.setItemHidden(pid, MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel for this profile (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.renameItem(pid, MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatchResolver.epgIdFor(channel)

    suspend fun availableEpgChannels(channelName: String, query: String): List<tv.own.owntv.core.epg.GuideCandidate> =
        if (ctx.value.profileId < 0) emptyList()
        else guideCandidates.forPicker(channelName, query)

    /** Map a channel to a guide channel by hand (null clears the override → back to auto-match). */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
            // The matched id may have no stored programmes yet — top it up from the cached XMLTV, then
            // drop the channel's stale now/next so the row updates immediately, not after a restart.
            val id = epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (id != null) runCatching { epgRepository.storeProgrammesForIdsFromCache(setOf(id)) }
            epgReader.invalidate(channel.id)
            _nowPlaying.value = _nowPlaying.value - channel.id
        }
    }

    fun currentEpgShift(channel: ChannelEntity): Int? = EpgShift.overrideFor(custom.value, channel)

    fun globalEpgShift(): Int = epgOffset.value

    /** Shift this channel's guide by [minutes] (null → follow the global offset). */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.setEpgShift(pid, MediaType.LIVE, CustomizeKeys.channel(channel), minutes)
            epgReader.invalidate(channel.id)
            _nowPlaying.value = _nowPlaying.value - channel.id
        }
    }

    /** Hand the channel to VLC, MX Player or whatever else is installed. Stalker channels store a
     *  portal command rather than a URL, so it is resolved first — an external app cannot mint one. */
    fun playExternal(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            if (!AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return@launch
            val source = sourceDao.getById(channel.sourceId)
            val url = if (streamUrlResolver.needsResolve(source)) {
                runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }.getOrNull() ?: return@launch
            } else {
                channel.streamUrl
            }
            externalPlayerLauncher.launch(
                url = url,
                title = channel.name,
                userAgent = source?.userAgent,
                httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
            )
        }
    }

    /** Take [channel] out of the custom category [key] only. If it had been moved out of its provider
     *  folder into here, it goes back there — the same as deleting the whole category does. */
    fun removeFromCustomCategory(channel: ChannelEntity, key: LiveKey.Custom) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            userDataWriter.removeCustomCategoryMember(pid, MediaType.LIVE, key.id, channel.id)
            val itemKey = CustomizeKeys.channel(channel)
            custom.value.movedFromOrigin[itemKey]?.let { origin ->
                customize.setItemMovedFromOrigin(pid, MediaType.LIVE, itemKey, origin, moved = false)
            }
        }
    }

    fun removeFromHistory(channelId: Long) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            userDataWriter.removeHistory(pid, MediaType.LIVE, channelId)
        }
    }

    /** The stable key of the list being looked at, or null when it has no stored order to move in. */
    fun contextKeyOf(key: LiveKey): String? = when (key) {
        is LiveKey.Folder -> folderContextKeys.value[key.id]
        is LiveKey.Custom -> key.id
        LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
        // Catch-up, History and All are computed views: there is no order of their own to change.
        LiveKey.History, LiveKey.All, LiveKey.Catchup -> null
    }

    /** The channels of [key] in their current order — what the Move sheet shuffles. */
    suspend fun moveList(key: LiveKey): List<ReorderItem> {
        val c = ctx.value
        if (c.profileId < 0) return emptyList()
        val contextKey = contextKeyOf(key) ?: return emptyList()
        val ids = c.liveSourceIds.ifEmpty { listOf(-1L) }
        val items = when (key) {
            is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, c.profileId, contextKey, MOVE_LIST_LIMIT)
            is LiveKey.Custom -> customCategoryDao.snapshotChannels(c.profileId, key.id, ids, MOVE_LIST_LIMIT)
            LiveKey.Favorites -> channelDao.snapshotFavoritesManual(c.profileId, contextKey, ids, MOVE_LIST_LIMIT)
            else -> return emptyList()
        }
        return items.map { ReorderItem(it.id, it.name) }
    }

    fun commitMove(contextKey: String, itemIds: List<Long>) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.LIVE,
                contextKey = contextKey,
                rows = itemIds.mapIndexed { i, id ->
                    ContentOrderEntity(
                        profileId = pid,
                        mediaType = MediaType.LIVE,
                        contextKey = contextKey,
                        itemId = id,
                        position = i,
                    )
                },
            )
        }
    }

    /** The user's own combined categories — the Move-to-category sheet's targets. */
    val customCategories: StateFlow<List<Pair<String, String>>> = custom
        .map { c -> c.customCategories.map { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.createCustomCategory(pid, MediaType.LIVE, name)
        }
    }

    /**
     * Move (or copy, [keepInOrigin]) a channel into a custom category. Without [keepInOrigin] it
     * leaves where it came from: a favourite row is deleted, a custom-category membership is
     * deleted, and a provider folder is marked — the list then drops it from that folder while
     * keeping it in All.
     */
    fun moveToCategory(channel: ChannelEntity, originKey: String, targetId: String, keepInOrigin: Boolean) {
        if (targetId == originKey) return
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            val itemKey = CustomizeKeys.channel(channel)
            customCategoryDao.appendItem(pid, MediaType.LIVE, targetId, channel.id)
            if (!keepInOrigin) {
                when {
                    originKey == ContentOrderEntity.FAV_CONTEXT -> userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
                    CustomizeKeys.isCustom(originKey) -> userDataWriter.removeCustomCategoryMember(pid, MediaType.LIVE, originKey, channel.id)
                    else -> customize.setItemMovedFromOrigin(pid, MediaType.LIVE, itemKey, originKey, moved = true)
                }
            }
        }
    }

    /**
     * Pull to refresh: the same manual re-sync the TV app's settings screen enqueues, on every
     * playlist the profile has active. It runs in WorkManager, so leaving the screen does not cancel
     * it — the spinner only reports that it was asked for, which is why it is let go after a moment.
     */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            ctx.value.sources.forEach { syncScheduler.enqueueSync(it.id, reason = "manual") }
            kotlinx.coroutines.delay(REFRESH_SPINNER_MS)
            _refreshing.value = false
        }
    }

    private companion object {
        const val PAGE_SIZE = 60
        const val MOVE_LIST_LIMIT = 5_000
        const val REFRESH_SPINNER_MS = 1_200L
        const val SEARCH_DEBOUNCE_MS = 250L
        // "What's on now", one short string per answered channel. Capped — see loadNowPlaying.
        const val MAX_NOW_PLAYING = 500
    }
}
