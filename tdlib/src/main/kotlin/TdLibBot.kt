package com.github.purofle.remakebot.tdlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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


    override fun close() {
        loopJob?.cancel()
    }

    suspend fun getMessages(chatId: Long, messageIds: LongArray): Messages =
        client.sendAwait(GetMessages(chatId, messageIds))

    suspend fun getUser(userId: Long): User = client.sendAwait(GetUser(userId))

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
                else -> logger.debug(it.toString())
            }
        }
    }

    suspend fun <T : Object> Client.sendAwait(query: TdApi.Function<T>): T {
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