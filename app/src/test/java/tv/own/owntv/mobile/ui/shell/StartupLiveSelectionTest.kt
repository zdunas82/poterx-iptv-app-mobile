package tv.own.owntv.mobile.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class StartupLiveSelectionTest {

    private val channel = ChannelEntity(sourceId = 1, categoryId = 3, name = "X", streamUrl = "http://x/1.ts", remoteId = "c1")

    @Test
    fun `a channel from Search is taken once`() {
        val selection = StartupLiveSelection()
        selection.requestReveal(42)
        assertEquals(42L, selection.consumeReveal())
        assertNull(selection.consumeReveal())
    }

    @Test
    fun `taking it drops a Start on still waiting`() {
        val selection = StartupLiveSelection()
        selection.requestFavorites()
        selection.requestChannel(StartupTarget.ChannelIn(channel, guide = false))
        selection.requestReveal(42)
        assertEquals(42L, selection.consumeReveal())
        assertFalse(selection.consumeFavorites())
        assertNull(selection.consumeChannel(guide = false))
    }

    @Test
    fun `with no channel from Search, Start on is untouched`() {
        val selection = StartupLiveSelection()
        selection.requestChannel(StartupTarget.ChannelIn(channel, guide = false))
        assertNull(selection.consumeReveal())
        assertEquals(channel, selection.consumeChannel(guide = false))
    }
}
