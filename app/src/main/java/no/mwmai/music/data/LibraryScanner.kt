package no.mwmai.music.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import no.mwmai.music.model.Track

/**
 * Lists every audio file Android has indexed. Whatever the device can decode
 * shows up here: mp3, m4a/aac, flac, ogg, opus, wav and the rest.
 */
object LibraryScanner {
    private const val UNKNOWN = "<unknown>"

    fun scan(context: Context): List<Track> {
        val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        // Ringtones, alarms and notification sounds are audio too; nobody wants
        // them in a music library.
        val where = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(audio, columns, where, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val uri = ContentUris.withAppendedId(audio, rowId).toString()
                val fileName = c.getString(name).orEmpty()
                tracks += Track(
                    key = c.getString(data) ?: uri,
                    uri = uri,
                    title = c.getString(title)?.takeIf { it.isNotBlank() }
                        ?: fileName.substringBeforeLast('.').ifBlank { "Untitled" },
                    artist = c.getString(artist).clean("Unknown artist"),
                    album = c.getString(album).clean("Unknown album"),
                    durationMs = c.getLong(duration),
                )
            }
        }
        return tracks.sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
    }

    private fun String?.clean(fallback: String): String =
        if (isNullOrBlank() || this == UNKNOWN) fallback else this
}
