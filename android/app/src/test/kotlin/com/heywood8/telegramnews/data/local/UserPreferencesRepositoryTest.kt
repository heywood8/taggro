package com.heywood8.telegramnews.data.local

import com.heywood8.telegramnews.FakeSharedPreferences
import com.heywood8.telegramnews.domain.model.PhotoLayout
import org.junit.Assert.*
import org.junit.Test

class UserPreferencesRepositoryTest {

    private fun repo(prefs: FakeSharedPreferences = FakeSharedPreferences()) =
        UserPreferencesRepository(prefs)

    @Test
    fun `showChannelIcons defaults to true when no preference stored`() {
        assertTrue(repo().showChannelIcons.value)
    }

    @Test
    fun `showChannelIcons reads stored preference on init`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putBoolean("show_channel_icons", false).apply()
        assertFalse(repo(prefs).showChannelIcons.value)
    }

    @Test
    fun `setShowChannelIcons updates state flow`() {
        val r = repo()
        r.setShowChannelIcons(false)
        assertFalse(r.showChannelIcons.value)
        r.setShowChannelIcons(true)
        assertTrue(r.showChannelIcons.value)
    }

    @Test
    fun `setShowChannelIcons persists to shared preferences`() {
        val prefs = FakeSharedPreferences()
        val r = repo(prefs)
        r.setShowChannelIcons(false)
        assertFalse(prefs.getBoolean("show_channel_icons", true))
    }

    @Test
    fun `photoLayout defaults to ABOVE when no preference stored`() {
        assertEquals(PhotoLayout.ABOVE, repo().photoLayout.value)
    }

    @Test
    fun `photoLayout reads stored preference on init`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("photo_layout", PhotoLayout.BELOW.name).apply()
        assertEquals(PhotoLayout.BELOW, repo(prefs).photoLayout.value)
    }

    @Test
    fun `photoLayout falls back to ABOVE for unknown stored value`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("photo_layout", "INVALID").apply()
        assertEquals(PhotoLayout.ABOVE, repo(prefs).photoLayout.value)
    }

    @Test
    fun `setPhotoLayout updates state flow`() {
        val r = repo()
        r.setPhotoLayout(PhotoLayout.LEFT)
        assertEquals(PhotoLayout.LEFT, r.photoLayout.value)
    }

    @Test
    fun `setPhotoLayout persists to shared preferences`() {
        val prefs = FakeSharedPreferences()
        val r = repo(prefs)
        r.setPhotoLayout(PhotoLayout.BELOW)
        assertEquals(PhotoLayout.BELOW.name, prefs.getString("photo_layout", null))
    }
}
