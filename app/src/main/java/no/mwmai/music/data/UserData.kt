package no.mwmai.music.data

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val trackKeys: List<String> = emptyList(),
)

/**
 * Everything the listener owns: favorites, playlists and the queue that was
 * loaded when the app last closed. Immutable, every edit returns a new value,
 * so the rules are testable on the JVM without Android.
 */
@Serializable
data class UserData(
    val favorites: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val nextPlaylistId: Long = 1,
    val lastQueue: List<String> = emptyList(),
    val lastIndex: Int = 0,
) {
    fun isFavorite(key: String): Boolean = key in favorites

    fun toggleFavorite(key: String): UserData =
        copy(favorites = if (key in favorites) favorites - key else favorites + key)

    /** Blank names are refused; a taken name gets " (2)", " (3)" and so on. */
    fun createPlaylist(name: String, trackKeys: List<String> = emptyList()): UserData {
        val clean = name.trim()
        if (clean.isEmpty()) return this
        val playlist = Playlist(nextPlaylistId, uniqueName(clean, exceptId = null), trackKeys.distinct())
        return copy(playlists = playlists + playlist, nextPlaylistId = nextPlaylistId + 1)
    }

    fun renamePlaylist(id: Long, name: String): UserData {
        val clean = name.trim()
        if (clean.isEmpty()) return this
        return edit(id) { it.copy(name = uniqueName(clean, exceptId = id)) }
    }

    fun deletePlaylist(id: Long): UserData = copy(playlists = playlists.filterNot { it.id == id })

    /** A track sits in a playlist once; adding it again is a no-op. */
    fun addToPlaylist(id: Long, keys: List<String>): UserData =
        edit(id) { it.copy(trackKeys = (it.trackKeys + keys).distinct()) }

    fun removeFromPlaylist(id: Long, key: String): UserData =
        edit(id) { it.copy(trackKeys = it.trackKeys - key) }

    /** Moves one row by [delta] places, clamped to the ends of the list. */
    fun moveInPlaylist(id: Long, key: String, delta: Int): UserData = edit(id) { playlist ->
        val from = playlist.trackKeys.indexOf(key)
        if (from < 0) return@edit playlist
        val to = (from + delta).coerceIn(0, playlist.trackKeys.lastIndex)
        if (to == from) return@edit playlist
        val keys = playlist.trackKeys.toMutableList()
        keys.add(to, keys.removeAt(from))
        playlist.copy(trackKeys = keys)
    }

    fun withSession(queue: List<String>, index: Int): UserData =
        copy(lastQueue = queue, lastIndex = if (queue.isEmpty()) 0 else index.coerceIn(0, queue.lastIndex))

    private fun edit(id: Long, change: (Playlist) -> Playlist): UserData =
        copy(playlists = playlists.map { if (it.id == id) change(it) else it })

    private fun uniqueName(name: String, exceptId: Long?): String {
        val taken = playlists.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while ("$name ($n)".lowercase() in taken) n++
        return "$name ($n)"
    }
}
