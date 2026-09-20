package no.mwmai.music.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import no.mwmai.music.model.Track

data class QueueItem(val key: String, val title: String, val artist: String)

data class PlayerState(
    val connected: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val art: ImageBitmap? = null,
) {
    val currentKey: String? get() = queue.getOrNull(index)?.key
}

/** The UI's handle on [PlaybackService]: commands go in, [state] comes out. */
class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val future: ListenableFuture<MediaController> = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private var lastArt: ByteArray? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    init {
        future.addListener({
            val c = try {
                future.get()
            } catch (e: java.util.concurrent.ExecutionException) {
                android.util.Log.e("PlayerConnection", "could not reach the playback service", e)
                return@addListener
            }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player, events)
            })
            publish(c, null)
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun publish(player: Player, events: Player.Events?) {
        val queueChanged = events == null || events.contains(Player.EVENT_TIMELINE_CHANGED)
        val meta = player.mediaMetadata
        val art = meta.artworkData
        // The same cover arrives with every event; decode it once per track.
        val sameArt = art != null && lastArt != null && (art === lastArt || art.contentEquals(lastArt))
        _state.update { old ->
            old.copy(
                connected = true,
                queue = if (queueChanged) readQueue(player) else old.queue,
                index = if (player.mediaItemCount == 0) -1 else player.currentMediaItemIndex,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it > 0 } ?: 0,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                art = when {
                    art == null -> null
                    sameArt && old.art != null -> old.art
                    else -> decode(art)
                },
            )
        }
        lastArt = art
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return bitmap?.asImageBitmap()
    }

    private fun readQueue(player: Player): List<QueueItem> = List(player.mediaItemCount) { i ->
        val item = player.getMediaItemAt(i)
        QueueItem(
            key = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
        )
    }

    /** Called on a timer while playing; position has no change event of its own. */
    fun tick() {
        val c = controller ?: return
        _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0)
        c.prepare()
        c.play()
    }

    /** Loads a queue without starting it: what the app does on a cold start. */
    fun restore(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        if (tracks.isEmpty() || c.mediaItemCount > 0) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), 0)
        c.prepare()
    }

    fun playUri(uri: Uri, name: String) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track), 0)
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0)
        c.prepare()
        c.play()
    }

    fun removeFromQueue(index: Int) {
        controller?.takeIf { index in 0 until it.mediaItemCount }?.removeMediaItem(index)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        if (c.isPlaying) return c.pause()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0)
        c.play()
    }

    /** Winamp's stop: silence, and back to the start of the track. */
    fun stop() {
        val c = controller ?: return
        c.pause()
        c.seekTo(0)
        tick()
    }

    fun next() { controller?.seekToNextMediaItem() }

    /** Past three seconds "previous" restarts the track, before that it goes back one. */
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        tick()
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        MediaController.releaseFuture(future)
        controller = null
    }

    private fun Track.toMediaItem(): MediaItem {
        val parsed = Uri.parse(uri)
        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(parsed)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsed).build())
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build(),
            )
            .build()
    }
}
