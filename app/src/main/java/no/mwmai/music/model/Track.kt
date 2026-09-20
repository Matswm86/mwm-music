package no.mwmai.music.model

/**
 * One audio file on the device. [key] is the file path: MediaStore row ids are
 * reassigned when the media database is rebuilt, and a playlist that points at
 * ids would silently turn into a different playlist. A path survives that.
 */
data class Track(
    val key: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)
