package com.github.purofle.remakebot.quotely

import com.github.purofle.remakebot.utils.fullName
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** MarkdownV2 characters that have to be preceded by a backslash to be rendered literally. */
private val MARKDOWN_V2_SPECIALS = setOf(
    '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!', '\\',
)

/**
 * Renders the quote reply for a message that replies to another message, or returns null when
 * [message] is not a quote command or is not a reply.
 *
 * A quote command starts with `/` (or `\` to swap actor and target) followed by a non-ASCII
 * command word, or by `$` when the command word is ASCII. `/$time` and `$time` in the text are
 * replaced with the message timestamp. Like the original bot this is rendered as MarkdownV2.
 *
 * The target is the sender of the replied-to message, or the sender itself when the reply points
 * at a forum topic root.
 */
fun buildQuoteReply(message: Message): String? {
    val source = message.text ?: return null
    if (source.length < 2) return null

    // ASCII commands are bot commands unless they use the `$` escape; non-ASCII words cannot collide with them.
    if ((source[0] != '/' && source[0] != '\\') || (source[1].code < 0x80 && source[1] != '$')) return null

    val replyToMessage = message.replyToMessage ?: return null

    val timestamp = Instant.ofEpochSecond((message.date ?: 0).toLong())
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
    val body = source
        .replace("\$time", timestamp)
        .removePrefix("/")
        .removePrefix("\\")
        .removePrefix("$")

    // Escaping happens before splitting, exactly like the Go implementation: a space is never escaped.
    val keywords = escapeMarkdownV2(body).split(" ", limit = 2)
    val keyword = keywords[0]

    val sender = message.senderIdentity() ?: return null

    val replyTo = replyToMessage
        .takeUnless { message.isTopicMessage() && it.messageId == message.messageThreadId }
    val target = replyTo?.senderIdentity(followTextMention = true) ?: ("自己" to sender.second)

    val reversed = source.startsWith("\\")
    val (actorName, actorUri) = if (reversed) target else sender
    val (receiverName, receiverUri) = if (reversed) sender else target
    val rest = keywords.getOrNull(1)

    val separator = if (rest == null) "了 " else " "
    val restSuffix = rest?.let { " $it" }.orEmpty()
    return "[$actorName]($actorUri) $keyword$separator[$receiverName]($receiverUri)$restSuffix！"
}

private fun Message.senderIdentity(followTextMention: Boolean = false): Pair<String, String>? = senderChat?.let {
    escapeMarkdownV2(it.title.orEmpty()) to "https://t.me/${it.userName.orEmpty()}"
} ?: from?.let { sender ->
    // Bots without a username mention the user they refer to through a text_mention entity.
    val mentioned = if (followTextMention && sender.isBot == true) {
        entities?.firstOrNull()?.takeIf { it.type == "text_mention" }?.user
    } else {
        null
    }
    val user = mentioned ?: sender

    escapeMarkdownV2(trimFullNameBeforePipe(user.fullName)) to "tg://user?id=${user.id}"
}

private fun trimFullNameBeforePipe(fullName: String): String =
    fullName.substringBefore('|').trim()

private fun escapeMarkdownV2(text: String): String = buildString(text.length) {
    text.forEach { char ->
        if (char in MARKDOWN_V2_SPECIALS) append('\\')
        append(char)
    }
}
