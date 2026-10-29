package tv.own.owntv.mobile.ui.components

import androidx.paging.ItemSnapshotList

/**
 * The index in the WHOLE paged list (placeholders counted) of the first loaded item that matches
 * [predicate], or -1 when no loaded item does.
 *
 * `ItemSnapshotList.items` holds only the loaded window. Once the pager has dropped pages from the
 * front (it keeps at most `maxSize` items), an index into `items` is that window's, not the list's:
 * scrolling a grid to it lands hundreds of rows too early. A title starred under "D" in an A–Z list
 * then threw the customer back to a title under "A". Every index that goes to `scrollToItem` must
 * come from here.
 */
inline fun <T : Any> ItemSnapshotList<T>.indexOfLoaded(predicate: (T) -> Boolean): Int {
    val at = items.indexOfFirst { it != null && predicate(it) }
    return if (at < 0) -1 else placeholdersBefore + at
}

/** The index in the whole list of the last loaded item, or -1 when nothing is loaded. */
fun <T : Any> ItemSnapshotList<T>.lastLoadedIndex(): Int {
    val at = items.indexOfLast { it != null }
    return if (at < 0) -1 else placeholdersBefore + at
}
