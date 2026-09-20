package no.mwmai.music

import no.mwmai.music.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataTest {
    @Test
    fun favoriteTogglesOnAndOff() {
        val on = UserData().toggleFavorite("/a.mp3")
        assertTrue(on.isFavorite("/a.mp3"))
        assertFalse(on.toggleFavorite("/a.mp3").isFavorite("/a.mp3"))
    }

    @Test
    fun blankPlaylistNameIsRefused() {
        assertTrue(UserData().createPlaylist("   ").playlists.isEmpty())
    }

    @Test
    fun duplicateNamesGetANumber() {
        val data = UserData().createPlaylist("Gym").createPlaylist("gym").createPlaylist("Gym")
        assertEquals(listOf("Gym", "gym (2)", "Gym (3)"), data.playlists.map { it.name })
        assertEquals(listOf(1L, 2L, 3L), data.playlists.map { it.id })
    }

    @Test
    fun renamingToItsOwnNameKeepsTheName() {
        val data = UserData().createPlaylist("Gym").renamePlaylist(1, "Gym")
        assertEquals("Gym", data.playlists.single().name)
    }

    @Test
    fun aTrackSitsInAPlaylistOnce() {
        val data = UserData().createPlaylist("Gym")
            .addToPlaylist(1, listOf("/a.mp3", "/b.mp3"))
            .addToPlaylist(1, listOf("/a.mp3", "/c.mp3"))
        assertEquals(listOf("/a.mp3", "/b.mp3", "/c.mp3"), data.playlists.single().trackKeys)
    }

    @Test
    fun moveIsClampedToTheEnds() {
        val base = UserData().createPlaylist("Gym", listOf("/a", "/b", "/c"))
        assertEquals(listOf("/b", "/a", "/c"), base.moveInPlaylist(1, "/a", 1).playlists[0].trackKeys)
        assertEquals(listOf("/a", "/b", "/c"), base.moveInPlaylist(1, "/a", -1).playlists[0].trackKeys)
        assertEquals(listOf("/a", "/b", "/c"), base.moveInPlaylist(1, "/c", 5).playlists[0].trackKeys)
        assertEquals(listOf("/c", "/a", "/b"), base.moveInPlaylist(1, "/c", -9).playlists[0].trackKeys)
    }

    @Test
    fun removeAndDelete() {
        val data = UserData().createPlaylist("Gym", listOf("/a", "/b")).removeFromPlaylist(1, "/a")
        assertEquals(listOf("/b"), data.playlists.single().trackKeys)
        assertTrue(data.deletePlaylist(1).playlists.isEmpty())
    }

    @Test
    fun deletedIdsAreNeverReused() {
        val data = UserData().createPlaylist("A").deletePlaylist(1).createPlaylist("B")
        assertEquals(2L, data.playlists.single().id)
    }

    @Test
    fun sessionIndexIsClamped() {
        assertEquals(1, UserData().withSession(listOf("/a", "/b"), 7).lastIndex)
        assertEquals(0, UserData().withSession(emptyList(), 3).lastIndex)
    }
}
