package tv.own.owntv.mobile.poterx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoterxDailyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `never asked is due`() {
        assertTrue(PoterxDaily.due(0L, now))
    }

    @Test
    fun `asked today is not due again`() {
        assertFalse(PoterxDaily.due(now - 60_000L, now))
        assertFalse(PoterxDaily.due(now - PoterxDaily.DAY_MS + 1, now))
    }

    @Test
    fun `a day later it is due`() {
        assertTrue(PoterxDaily.due(now - PoterxDaily.DAY_MS, now))
        assertTrue(PoterxDaily.due(now - 3 * PoterxDaily.DAY_MS, now))
    }

    @Test
    fun `a clock moved back does not stretch the wait`() {
        // Asked while the box's clock ran a week ahead: by the right clock the next ask would be a week off.
        assertTrue(PoterxDaily.due(now + 7 * PoterxDaily.DAY_MS, now))
    }
}
