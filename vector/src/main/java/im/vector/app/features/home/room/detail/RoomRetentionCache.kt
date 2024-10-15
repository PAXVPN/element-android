package im.vector.app.features.home.room.detail

import kotlinx.coroutines.flow.firstOrNull
//import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.query.QueryStringValue
import org.matrix.android.sdk.api.session.Session
//import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.flow.flow

class RoomRetentionCache {
    private val retentionCache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(val maxLifetime: Long, val timestamp: Long)

    fun getMaxLifetime(roomId: String): Long? {
        val cacheEntry = retentionCache[roomId]
        if (cacheEntry != null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - cacheEntry.timestamp > FIFTEEN_MINUTES) {
                retentionCache.remove(roomId)
                return null
            }
            return cacheEntry.maxLifetime
        }
        return null
    }

    fun setMaxLifetime(roomId: String, maxLifetime: Long) {
        val currentTime = System.currentTimeMillis()
        retentionCache[roomId] = CacheEntry(maxLifetime, currentTime)
    }

    companion object {
        private const val FIFTEEN_MINUTES = 15 * 60 * 1000
    }
}

suspend fun getCachedOrFetchMaxLifetime(
        roomId: String,
        cache: RoomRetentionCache,
        session: Session
): Long {
    return cache.getMaxLifetime(roomId) ?: fetchAndCacheMaxLifetime(roomId, cache, session)
}

private suspend fun fetchAndCacheMaxLifetime(
        roomId: String,
        cache: RoomRetentionCache,
        session: Session
): Long {
    val maxLifetime = session.getMaxLifetime(roomId)
    cache.setMaxLifetime(roomId, maxLifetime)
    return maxLifetime
}

suspend fun Session.getMaxLifetime(roomId: String): Long {
    val room = this.getRoom(roomId) ?: return 0L
    val flowRoom = room.flow()

    // Selection of all events of the type `m.room.retention`
    val stateEventsFlow = flowRoom.liveStateEvents(setOf("m.room.retention"), QueryStringValue.IsEmpty)
    val retentionEvents = stateEventsFlow.firstOrNull()

    // Search for `m.room.retention` event to retrieve max_lifetime
    val retentionEvent = retentionEvents?.find { it.type == "m.room.retention" }

    // Extract `max_lifetime`
    return (retentionEvent?.content?.get("max_lifetime") as? Number)?.toLong() ?: 0L
}
