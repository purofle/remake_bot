package com.github.purofle.remakebot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.io.Serializable

suspend fun <T : Serializable> TelegramClient.executeAwait(method: BotApiMethod<T>): T =
    withContext(Dispatchers.IO) { execute(method) }
