package tv.own.owntv.mobile.ui.components

import androidx.paging.ItemSnapshotList
import org.junit.Assert.assertEquals
import org.junit.Test

class PagingIndexTest {

    // An A–Z list scrolled far down: the pager has dropped the first 600 titles and keeps a window.
    private val scrolled = ItemSnapshotList(600, 100, listOf("Aquaman 2", "Doktor Strange", "Diuna"))

    @Test
    fun `the index counts the titles the pager dropped before the loaded window`() {
        assertEquals(601, scrolled.indexOfLoaded { it == "Doktor Strange" })
        assertEquals("Doktor Strange", scrolled[601])
    }

    @Test
    fun `a title that is not loaded has no index`() {
        assertEquals(-1, scrolled.indexOfLoaded { it == "Zorro" })
    }

    @Test
    fun `the last loaded title is found in the whole list`() {
        assertEquals(602, scrolled.lastLoadedIndex())
        assertEquals(-1, ItemSnapshotList<String>(0, 0, emptyList()).lastLoadedIndex())
    }

    @Test
    fun `without dropped pages the index is the plain one`() {
        val top = ItemSnapshotList(0, 50, listOf("A", "B", "C"))
        assertEquals(2, top.indexOfLoaded { it == "C" })
    }
}
