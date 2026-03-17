package com.heywood8.telegramnews.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val messageDao: MessageDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val channels = localRepo.getActiveChannels()
            for (channel in channels) {
                val lastSeen = localRepo.getLastSeen(channel)
                val messages = telegramRepo.fetchMessagesSince(channel, lastSeen)
                if (messages.isNotEmpty()) {
                    val entities = messages.map { msg ->
                        MessageEntity(
                            id = msg.id,
                            channel = msg.channel,
                            channelTitle = msg.channelTitle,
                            text = msg.text,
                            timestamp = msg.timestamp,
                            mediaType = msg.mediaType,
                        )
                    }
                    messageDao.insertAll(entities)
                    val maxId = messages.maxOf { it.id }
                    localRepo.updateLastSeen(channel, maxId)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
