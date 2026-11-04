package tv.own.owntv.mobile.poterx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Poterx: what the panel last said about the active line's expiry, shared by everything that shows it
 * (the account line in the shell's chrome, the "ends soon" notice, the Account page). One `user_info`
 * request per playlist per [TTL_MS]; a sign-out clears it, a sign-in asks again.
 */
object PoterxAccountStatus {
    private const val TTL_MS = 6L * 60 * 60 * 1000

    private val _expiryMs = MutableStateFlow<Long?>(null)

    /** The active line's expiry in epoch ms; null until the panel answered, or when it names none. */
    val expiryMs: StateFlow<Long?> = _expiryMs.asStateFlow()

    @Volatile private var fetchedAt = 0L
    @Volatile private var fetchedFor = -1L

    /** The expiry, from the last answer while it is fresh, else from the panel. Null when it cannot be known. */
    suspend fun refresh(sourceDao: SourceDao, settings: SettingsRepository, xtream: XtreamClient, force: Boolean = false): Long? =
        withContext(Dispatchers.IO) {
            val source = PoterxAccount.activeXtreamSource(sourceDao, settings) ?: run { clear(); return@withContext null }
            val now = System.currentTimeMillis()
            if (!force && source.id == fetchedFor && now - fetchedAt < TTL_MS) return@withContext _expiryMs.value
            val found = runCatching { xtream.accountExpiryMs(source) }.getOrNull() ?: return@withContext _expiryMs.value.takeIf { source.id == fetchedFor }
            fetchedFor = source.id
            fetchedAt = now
            _expiryMs.value = found
            found
        }

    fun clear() {
        fetchedFor = -1L
        fetchedAt = 0L
        _expiryMs.value = null
    }
}
