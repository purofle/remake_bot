package com.github.purofle.remakebot.text

import org.drinkless.tdlib.TdApi.*
import org.telegram.telegrambots.meta.api.objects.MessageEntity

/**
 * The entities of this text for a Bot API caption. Offsets carry over as they are, both count UTF-16
 * code units. Only the entity types [FormattedTextBuilder] produces are mapped, anything else is
 * dropped.
 */
fun FormattedText.toBotApiEntities(): List<MessageEntity> = entities.mapNotNull { entity ->
    val builder = MessageEntity.builder().offset(entity.offset).length(entity.length)

    when (val type = entity.type) {
        is TextEntityTypeTextUrl -> builder.type("text_link").url(type.url)
        is TextEntityTypeBlockQuote -> builder.type("blockquote")
        is TextEntityTypeExpandableBlockQuote -> builder.type("expandable_blockquote")
        else -> null
    }?.build()
}
