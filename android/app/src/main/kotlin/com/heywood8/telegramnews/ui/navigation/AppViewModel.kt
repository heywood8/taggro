package com.heywood8.telegramnews.ui.navigation

import androidx.lifecycle.ViewModel
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    telegramRepository: TelegramRepository,
) : ViewModel() {
    val authState: Flow<AuthState> = telegramRepository.authState
}
