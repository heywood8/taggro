package com.heywood8.telegramnews.domain.repository

import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface TelegramRepository {
    val authState: Flow<AuthState>
    fun observeNewMessages(channels: List<String>): Flow<Message>
    suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message>
    suspend fun searchChannel(query: String): List<Channel>
    suspend fun isLoggedIn(): Boolean
    suspend fun sendPhoneNumber(phone: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun logOut()
}
