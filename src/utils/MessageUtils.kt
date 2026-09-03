package com.github.purofle.remakebot.utils

import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient

val User.fullName: String
    get() = if (lastName.isNullOrEmpty()) firstName else "$firstName $lastName"

context(client: TelegramClient)
suspend fun Message.reply(replyText: String) {
    val msg = this
    client.executeAwait(SendMessage.builder().apply {
        chatId(msg.chatId)
        text(replyText)
        replyToMessageId(msg.messageId)
    }.build())
}

private val COMMAND_RE = Regex("^(/\\w+)(?:@(\\w+))?")
fun Message.getCommandReceiver(): Pair<String?, String?> {
    check(this.text != null) { "Message text is null" }

    val groups = COMMAND_RE.find(this.text)?.groups

    return groups?.get(1)?.value to groups?.get(2)?.value
}
