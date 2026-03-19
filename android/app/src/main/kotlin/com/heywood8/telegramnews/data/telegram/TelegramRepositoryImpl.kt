package com.heywood8.telegramnews.data.telegram

import android.content.Context
import android.os.Build
import com.heywood8.telegramnews.BuildConfig
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.MediaType
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.io.File
import kotlinx.telegram.core.TelegramFlow
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
    private val tdDbDir get() = File(context.filesDir, "td")
    private val tdFilesDir get() = File(context.filesDir, "td_files")

    private fun clearTdlibDatabase() {
        tdDbDir.deleteRecursively()
        tdFilesDir.deleteRecursively()
    }

    private fun sendTdlibParameters() {
        scope.launch {
            try {
                api.sendFunctionAsync(TdApi.SetTdlibParameters().also {
                    it.useTestDc = false
                    it.databaseDirectory = tdDbDir.absolutePath
                    it.filesDirectory = tdFilesDir.absolutePath
                    it.databaseEncryptionKey = byteArrayOf()
                    it.useFileDatabase = true
                    it.useChatInfoDatabase = true
                    it.useMessageDatabase = true
                    it.useSecretChats = false
                    it.apiId = BuildConfig.TELEGRAM_API_ID
                    it.apiHash = BuildConfig.TELEGRAM_API_HASH
                    it.systemLanguageCode = "en"
                    it.deviceModel = Build.MODEL
                    it.systemVersion = Build.VERSION.RELEASE
                    it.applicationVersion = "1.0"
                })
            } catch (_: Exception) {}
        }
    }

    private fun initTdlib() {
        api.attachClient()
        // Send parameters immediately after attaching — authorizationStateFlow() always emits
        // WaitTdlibParameters first, but that event can be missed if the flow collector hasn't
        // subscribed yet when TDLib emits it (~5ms after attachClient). Sending proactively
        // avoids the race condition.
        sendTdlibParameters()
        val handler = CoroutineExceptionHandler { _, _ -> initTdlib() }
        scope.launch(handler) {
            api.authorizationStateFlow().collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateClosed -> {
                        api.attachClient()
                        sendTdlibParameters()
                    }
                    else -> {}
                }
            }
        }
    }

    init { initTdlib() }

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
        api.sendFunctionAsync(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun sendPassword(password: String) {
        api.sendFunctionAsync(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        try { api.sendFunctionAsync(TdApi.LogOut()) } catch (_: Exception) {}
        clearTdlibDatabase()
    }

    override fun observeNewMessages(channels: List<String>): Flow<Message> = channelFlow {
        val chatIdToUsername = mutableMapOf<Long, String>()
        val chatIdToTitle = mutableMapOf<Long, String>()

        for (username in channels) {
            try {
                val chat = api.sendFunctionAsync(TdApi.SearchPublicChat(username))
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
                val text = extractText(msg.content, title)
                val mediaType = extractMediaType(msg.content)
                val photoFileId = extractPhotoFileId(msg.content)
                if (text.isNotBlank() || mediaType == MediaType.PHOTO) {
                    send(
                        Message(
                            id = msg.id,
                            channel = username,
                            channelTitle = title,
                            text = text,
                            timestamp = msg.date.toLong(),
                            mediaType = mediaType,
                            photoFileId = photoFileId,
                            mediaAlbumId = if (msg.mediaAlbumId != 0L) msg.mediaAlbumId else null,
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
            val chat = api.sendFunctionAsync(TdApi.SearchPublicChat(channel))
            val result = api.sendFunctionAsync(TdApi.GetChatHistory(chat.id, afterMessageId, 0, 50, false))
            result.messages?.mapNotNull { msg ->
                val text = extractText(msg.content, chat.title)
                val mediaType = extractMediaType(msg.content)
                val photoFileId = extractPhotoFileId(msg.content)
                // Drop non-photo messages with no text
                if (text.isBlank() && mediaType != MediaType.PHOTO) return@mapNotNull null
                Message(
                    id = msg.id,
                    channel = channel,
                    channelTitle = chat.title,
                    text = text,
                    timestamp = msg.date.toLong(),
                    mediaType = mediaType,
                    photoFileId = photoFileId,
                    mediaAlbumId = if (msg.mediaAlbumId != 0L) msg.mediaAlbumId else null,
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchChannel(query: String): List<Channel> {
        return try {
            val username = query.removePrefix("@").trim()
            val chat = api.sendFunctionAsync(TdApi.SearchPublicChat(username))
            val supergroupId = (chat.type as? TdApi.ChatTypeSupergroup)?.supergroupId
            val memberCount = if (supergroupId != null) {
                try { api.sendFunctionAsync(TdApi.GetSupergroup(supergroupId)).memberCount } catch (e: Exception) { 0 }
            } else 0
            listOf(Channel(username = username, title = chat.title, memberCount = memberCount))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractPhotoFileId(content: TdApi.MessageContent): Int? {
        val sizes = (content as? TdApi.MessagePhoto)?.photo?.sizes ?: return null
        val chosen = sizes.filter { it.width <= 1280 }.maxByOrNull { it.width }
            ?: sizes.maxByOrNull { it.width }
        return chosen?.photo?.id
    }

    override suspend fun downloadFile(fileId: Int): String? = try {
        val file = api.sendFunctionAsync(TdApi.DownloadFile(fileId, 1, 0L, 0L, true))
        if (file.local.isDownloadingCompleted) file.local.path else null
    } catch (_: Exception) { null }

    private fun extractMediaType(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessagePhoto -> MediaType.PHOTO
        is TdApi.MessageVideo -> "video"
        is TdApi.MessageDocument -> "document"
        is TdApi.MessageAnimation -> "animation"
        else -> null
    }

    private fun extractText(content: TdApi.MessageContent, channelTitle: String = ""): String {
        val raw = when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> content.caption?.text.orEmpty()
            is TdApi.MessageVideo -> content.caption?.text.orEmpty()
            is TdApi.MessageDocument -> content.caption?.text.orEmpty()
            is TdApi.MessageAnimation -> content.caption?.text.orEmpty()
            else -> ""
        }
        return cleanText(raw, channelTitle)
    }

    private fun cleanText(text: String, channelTitle: String = ""): String {
        val lines = text.lines().toMutableList()
        val normalizedTitle = channelTitle.stripEmojis().trim().lowercase()
        // Drop trailing lines that are blank or are the channel signature (with or without emoji)
        while (lines.isNotEmpty()) {
            val stripped = lines.last().stripEmojis().trim()
            val isBlank = stripped.isBlank()
            val isSignature = normalizedTitle.isNotEmpty() && stripped.lowercase() == normalizedTitle
            if (isBlank || isSignature) lines.removeLast() else break
        }
        return lines.joinToString("\n").stripEmojis().trim()
    }

    // Iterates code points so supplementary-plane emoji (U+1F000+) are handled correctly.
    private fun String.stripEmojis(): String {
        val sb = StringBuilder()
        var i = 0
        while (i < length) {
            val cp = codePointAt(i)
            val keep = cp <= 0xFFFF &&
                cp !in 0x2300..0x23FF &&   // misc technical
                cp !in 0x2600..0x27FF &&   // misc symbols & dingbats
                cp !in 0x2B00..0x2BFF &&   // misc symbols arrows
                cp !in 0x3000..0x303F &&   // CJK symbols
                cp !in 0xFE00..0xFE0F &&   // variation selectors
                cp != 0x200D               // ZWJ
            if (keep) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(Regex(" {2,}"), " ")
    }
}
