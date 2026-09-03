package com.github.purofle.remakebot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main() {
    val botToken = System.getenv("BOT_TOKEN")
    if (botToken.isNullOrEmpty()) {
        logger.error { RuntimeException("Bot token is required") }
        exitProcess(1)
    }

    runCatching {
        TelegramBotsLongPollingApplication().also {
            it.registerBot(botToken, RemakeBot(botToken))
        }
    }.onFailure {
        it.printStackTrace()
    }
}
