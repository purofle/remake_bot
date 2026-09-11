package com.github.purofle.remakebot.tdlib

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.drinkless.tdlib.TdApi.Function as TdApiFunction

class TdLibBot(
    private val botToken: String,
    apiId: Int,
    apiHash: String,
): AutoCloseable {

    private val _updates = MutableSharedFlow<Object>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<Object> = _updates.asSharedFlow()

    private val tdlibParameters = SetTdlibParameters().apply {
        this.apiHash = apiHash
        this.apiId = apiId
        applicationVersion = "1.0.0"
        databaseDirectory = "tdlib"
        useMessageDatabase = false
        useSecretChats = false
        systemLanguageCode = "en"
        deviceModel = "bot"
    }

    lateinit var client: Client

    private var loggedGate = CompletableDeferred<Unit>()

    private var loopJob: Job? = null

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val pendingSends = ConcurrentHashMap<Long, CompletableDeferred<Message>>()

    override fun close() {
        loopJob?.cancel()
    }

    suspend fun uploadVideoWithMessage(file: ByteArray, from: Long, text: String): Message = withContext(Dispatchers.IO) {
        val tmpFile = File.createTempFile("tdlib", ".mp4")
        try {
            tmpFile.writeBytes(file)

            val inputFile = InputFileLocal(tmpFile.absolutePath)
            val inputVideo = InputVideo().apply {
                video = inputFile
                supportsStreaming = true
            }
            val inputMessageVideo = InputMessageVideo().apply {
                video = inputVideo
                caption = FormattedText(text, null)
            }
            val sendMessage = SendMessage().apply {
                chatId = from
                inputMessageContent = inputMessageVideo
            }

            val deferred = CompletableDeferred<Message>()

            val msg = client.sendAwait(sendMessage)
            pendingSends[msg.id] = deferred

            deferred.await()
        } finally {
            tmpFile.delete()
        }
    }

    private inner class UpdateHandler : Client.ResultHandler {
        override fun onResult(obj: Object) {
            _updates.tryEmit(obj)
        }
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        logger.debug(state.toString())
        when (state) {
            is AuthorizationStateWaitTdlibParameters ->
                client.sendAwait<Ok>(tdlibParameters)

            is AuthorizationStateWaitPhoneNumber ->
                client.sendAwait<Ok>(CheckAuthenticationBotToken(botToken))

            is AuthorizationStateClosed ->
                error("TDLib closed")

            is AuthorizationStateReady -> {
                loggedGate.complete(Unit)
            }

            else -> {
                logger.debug(state.toString())
            }
        }
    }

    suspend fun connect() {
        if (!this::client.isInitialized) {
            Client.execute(SetLogVerbosityLevel(0))
            Client.execute(SetLogStream(LogStreamFile("tdlib.log", 1 shl 27, false)))

            client = Client.create(UpdateHandler(), null, null)
        }
        updates.collect {
            when (it) {
                is UpdateAuthorizationState -> handleAuthState(it.authorizationState)
                is UpdateNewMessage -> { }
                is UpdateMessageSendSucceeded -> {
                    pendingSends.remove(it.oldMessageId)?.complete(it.message)
                }
                is UpdateMessageSendFailed -> {
                    pendingSends.remove(it.oldMessageId)?.completeExceptionally(
                        RuntimeException("TDLib send failed ${it.error}")
                    )
                }
                else -> logger.debug(it.toString())
            }
        }
    }

    suspend fun <T : Object> Client.sendAwait(query: TdApiFunction<T>): T {
        return suspendCancellableCoroutine { cont ->
            send(query) { obj ->
                when (obj) {
                    is Error -> cont.resumeWithException(RuntimeException("TDLib error ${obj.code}: ${obj.message}"))
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        cont.resume(obj as T)
                    }
                }
            }
        }
    }
}