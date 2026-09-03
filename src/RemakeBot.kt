package com.github.purofle.remakebot

import com.github.purofle.remakebot.utils.fullName
import com.github.purofle.remakebot.utils.getCommandReceiver
import com.github.purofle.remakebot.utils.reply
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.milliseconds

class RemakeBot(botToken: String): DefaultLongPollingUpdateConsumer() {

    private val telegramClient by lazy { OkHttpTelegramClient(botToken) }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, e -> logger.error(e) { "handler crashed" } }
    )

    private val logger = KotlinLogging.logger("RemakeBot")

    private val botUser: User = telegramClient.execute(GetMe())

    override fun consume(update: Update) {
        if (!update.hasMessage()) return

        if (update.message.hasText() && update.message.from != null) {
            logger.debug { "${update.message.from.fullName}(${update.message.from.id}) -(${update.message.chat.title})>${update.message.text}" }
        }

        scope.launch {
            with(telegramClient) {
                dispatch(update)
            }
        }
    }

    context(_: OkHttpTelegramClient)
    private suspend fun dispatch(update: Update) {
        val message = update.message

        if (!message.hasText() || message.from == null) {
            return
        }

        val command = message.getCommandReceiver()

        if (command.first == null || !(command.second != null && command.second.equals(botUser.userName, ignoreCase = true))) {
            return
        }

        when (command.first) {
            "/start" -> {
                message.reply("你是？")
            }
            "/ping" -> {
                message.reply("pong!")
            }
            "/status" -> {
                val runtime = Runtime.getRuntime()
                val botUptime = ManagementFactory.getRuntimeMXBean().uptime.milliseconds
                message.reply("""
                    已用内存：${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB
                    Bot 已稳定运行：${botUptime}
                    目前平台：${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}
                """.trimIndent())
            }
            else -> {}
        }
    }
}