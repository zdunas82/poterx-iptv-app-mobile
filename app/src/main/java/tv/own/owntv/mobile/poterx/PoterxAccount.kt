package tv.own.owntv.mobile.poterx

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.mobile.BuildConfig

/**
 * Poterx: what a locked build knows about its one account beyond the credentials.
 */
object PoterxAccount {

    /**
     * The identity every request to the panel carries (playlist, guide, streams), so the provider's
     * logs tell this app from the generic players — and the phone from the television.
     */
    val userAgent: String get() = "$USER_AGENT_NAME/${BuildConfig.VERSION_NAME}"

    /**
     * Existing installs signed in before the identity existed, or on an older version: every Xtream
     * playlist that does not carry the current identity is updated to it. Nothing else changes.
     */
    suspend fun ensureUserAgent(sourceDao: SourceDao, sourceRepository: SourceRepository) {
        if (!LockedSource.enabled) return
        val wanted = userAgent
        sourceDao.getAllOnce()
            .filter { it.type == SourceType.XTREAM && it.userAgent != wanted }
            .forEach { sourceRepository.updateSource(it.copy(userAgent = wanted)) }
    }

    /**
     * The Xtream playlist of the profile in use. A profile can carry its own account (a second list
     * with its own login), so the checks that ask the panel about "the account" — the expiry notice —
     * must ask about this profile's, not the first one in the database.
     */
    suspend fun activeXtreamSource(sourceDao: SourceDao, settings: SettingsRepository): SourceEntity? {
        val profileId = settings.activeProfileId.first()
        if (profileId < 0L) return null
        return sourceDao.observeForProfile(profileId).first().firstOrNull { it.type == SourceType.XTREAM }
    }

    /**
     * Sign out: the playlists (with everything remembered for them), the guide feeds and the
     * profiles go, and the active profile is cleared, which sends the app back to the sign-in page.
     * The built-in server stays, as does every device-level setting (language, playback).
     */
    suspend fun signOut(
        sourceDao: SourceDao,
        sourceRepository: SourceRepository,
        profileDao: ProfileDao,
        epgStore: EpgSourceStore,
        settings: SettingsRepository,
    ) {
        sourceDao.getAllOnce().forEach { runCatching { sourceRepository.deleteSource(it) } }
        epgStore.getAll().forEach { runCatching { epgStore.remove(it.id) } }
        runCatching { profileDao.deleteAll() }
        settings.setActiveProfile(-1L)
    }

    /**
     * Profiles without a playlist. The sign-in screen used to create "Dom" on every showing (a cold
     * start on the sign-in page, a failed attempt, a sign-out and back), so one device ended up
     * with several empty "Dom" profiles, a "Who's watching?" gate at every start and channels in the
     * last one only. [emptyProfileIds] names them; [pruneEmptyProfiles] removes all but one.
     */
    suspend fun emptyProfileIds(profileDao: ProfileDao, sourceDao: SourceDao): List<Long> {
        val linked = sourceDao.allLinks().map { it.profileId }.toSet()
        return profileDao.getAllOnce().filter { it.pinHash == null && it.id !in linked }.map { it.id }
    }

    /**
     * Removes empty profiles. One is kept only when no other profile holds a playlist: it is then the
     * sign-in's own profile, waiting to be filled ([keep], else the active one, else the first). Beside
     * a profile that has a list an empty one is a hole in the "Who's watching?" gate, whatever left it
     * (Back pressed while adding a profile, a crash mid-import), so all of them go. A profile that holds
     * a playlist or has a PIN is never touched. Returns the id kept, or null when none was.
     */
    suspend fun pruneEmptyProfiles(profileDao: ProfileDao, sourceDao: SourceDao, settings: SettingsRepository, keep: Long? = null): Long? {
        val empty = emptyProfileIds(profileDao, sourceDao)
        if (empty.isEmpty()) return null
        val populated = profileDao.getAllOnce().filter { it.id !in empty }
        val active = settings.activeProfileId.first()
        val kept = if (populated.isNotEmpty()) null else keep?.takeIf { it in empty } ?: active.takeIf { it in empty } ?: empty.first()
        for (id in empty) {
            if (id == kept) continue
            // An empty profile that is the active one is replaced, so the gate does not point at a hole.
            if (id == active) settings.setActiveProfile(kept ?: populated.first().id)
            profileDao.getById(id)?.let { runCatching { profileDao.delete(it) } }
        }
        return kept
    }

    /**
     * Playlists no profile holds any more (the profile that had a second list was deleted): they would
     * go on refreshing, and their guide feed with them, with nothing in the app able to show or remove
     * them. The playlist, its refresh choice and the guide feed that belongs to it alone are removed.
     */
    suspend fun pruneOrphanSources(
        sourceDao: SourceDao,
        sourceRepository: SourceRepository,
        epgStore: EpgSourceStore,
        epgRepository: EpgRepository,
        settings: SettingsRepository,
        catalogSyncScheduler: CatalogSyncScheduler,
        epgSyncScheduler: EpgSyncScheduler,
    ) {
        if (!LockedSource.enabled) return
        val sources = sourceDao.getAllOnce()
        val linked = sourceDao.allLinks().map { it.sourceId }.toSet()
        val orphans = sources.filter { it.id !in linked }
        if (orphans.isEmpty()) return
        val keepUrls = sources.filter { it.id in linked }.flatMap { epgRepository.guideUrls(it) }.toSet()
        val feeds = epgStore.getAll()
        for (orphan in orphans) {
            // The same steps the app's own delete takes: scheduled work first, then the programmes of its
            // guide feed (they can be 100k+ rows), then the feed, the refresh choice and the playlist.
            runCatching { catalogSyncScheduler.cancelSync(orphan.id) }
            val urls = epgRepository.guideUrls(orphan).toSet() - keepUrls
            feeds.filter { it.url in urls }.forEach { feed ->
                runCatching { epgSyncScheduler.cancelSync(feed.id) }
                runCatching { epgRepository.clear(feed.id) }
                runCatching { epgStore.remove(feed.id) }
            }
            runCatching { settings.setPlaylistAutoRefresh(orphan.id, PlaylistRefresh.OFF) }
            runCatching { sourceRepository.deleteSource(orphan) }
            if (settings.defaultSourceId.first() == orphan.id) runCatching { settings.setDefaultSource(-1L) }
        }
    }

    /** [pruneOrphanSources] shortly after a profile was deleted, in the app's own scope (the screen may be gone by then). */
    fun pruneOrphanSourcesSoon() {
        if (!LockedSource.enabled) return
        val koin = org.koin.core.context.GlobalContext.get()
        koin.get<kotlinx.coroutines.CoroutineScope>().launch {
            delay(500)
            runCatching { pruneOrphanSources(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get()) }
        }
    }

    private const val USER_AGENT_NAME = "PoterxMobile"
}
