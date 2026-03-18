package com.heywood8.telegramnews.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import com.heywood8.telegramnews.data.local.entity.ReadMessageEntity
import com.heywood8.telegramnews.domain.model.MediaType
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.PhotoLayout
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val readMessageDao: ReadMessageDao,
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    companion object {
        const val USER_ID = 0L
    }

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

    // Must be called from a coroutine (e.g. produceState in a composable)
    suspend fun getPhotoPath(fileId: Int): String? = telegramRepo.downloadFile(fileId)

    val subscriptions: StateFlow<List<Subscription>> = localRepo.observeSubscriptions(USER_ID)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val filteredMessages: StateFlow<List<Message>> = combine(
        messageDao.observeAll().map { entities ->
            entities.map { e ->
                Message(
                    id = e.id,
                    channel = e.channel,
                    channelTitle = e.channelTitle.ifBlank { e.channel },
                    text = e.text,
                    timestamp = e.timestamp,
                    mediaType = e.mediaType,
                    photoFileId = e.photoFileId,
                )
            }
        },
        readMessageDao.observeReadIds().map { it.toHashSet() },
        _selectedChannel,
        subscriptions.map { subs -> subs.associateBy { it.channel } },
    ) { msgs, readSet, channel, subscriptionsByChannel ->
        val withRead = msgs.map { it.copy(isRead = it.id in readSet) }
        val channelFiltered = if (channel == null) withRead else withRead.filter { it.channel == channel }
        channelFiltered.filter { message ->
            val includePhotos = subscriptionsByChannel[message.channel]?.includePhotos ?: false
            includePhotos || message.mediaType != MediaType.PHOTO || message.text.isNotBlank()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Per-channel unread counts; "All" chip uses totalUnreadCount
    val unreadCounts: StateFlow<Map<String, Int>> = subscriptions
        .flatMapLatest { subs ->
            val channels = subs.filter { it.active }.map { it.channel }
            if (channels.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val channelFlows: List<Flow<Pair<String, Int>>> = channels.map { ch ->
                    readMessageDao.observeUnreadCount(ch).map { count -> ch to count }
                }
                combine(channelFlows) { pairs: Array<Pair<String, Int>> -> pairs.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val totalUnreadCount: StateFlow<Int> = readMessageDao.observeTotalUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun selectChannel(channel: String?) {
        _selectedChannel.value = channel
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            val timestamp = filteredMessages.value.find { it.id == id }?.timestamp ?: return@launch
            readMessageDao.markReadUpTo(timestamp)
        }
    }

    fun markChannelRead(channel: String) {
        viewModelScope.launch {
            readMessageDao.markChannelRead(channel)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            readMessageDao.markAllRead()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val subs = localRepo.observeSubscriptions(USER_ID).first()
            for (sub in subs.filter { it.active }) {
                try {
                    val messages = telegramRepo.fetchMessagesSince(sub.channel, 0)
                    val filtered = messages.filter { msg ->
                        (sub.includePhotos && msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
                            filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                    }
                    if (filtered.isNotEmpty()) {
                        messageDao.insertAll(filtered.map { msg ->
                            MessageEntity(
                                id = msg.id,
                                channel = msg.channel,
                                channelTitle = msg.channelTitle,
                                text = msg.text,
                                timestamp = msg.timestamp,
                                mediaType = msg.mediaType,
                                photoFileId = msg.photoFileId,
                            )
                        })
                        messageDao.pruneChannel(sub.channel)
                    }
                } catch (_: Exception) {}
            }
            _isRefreshing.value = false
        }
    }

    init {
        // Real-time: persist new messages as they arrive
        viewModelScope.launch {
            localRepo.observeSubscriptions(USER_ID)
                .flatMapLatest { subs ->
                    val channels = subs.filter { it.active }.map { it.channel }
                    telegramRepo.observeNewMessages(channels)
                        .filter { msg ->
                            val sub = subs.find { it.channel == msg.channel } ?: return@filter false
                            (sub.includePhotos && msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
                                filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                        }
                }
                .collect { msg ->
                    messageDao.insertAll(listOf(
                        MessageEntity(
                            id = msg.id,
                            channel = msg.channel,
                            channelTitle = msg.channelTitle,
                            text = msg.text,
                            timestamp = msg.timestamp,
                            mediaType = msg.mediaType,
                            photoFileId = msg.photoFileId,
                        )
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
                    val filtered = messages.filter { msg ->
                        (sub.includePhotos && msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
                            filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                    }
                    if (filtered.isNotEmpty()) {
                        messageDao.insertAll(filtered.map { msg ->
                            MessageEntity(
                                id = msg.id,
                                channel = msg.channel,
                                channelTitle = msg.channelTitle,
                                text = msg.text,
                                timestamp = msg.timestamp,
                                mediaType = msg.mediaType,
                                photoFileId = msg.photoFileId,
                            )
                        })
                        messageDao.pruneChannel(sub.channel)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
