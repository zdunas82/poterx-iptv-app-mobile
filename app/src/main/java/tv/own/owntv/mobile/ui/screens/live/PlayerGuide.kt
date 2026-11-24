package tv.own.owntv.mobile.ui.screens.live

import tv.own.owntv.core.live.EpgNowNext

/**
 * The guide entry to show over the picture.
 *
 * At the live edge that is what is on now. Behind it — a rewind, or a programme replayed from the
 * archive — it is what was on at the instant being watched: the stored guide's answer when it has
 * one, otherwise the live entry for as long as that programme still covers the instant. Never a
 * programme that was not on at that moment; no card is better than a wrong one.
 *
 * @param live what is on the channel now
 * @param archive what the stored guide says was on at [watchingMs], null when it does not know
 * @param watchingMs the instant on screen, null at the live edge
 */
internal fun playerGuideFor(live: EpgNowNext?, archive: EpgNowNext?, watchingMs: Long?): EpgNowNext? = when {
    watchingMs == null -> live
    archive != null -> archive
    else -> live?.takeIf { entry -> entry.now?.let { watchingMs >= it.startMs && watchingMs < it.stopMs } == true }
}
