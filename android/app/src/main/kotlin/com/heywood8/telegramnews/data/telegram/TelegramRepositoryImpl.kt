package com.heywood8.telegramnews.data.telegram

import android.content.Context
import android.os.Build
import com.heywood8.telegramnews.BuildConfig
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.telegram.core.TelegramFlow
import kotlinx.telegram.coroutines.checkAuthenticationCode
import kotlinx.telegram.coroutines.getChatHistory
import kotlinx.telegram.coroutines.getSupergroup
import kotlinx.telegram.coroutines.logOut
import kotlinx.telegram.coroutines.searchPublicChat
import kotlinx.telegram.coroutines.setTdlibParameters
import kotlinx.telegram.flows.authorizationStateFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TelegramRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = TelegramFlow()

    init {
        api.attachClient()
        scope.launch {
            api.authorizationStateFlow().collect { state ->
                if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
                    api.setTdlibParameters(
                        useTestDc = false,
                        databaseDirectory = context.filesDir.absolutePath + "/td",
                        filesDirectory = context.filesDir.absolutePath + "/td_files",
                        databaseEncryptionKey = byteArrayOf(),
                        useFileDatabase = true,
                        useChatInfoDatabase = true,
                        useMessageDatabase = true,
                        useSecretChats = false,
                        apiId = BuildConfig.TELEGRAM_API_ID,
                        apiHash = BuildConfig.TELEGRAM_API_HASH,
                        systemLanguageCode = "en",
                        deviceModel = Build.MODEL,
                        systemVersion = Build.VERSION.RELEASE,
                        applicationVersion = "1.0"
                    )
                }
            }
        }
    }

    override val authState: Flow<AuthState> = api.authorizationStateFlow()
        .map { state ->
            when (state) {
                is TdApi.AuthorizationStateWaitPhoneNumber -> AuthState.WaitingForPhone
                is TdApi.AuthorizationStateWaitCode -> AuthState.WaitingForCode
                is TdApi.AuthorizationStateWaitPassword -> AuthState.WaitingForPassword
                is TdApi.AuthorizationStateReady -> AuthState.LoggedIn
                is TdApi.AuthorizationStateClosed,
                is TdApi.AuthorizationStateLoggingOut -> AuthState.LoggedOut
                else -> AuthState.Unknown
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Unknown)

    override suspend fun isLoggedIn(): Boolean = authState.first() is AuthState.LoggedIn

    override suspend fun sendPhoneNumber(phone: String) {
        api.sendFunctionAsync(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }

    override suspend fun sendCode(code: String) {
        api.checkAuthenticationCode(code)
    }

    override suspend fun sendPassword(password: String) {
        api.sendFunctionAsync(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        api.logOut()
    }

    override fun observeNewMessages(channels: List<String>): Flow<Message> = channelFlow {
        val chatIdToUsername = mutableMapOf<Long, String>()
        val chatIdToTitle = mutableMapOf<Long, String>()

        for (username in channels) {
            try {
                val chat = api.searchPublicChat(username)
                chatIdToUsername[chat.id] = username
                chatIdToTitle[chat.id] = chat.title
            } catch (e: Exception) {
                // skip unreachable channels
            }
        }

        api.getUpdatesFlowOfType<TdApi.UpdateNewMessage>()
            .filter { it.message.chatId in chatIdToUsername }
            .collect { update ->
                val msg = update.message
                val username = chatIdToUsername[msg.chatId] ?: return@collect
                val title = chatIdToTitle[msg.chatId] ?: username
                val text = extractText(msg.content)
                if (text.isNotBlank()) {
                    send(
                        Message(
                            id = msg.id,
                            channel = username,
                            channelTitle = title,
                            text = text,
                            timestamp = msg.date.toLong()
                        )
                    )
                }
            }
    }

    override suspend fun fetchMessagesSince(
        channel: String,
        afterMessageId: Long
    ): List<Message> {
        return try {
            val chat = api.searchPublicChat(channel)
            val result = api.getChatHistory(chat.id, afterMessageId, 0, 50, false)
            result.messages?.mapNotNull { msg ->
                val text = extractText(msg.content).ifBlank { return@mapNotNull null }
                Message(
                    id = msg.id,
                    channel = channel,
                    channelTitle = chat.title,
                    text = text,
                    timestamp = msg.date.toLong()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchChannel(query: String): List<Channel> {
        return try {
            val username = query.removePrefix("@").trim()
            val chat = api.searchPublicChat(username)
            val supergroupId = (chat.type as? TdApi.ChatTypeSupergroup)?.supergroupId
            val memberCount = if (supergroupId != null) {
                try { api.getSupergroup(supergroupId).memberCount } catch (e: Exception) { 0 }
            } else 0
            listOf(Channel(username = username, title = chat.title, memberCount = memberCount))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractText(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption?.text.orEmpty()
        is TdApi.MessageVideo -> content.caption?.text.orEmpty()
        is TdApi.MessageDocument -> content.caption?.text.orEmpty()
        is TdApi.MessageAnimation -> content.caption?.text.orEmpty()
        else -> ""
    }
}
