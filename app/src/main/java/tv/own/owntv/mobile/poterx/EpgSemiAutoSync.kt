package tv.own.owntv.mobile.poterx

import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.EpgRepository

/**
 * Poterx: the panel's own guide (Xtream `xmltv.php`), registered and downloaded without a question —
 * the television's semi-auto EPG sync, which the wizard's "Add a TV guide" asks for on a stock build.
 * Each feed the playlist advertises becomes an [EpgSource][tv.own.owntv.core.epg.EpgSource] in the
 * [store] (an entry with the same URL is reused) so it shows under Settings › Sources & guide, then is
 * downloaded keyed by that source id. Returns the programmes written; a feed that fails is remembered
 * as failed on its row and does not cost the ones that work.
 */
suspend fun runSemiAutoEpgSync(source: SourceEntity, epgRepository: EpgRepository, store: EpgSourceStore): Int {
    val urls = epgRepository.guideUrls(source)
    var total = 0
    for (url in urls) {
        val epgSource = store.getAll().firstOrNull { it.url == url } ?: store.add(source.name, url, source.userAgent)
        val now = System.currentTimeMillis()
        try {
            total += epgRepository.refreshUrl(epgSource.id, epgSource.url, epgSource.userAgent) { _, _ -> }
            store.setSynced(epgSource.id, now, null)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            store.setSynced(epgSource.id, now, e.message)
        }
    }
    return total
}
