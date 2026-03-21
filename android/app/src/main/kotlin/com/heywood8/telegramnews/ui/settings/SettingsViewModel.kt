package com.heywood8.telegramnews.ui.settings

import androidx.lifecycle.ViewModel
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.domain.model.PhotoLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    fun setShowChannelIcons(show: Boolean) {
        userPrefs.setShowChannelIcons(show)
    }

    val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

    fun setPhotoLayout(layout: PhotoLayout) {
        userPrefs.setPhotoLayout(layout)
    }
}
