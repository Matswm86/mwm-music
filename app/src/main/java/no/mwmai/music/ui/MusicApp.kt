package no.mwmai.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.mwmai.music.MainViewModel
import no.mwmai.music.ui.theme.Deck
import no.mwmai.music.ui.theme.Mono

enum class Tab(val label: String) {
    Library("LIBRARY"),
    Playlists("PLAYLISTS"),
    Favorites("FAVORITES"),
    Queue("QUEUE"),
}

@Composable
fun MusicApp(vm: MainViewModel, hasPermission: Boolean, onAskPermission: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    var openPlaylist by rememberSaveable { mutableStateOf<Long?>(null) }
    // Track keys waiting for the listener to say which playlist they go into.
    var adding by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var namingNewFor by rememberSaveable { mutableStateOf<List<String>?>(null) }

    BackHandler(enabled = tab == Tab.Playlists && openPlaylist != null) { openPlaylist = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(Deck.Panel)
            .systemBarsPadding(),
    ) {
        DeckPanel(
            state = state,
            isFavorite = state.currentKey?.let(user::isFavorite) == true,
            onTogglePlay = vm.player::togglePlay,
            onStop = vm.player::stop,
            onPrevious = vm.player::previous,
            onNext = vm.player::next,
            onSeek = vm.player::seekTo,
            onShuffle = vm.player::toggleShuffle,
            onRepeat = vm.player::cycleRepeat,
            onFavorite = { state.currentKey?.let { key -> vm.edit { it.toggleFavorite(key) } } },
        )
        Tabs(tab) { tab = it }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Deck.Ink),
        ) {
            when {
                !hasPermission -> PermissionNote(onAskPermission)
                tab == Tab.Library -> LibraryScreen(
                    library = library,
                    user = user,
                    currentKey = state.currentKey,
                    onPlay = vm.player::play,
                    onPlayNext = vm.player::playNext,
                    onFavorite = { key -> vm.edit { it.toggleFavorite(key) } },
                    onAddToPlaylist = { adding = it },
                )
                tab == Tab.Favorites -> FavoritesScreen(
                    tracks = library.resolve(user.favorites).sortedBy { it.title.lowercase() },
                    currentKey = state.currentKey,
                    onPlay = vm.player::play,
                    onPlayNext = vm.player::playNext,
                    onFavorite = { key -> vm.edit { it.toggleFavorite(key) } },
                    onAddToPlaylist = { adding = it },
                )
                tab == Tab.Queue -> QueueScreen(
                    state = state,
                    user = user,
                    onJump = vm.player::jumpTo,
                    onRemove = vm.player::removeFromQueue,
                    onFavorite = { key -> vm.edit { it.toggleFavorite(key) } },
                    onSaveAsPlaylist = { namingNewFor = state.queue.map { it.key } },
                )
                else -> PlaylistsScreen(
                    library = library,
                    user = user,
                    openId = openPlaylist,
                    currentKey = state.currentKey,
                    onOpen = { openPlaylist = it },
                    onPlay = vm.player::play,
                    onPlayNext = vm.player::playNext,
                    onEdit = vm::edit,
                    onNew = { namingNewFor = emptyList() },
                )
            }
        }
    }

    adding?.let { keys ->
        AddToPlaylistDialog(
            playlists = user.playlists,
            onPick = { playlist ->
                vm.edit { it.addToPlaylist(playlist.id, keys) }
                adding = null
            },
            onNew = {
                namingNewFor = keys
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
    namingNewFor?.let { keys ->
        NameDialog(
            heading = if (keys.isEmpty()) "New playlist" else "Save as playlist",
            initial = "",
            confirmLabel = "Save",
            onConfirm = { name ->
                vm.edit { it.createPlaylist(name, keys) }
                namingNewFor = null
            },
            onDismiss = { namingNewFor = null },
        )
    }
}

@Composable
private fun Tabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Deck.Panel),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .pressable(tab.label) { onSelect(tab) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.label,
                    color = if (on) Deck.Phosphor else Deck.TextDim,
                    fontFamily = Mono,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (on) Deck.Phosphor else Deck.Edge),
                )
            }
        }
    }
}

@Composable
private fun PermissionNote(onAsk: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyNote("MWM Music needs permission to read the audio files on this phone. It reads nothing else and has no internet access.")
        Button(onClick = onAsk) { Text("Allow access to music") }
    }
}
