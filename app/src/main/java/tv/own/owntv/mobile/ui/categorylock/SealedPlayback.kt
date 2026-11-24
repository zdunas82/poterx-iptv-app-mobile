package tv.own.owntv.mobile.ui.categorylock

import kotlinx.coroutines.flow.MutableSharedFlow
import tv.own.owntv.core.categorylock.PlayingCategories
import tv.own.owntv.core.categorylock.SealedPlaybackGuard
import tv.own.owntv.core.categorylock.sealedTileIndices
import tv.own.owntv.mobile.ui.screens.library.VodTuner
import tv.own.owntv.mobile.ui.screens.live.LiveTuner

/**
 * What the relock needs from the screens and tells them. The player screen sets [tiles] (each Multiview
 * tile's channel category) and [clear] (empty one tile) while the grid is up, and clears both when it
 * goes. [stopped] fires when the relock has stopped the channel or the title playing: the player page
 * and the floating window then close, rather than staying up with nothing in them.
 */
class SealedPlaybackHost {
    var tiles: (() -> List<Long?>)? = null
    var clear: ((tile: Int) -> Unit)? = null
    val stopped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/**
 * What the relock stops on the phone when a category it closes is playing: the live channel (full
 * screen, in the preview or replaying its archive), the film, episode or recording, and each Multiview
 * tile on a channel of a sealed category.
 */
internal fun sealedPlaybackGuard(live: LiveTuner, vod: VodTuner, host: SealedPlaybackHost): SealedPlaybackGuard =
    SealedPlaybackGuard(
        playing = {
            PlayingCategories(
                live = live.channel.value?.takeIf { live.hasStream || live.previewing.value }?.categoryId,
                liveInPlayer = live.hasStream,
                vod = vod.playing.value?.categoryId,
                tiles = host.tiles?.invoke().orEmpty(),
            )
        },
        stopLive = { _, _ ->
            live.stop()
            host.stopped.tryEmit(Unit)
        },
        stopVod = {
            vod.stop()
            host.stopped.tryEmit(Unit)
        },
        stopTiles = { sealed ->
            val tiles = host.tiles?.invoke().orEmpty()
            sealedTileIndices(tiles, sealed).forEach { host.clear?.invoke(it) }
        },
    )
