package com.heywood8.telegramnews.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val filterUseCase: FilterUseCase,
    private val messageDao: MessageDao,
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    companion object {
        const val USER_ID = 0L
    }

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    val subscriptions: StateFlow<List<Subscription>> = localRepo.observeSubscriptions(USER_ID)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

    val filteredMessages: StateFlow<List<Message>> = combine(
        messageDao.observeAll().map { entities ->
            entities.map { e -> Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp) }
        },
        _selectedChannel
    ) { msgs, channel ->
        if (channel == null) msgs else msgs.filter { it.channel == channel }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Real-time: persist new messages as they arrive
        viewModelScope.launch {
            localRepo.observeSubscriptions(USER_ID)
                .flatMapLatest { subs ->
                    val channels = subs.filter { it.active }.map { it.channel }
                    telegramRepo.observeNewMessages(channels)
                        .filter { msg ->
                            val sub = subs.find { it.channel == msg.channel }
                            sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                        }
                }
                .collect { msg ->
                    messageDao.insertAll(listOf(
                        MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
                    ))
                    messageDao.pruneChannel(msg.channel)
                }
        }
        // On load: fetch recent messages from each subscribed channel
        viewModelScope.launch {
            val subs = localRepo.observeSubscriptions(USER_ID).first()
            for (sub in subs.filter { it.active }) {
                try {
                    val messages = telegramRepo.fetchMessagesSince(sub.channel, 0)
                    val filtered = messages.filter {
                        filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
                    }
                    if (filtered.isNotEmpty()) {
                        messageDao.insertAll(filtered.map { msg ->
                            MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
                        })
                        messageDao.pruneChannel(sub.channel)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun selectChannel(channel: String?) {
        _selectedChannel.value = channel
    }
}
