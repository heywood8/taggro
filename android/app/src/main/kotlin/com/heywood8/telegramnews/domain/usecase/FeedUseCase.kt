package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

class FeedUseCase @Inject constructor(
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val filterUseCase: FilterUseCase
) {
    fun observeFeed(userId: Long): Flow<List<Message>> =
        localRepo.observeSubscriptions(userId).flatMapLatest { subs ->
            val channels = subs.filter { it.active }.map { it.channel }
            flow {
                val allMessages = telegramRepo.observeNewMessages(channels).toList()
                val filtered = allMessages.filter { msg ->
                    val sub = subs.find { it.channel == msg.channel }
                    sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                }
                if (filtered.isNotEmpty()) {
                    emit(filtered)
                }
            }
        }
}
