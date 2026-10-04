package com.github.purofle.remakebot

import com.github.purofle.remakebot.tdlib.TdLibBot
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

fun main(): Unit = runBlocking {

    val botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("TELEGRAM_BOT_TOKEN environment variable is not set")

    val apiId = System.getenv("TELEGRAM_API_ID")?.toIntOrNull()
        ?: error("TELEGRAM_API_ID environment variable is not set or is not a valid integer")

    val apiHash = System.getenv("TELEGRAM_API_HASH")
        ?: error("TELEGRAM_API_HASH environment variable is not set")

    // Optional: most xhs notes sit behind a login wall, so without it those links just fail.
    val xhsCookie = System.getenv("XHS_COOKIE")
    val douyinCookie = System.getenv("DOUYIN_COOKIE")

    val td = TdLibBot(
        botToken = botToken,
        apiId = apiId,
        apiHash = apiHash,
    )

    runCatching {
            val tdJob = launch { td.connect() }

            TelegramBotsLongPollingApplication().also {
                it.registerBot(botToken, RemakeBot(botToken, td, xhsCookie, douyinCookie))
            }

            tdJob.join()
    }.onFailure {
        it.printStackTrace()
    }
}
