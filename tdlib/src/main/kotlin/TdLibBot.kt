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

/**
 * Upper bound for [TdLibBot.completedSends], so a result that is never claimed cannot leak.
 */
private const val MAX_COMPLETED_SENDS = 256

/**
 * Telegram *remote* file ids of an uploaded video, reusable via [TdLibBot.sendVideoWithRemoteIds].
 * The remote id (unlike the local `File.id`) stays usable across restarts.
 */
data class VideoRemoteIds(
    val videoRemoteId: String,
    val coverRemoteId: String,
)

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

    /**
     * Send results whose completion update arrived before [sendVideo] could register its waiter.
     * See [sendVideo] for why this is needed and how it is drained.
     */
    private val completedSends = ConcurrentHashMap<Long, Result<Message>>()

    override fun close() {
        loopJob?.cancel()

        val cause = CancellationException("TdLibBot is closed")
        pendingSends.values.forEach { it.completeExceptionally(cause) }
        pendingSends.clear()
    }

    /**
     * Uploads [videoFile] (owned by the caller, not deleted here) together with [coverBytes].
     */
    suspend fun uploadVideoWithMessage(
        videoFile: File,
        coverBytes: ByteArray,
        from: Long,
        fromMessage: Int,
        caption: FormattedText,
        videoDuration: Int
    ): Message = withContext(Dispatchers.IO) {
        val tmpCoverFile = File.createTempFile("tdlib", ".jpg")
        try {
            tmpCoverFile.writeBytes(coverBytes)

            val inputVideo = InputVideo().apply {
                video = InputFileLocal(videoFile.absolutePath)
                cover = InputFileLocal(tmpCoverFile.absolutePath)
                duration = videoDuration
                supportsStreaming = true
            }
            sendVideo(inputVideo, from, fromMessage, caption)
        } finally {
            tmpCoverFile.delete()
        }
    }

    /**
     * Resend a video that was already uploaded, referencing it by the remote file ids returned from
     * a previous [uploadVideoWithMessage] (see [extractVideoRemoteIds]). This skips both the download
     * and the upload.
     */
    suspend fun sendVideoWithRemoteIds(
        videoRemoteId: String,
        coverRemoteId: String,
        from: Long,
        fromMessage: Int,
        caption: FormattedText,
        videoDuration: Int
    ): Message = withContext(Dispatchers.IO) {
        val inputVideo = InputVideo().apply {
            video = InputFileRemote(videoRemoteId)
            cover = InputFileRemote(coverRemoteId)
            duration = videoDuration
            supportsStreaming = true
        }

        sendVideo(inputVideo, from, fromMessage, caption)
    }

    /**
     * Extracts the reusable remote file ids from a successfully sent video message. Remote ids can
     * come back empty, in which case the message simply is not cached.
     */
    fun extractVideoRemoteIds(message: Message): VideoRemoteIds? {
        val content = message.content as? MessageVideo ?: return null

        val videoRemoteId = content.video.video.remote?.id?.takeIf { it.isNotBlank() } ?: return null
        val cover = content.cover?.sizes?.maxByOrNull { it.photo.size }?.photo ?: return null
        val coverRemoteId = cover.remote?.id?.takeIf { it.isNotBlank() } ?: return null

        return VideoRemoteIds(videoRemoteId, coverRemoteId)
    }

    private suspend fun sendVideo(inputVideo: InputVideo, from: Long, fromMessage: Int, caption: FormattedText): Message {
        val inputMessageVideo = InputMessageVideo().apply {
            video = inputVideo
            this.caption = caption
        }
        val sendMessage = SendMessage().apply {
            chatId = from
            replyTo = InputMessageReplyToMessage().apply {
                // fromMessage is a Bot API message id; TDLib ids of server messages are shifted by 20 bits.
                messageId = fromMessage.toLong() shl 20
            }
            inputMessageContent = inputMessageVideo
        }

        val msg = client.sendAwait(sendMessage)

        // Register before draining completedSends: the completion update either finds this waiter,
        // or arrived before it and is sitting in completedSends. Either way the await below is
        // guaranteed to finish, with no window in between where the result could be dropped.
        val deferred = CompletableDeferred<Message>()
        pendingSends[msg.id] = deferred
        completedSends.remove(msg.id)?.let { deferred.completeWith(it) }

        return deferred.await()
    }

    private fun completeSend(messageId: Long, result: Result<Message>) {
        val deferred = pendingSends.remove(messageId)
        if (deferred != null) {
            deferred.completeWith(result)
            return
        }

        completedSends[messageId] = result
        if (completedSends.size > MAX_COMPLETED_SENDS) completedSends.clear()
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
                is UpdateMessageSendSucceeded ->
                    completeSend(it.oldMessageId, Result.success(it.message))

                is UpdateMessageSendFailed ->
                    completeSend(it.oldMessageId, Result.failure(RuntimeException("TDLib send failed ${it.error}")))
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