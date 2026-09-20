package no.mwmai.music.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Reads and writes [UserData] as one JSON file in the app's private storage. */
class UserStore(context: Context) {
    private val file = File(context.filesDir, "user_data.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): UserData {
        if (!file.exists()) return UserData()
        return try {
            json.decodeFromString(UserData.serializer(), file.readText())
        } catch (e: SerializationException) {
            quarantine(e)
        } catch (e: IOException) {
            quarantine(e)
        }
    }

    /** Written to a sibling file first so a kill mid-write cannot truncate the real one. */
    fun save(data: UserData) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(UserData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: IOException) {
            Log.e(TAG, "could not save playlists and favorites", e)
        }
    }

    // An unreadable file is kept beside the new one rather than overwritten, so
    // the playlists in it can still be recovered by hand.
    private fun quarantine(e: Exception): UserData {
        Log.e(TAG, "user_data.json unreadable, starting empty and keeping the old file", e)
        file.renameTo(File(file.parentFile, "user_data.broken.json"))
        return UserData()
    }

    private companion object {
        const val TAG = "UserStore"
    }
}
