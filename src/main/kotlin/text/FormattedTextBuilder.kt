package com.github.purofle.remakebot.text

import org.drinkless.tdlib.TdApi.*

@DslMarker
annotation class FormattedTextDsl

@FormattedTextDsl
class FormattedTextBuilder {
    private val sb = StringBuilder()
    private val entities = mutableListOf<TextEntity>()

    fun plain(str: String) {
        sb.append(str)
    }

    private inline fun addEntity(text: String, factory: (Int, Int) -> TextEntity) {
        val offset = sb.toString().length
        sb.append(text)
        val length = text.length
        entities += factory(offset, length)
    }

    fun expandableBlockQuote(str: String) {
        addEntity(str) { offset, length ->
            TextEntity(offset, length, TextEntityTypeExpandableBlockQuote())
        }
    }

    fun blockQuote(str: String) {
        addEntity(str) { offset, length ->
            TextEntity(offset, length, TextEntityTypeBlockQuote())
        }
    }

    fun url(str: String, url: String) {
        addEntity(str) { offset, length ->
            TextEntity(offset, length, TextEntityTypeTextUrl(url))
        }
    }

    fun line(block: FormattedTextBuilder.() -> Unit) {
        this.block()
        sb.append("\n")
    }

    fun build(): FormattedText = FormattedText(sb.toString(), entities.toTypedArray())
}

fun formattedText(block: FormattedTextBuilder.() -> Unit): FormattedText =
    FormattedTextBuilder().apply(block).build()