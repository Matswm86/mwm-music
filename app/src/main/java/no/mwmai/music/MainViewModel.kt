package no.mwmai.music

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwmai.music.data.LibraryScanner
import no.mwmai.music.data.UserData
import no.mwmai.music.data.UserStore
import no.mwmai.music.model.Track
import no.mwmai.music.playback.PlayerConnection

data class Library(
    val loaded: Boolean = false,
    val tracks: List<Track> = emptyList(),
) {
    val byKey: Map<String, Track> = tracks.associateBy { it.key }

    /** Playlists can name files that have since been deleted; those are skipped. */
    fun resolve(keys: Collection<String>): List<Track> = keys.mapNotNull { byKey[it] }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = UserStore(app)
    val player = PlayerConnection(app)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library

    private val _user = MutableStateFlow(UserData())
    val user: StateFlow<UserData> = _user

    private var userLoaded = false
    private var restored = false

    init {
        viewModelScope.launch {
            _user.value = withContext(Dispatchers.IO) { store.load() }
            userLoaded = true
            restoreSessionIfReady()
            // Every later edit is written straight back to disk.
            _user.drop(1).collectLatest { data -> withContext(Dispatchers.IO) { store.save(data) } }
        }
        viewModelScope.launch {
            player.state.map { it.connected }.distinctUntilChanged().collectLatest { restoreSessionIfReady() }
        }
        viewModelScope.launch {
            player.state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
                while (playing) {
                    player.tick()
                    delay(500)
                }
            }
        }
        // Remember the queue so the app reopens where it was closed.
        viewModelScope.launch {
            player.state.map { s -> s.queue.map { it.key } to s.index }
                .distinctUntilChanged()
                .collectLatest { (keys, index) ->
                    if (restored && keys.isNotEmpty()) edit { it.withSession(keys, index) }
                }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { LibraryScanner.scan(getApplication()) }
            _library.value = Library(loaded = true, tracks = tracks)
            restoreSessionIfReady()
        }
    }

    private fun restoreSessionIfReady() {
        if (restored || !userLoaded || !_library.value.loaded || !player.state.value.connected) return
        restored = true
        val saved = _user.value
        val tracks = _library.value.resolve(saved.lastQueue)
        val current = saved.lastQueue.getOrNull(saved.lastIndex)
        player.restore(tracks, tracks.indexOfFirst { it.key == current }.coerceAtLeast(0))
    }

    fun edit(change: (UserData) -> UserData) = _user.update(change)

    fun openExternal(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Audio file"
        viewModelScope.launch {
            // The controller connects a moment after a cold start.
            repeat(40) {
                if (player.state.value.connected) {
                    restored = true
                    return@launch player.playUri(uri, name)
                }
                delay(100)
            }
        }
    }

    override fun onCleared() {
        player.release()
    }
}
