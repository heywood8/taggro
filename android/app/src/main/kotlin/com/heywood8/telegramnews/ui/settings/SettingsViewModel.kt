package com.heywood8.telegramnews.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val telegramRepo: TelegramRepository,
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    fun setShowChannelIcons(show: Boolean) {
        userPrefs.setShowChannelIcons(show)
    }

    fun logOut() {
        viewModelScope.launch {
            telegramRepo.logOut()
        }
    }
}
