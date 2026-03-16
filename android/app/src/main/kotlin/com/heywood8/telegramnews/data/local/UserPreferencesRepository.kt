package com.heywood8.telegramnews.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val KEY_SHOW_CHANNEL_ICONS = "show_channel_icons"
    }

    val showChannelIcons: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHOW_CHANNEL_ICONS) {
                trySend(prefs.getBoolean(KEY_SHOW_CHANNEL_ICONS, true))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(prefs.getBoolean(KEY_SHOW_CHANNEL_ICONS, true))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setShowChannelIcons(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CHANNEL_ICONS, show).apply()
    }
}
