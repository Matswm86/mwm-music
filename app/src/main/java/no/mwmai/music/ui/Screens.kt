package no.mwmai.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.music.Library
import no.mwmai.music.data.Playlist
import no.mwmai.music.data.UserData
import no.mwmai.music.model.Track
import no.mwmai.music.playback.PlayerState
import no.mwmai.music.ui.theme.Deck
import no.mwmai.music.ui.theme.Mono
import no.mwmai.music.util.formatTime

@Composable
fun LibraryScreen(
    library: Library,
    user: UserData,
    currentKey: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onFavorite: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(library, query) {
        val q = query.trim()
        if (q.isEmpty()) library.tracks
        else library.tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search ${library.tracks.size} tracks", color = Deck.TextDim) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Deck.TextDim) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Deck.TextDim)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Deck.PhosphorDim,
                unfocusedBorderColor = Deck.Edge,
                cursorColor = Deck.Phosphor,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        when {
            !library.loaded -> EmptyNote("Reading your music…")
            library.tracks.isEmpty() -> EmptyNote(
                "No music found on this phone yet. Copy some files into the Music folder and come back.",
            )
            shown.isEmpty() -> EmptyNote("Nothing matches \"${query.trim()}\".")
            else -> TrackList(shown, currentKey, user::isFavorite, onPlay, onFavorite) { track, _ ->
                listOf(
                    RowAction("Play next") { onPlayNext(track) },
                    RowAction("Add to playlist") { onAddToPlaylist(listOf(track.key)) },
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    tracks: List<Track>,
    currentKey: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onFavorite: (String) -> Unit,
    onAddToPlaylist: (List<String>) -> Unit,
) {
    if (tracks.isEmpty()) return EmptyNote("Tap the heart on any track and it shows up here.")
    Column(Modifier.fillMaxSize()) {
        ListHeader("${tracks.size} favorite${if (tracks.size == 1) "" else "s"}") {
            TextButton(onClick = { onAddToPlaylist(tracks.map { it.key }) }) { Text("Save as playlist") }
        }
        TrackList(tracks, currentKey, { true }, onPlay, onFavorite) { track, _ ->
            listOf(
                RowAction("Play next") { onPlayNext(track) },
                RowAction("Add to playlist") { onAddToPlaylist(listOf(track.key)) },
            )
        }
    }
}

@Composable
fun QueueScreen(
    state: PlayerState,
    user: UserData,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onFavorite: (String) -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    if (state.queue.isEmpty()) return EmptyNote("Nothing is queued. Tap a track in the library and the whole list lines up here.")
    Column(Modifier.fillMaxSize()) {
        ListHeader("${state.queue.size} in queue") {
            TextButton(onClick = onSaveAsPlaylist) { Text("Save as playlist") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.queue) { index, item ->
                TrackRow(
                    number = index + 1,
                    title = item.title.ifBlank { "Untitled" },
                    subtitle = item.artist,
                    trailing = "",
                    current = index == state.index,
                    favorite = user.isFavorite(item.key),
                    onClick = { onJump(index) },
                    onFavorite = { onFavorite(item.key) },
                    actions = listOf(RowAction("Remove from queue") { onRemove(index) }),
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    library: Library,
    user: UserData,
    openId: Long?,
    currentKey: String?,
    onOpen: (Long?) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEdit: ((UserData) -> UserData) -> Unit,
    onNew: () -> Unit,
) {
    val open = user.playlists.firstOrNull { it.id == openId }
    if (open != null) {
        return PlaylistDetail(open, library, user, currentKey, { onOpen(null) }, onPlay, onPlayNext, onEdit)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onNew)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Deck.Phosphor)
            Text("New playlist", color = Deck.Phosphor, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
        }
        if (user.playlists.isEmpty()) {
            EmptyNote("No playlists yet. Make one here, or use \"Add to playlist\" on any track.")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(user.playlists, key = { _, p -> p.id }) { _, playlist ->
                PlaylistLine(playlist, library, onOpen = { onOpen(playlist.id) }, onPlay = onPlay, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun PlaylistLine(
    playlist: Playlist,
    library: Library,
    onOpen: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onEdit: ((UserData) -> UserData) -> Unit,
) {
    val tracks = library.resolve(playlist.trackKeys)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = Deck.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${tracks.size} track${if (tracks.size == 1) "" else "s"}  ·  ${formatTime(tracks.sumOf { it.durationMs })}",
                color = Deck.TextDim,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${playlist.name}", tint = Deck.Phosphor)
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Deck.TextDim)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = true })
            }
        }
    }
    if (renaming) {
        NameDialog("Rename playlist", playlist.name, "Rename", { name ->
            onEdit { it.renamePlaylist(playlist.id, name) }
            renaming = false
        }, { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            "Delete \"${playlist.name}\"?",
            "The playlist goes away. The music files stay on the phone.",
            "Delete",
            { onEdit { it.deletePlaylist(playlist.id) }; deleting = false },
            { deleting = false },
        )
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    library: Library,
    user: UserData,
    currentKey: String?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEdit: ((UserData) -> UserData) -> Unit,
) {
    val tracks = library.resolve(playlist.trackKeys)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to playlists", tint = Deck.Text)
            }
            Text(
                playlist.name,
                color = Deck.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${tracks.size}  ·  ${formatTime(tracks.sumOf { it.durationMs })}",
                color = Deck.TextDim,
                fontFamily = Mono,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        if (tracks.isEmpty()) {
            return@Column EmptyNote("Empty. Go to the library, open a track's menu and choose \"Add to playlist\".")
        }
        TrackList(tracks, currentKey, user::isFavorite, onPlay, { key -> onEdit { it.toggleFavorite(key) } }) { track, _ ->
            listOf(
                RowAction("Play next") { onPlayNext(track) },
                RowAction("Move up") { onEdit { it.moveInPlaylist(playlist.id, track.key, -1) } },
                RowAction("Move down") { onEdit { it.moveInPlaylist(playlist.id, track.key, 1) } },
                RowAction("Remove from playlist") { onEdit { it.removeFromPlaylist(playlist.id, track.key) } },
            )
        }
    }
}

/** Tapping a row queues the whole visible list and starts at that row. */
@Composable
private fun TrackList(
    tracks: List<Track>,
    currentKey: String?,
    isFavorite: (String) -> Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onFavorite: (String) -> Unit,
    actionsFor: (Track, Int) -> List<RowAction>,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.key }) { index, track ->
            TrackRow(
                number = index + 1,
                title = track.title,
                subtitle = "${track.artist}  ·  ${track.album}",
                trailing = formatTime(track.durationMs),
                current = track.key == currentKey,
                favorite = isFavorite(track.key),
                onClick = { onPlay(tracks, index) },
                onFavorite = { onFavorite(track.key) },
                actions = actionsFor(track, index),
            )
        }
    }
}

@Composable
private fun ListHeader(text: String, action: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Deck.Ink)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Deck.TextDim, fontFamily = Mono, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        action()
    }
}
