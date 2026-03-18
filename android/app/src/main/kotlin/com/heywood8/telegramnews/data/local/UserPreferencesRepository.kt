package com.heywood8.telegramnews.data.local

import android.content.SharedPreferences
import com.heywood8.telegramnews.domain.model.PhotoLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val KEY_SHOW_CHANNEL_ICONS = "show_channel_icons"
        private const val KEY_PHOTO_LAYOUT = "photo_layout"
    }

    private val _showChannelIcons = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CHANNEL_ICONS, true))
    val showChannelIcons: StateFlow<Boolean> = _showChannelIcons.asStateFlow()

    fun setShowChannelIcons(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CHANNEL_ICONS, show).apply()
        _showChannelIcons.value = show
    }

    private val _photoLayout = MutableStateFlow(
        PhotoLayout.entries.firstOrNull {
            it.name == (prefs.getString(KEY_PHOTO_LAYOUT, PhotoLayout.ABOVE.name) ?: PhotoLayout.ABOVE.name)
        } ?: PhotoLayout.ABOVE
    )
    val photoLayout: StateFlow<PhotoLayout> = _photoLayout.asStateFlow()

    fun setPhotoLayout(layout: PhotoLayout) {
        prefs.edit().putString(KEY_PHOTO_LAYOUT, layout.name).apply()
        _photoLayout.value = layout
    }
}
