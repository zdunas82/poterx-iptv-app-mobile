package tv.own.owntv.mobile.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.parser.XtEpgEntry

class PlayerGuideTest {

    private fun minute(m: Int) = m * 60_000L

    // 18:55–19:15 and 19:15–19:50, in minutes of an arbitrary day.
    private val earlier = XtEpgEntry("Uwaga", null, minute(1135), minute(1155))
    private val onNow = XtEpgEntry("Na Wspólnej", null, minute(1155), minute(1190))
    private val later = XtEpgEntry("The Floor", null, minute(1190), minute(1230))

    private val live = EpgNowNext(onNow, later)
    private val archive = EpgNowNext(earlier, onNow)

    @Test
    fun `at the live edge the card is what is on now`() {
        assertEquals(live, playerGuideFor(live, archive = null, watchingMs = null))
        // A stale archive answer from a replay that just ended does not outlive it.
        assertEquals(live, playerGuideFor(live, archive, watchingMs = null))
    }

    @Test
    fun `a replayed programme is named, not the one on air now`() {
        assertEquals(archive, playerGuideFor(live, archive, watchingMs = minute(1136)))
    }

    @Test
    fun `a short rewind inside the programme on air keeps its card without the stored guide`() {
        assertEquals(live, playerGuideFor(live, archive = null, watchingMs = minute(1170)))
        assertEquals(live, playerGuideFor(live, archive = null, watchingMs = onNow.startMs))
    }

    @Test
    fun `behind the programme on air with no stored guide there is no card`() {
        assertNull(playerGuideFor(live, archive = null, watchingMs = minute(1136)))
        assertNull(playerGuideFor(live, archive = null, watchingMs = onNow.startMs - 1))
        assertNull(playerGuideFor(EpgNowNext(null, later), archive = null, watchingMs = minute(1170)))
        assertNull(playerGuideFor(null, archive = null, watchingMs = minute(1170)))
    }
}
