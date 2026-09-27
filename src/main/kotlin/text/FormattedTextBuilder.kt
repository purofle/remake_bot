package com.github.purofle.remakebot.text

import org.drinkless.tdlib.TdApi.*

@DslMarker
annotation class FormattedTextDsl

/**
 * @param maxLength the length [build] cuts the text down to, by shortening the [shrinkable] region.
 *   Lengths are UTF-16 code units, like entity offsets.
 */
@FormattedTextDsl
class FormattedTextBuilder(private val maxLength: Int? = null) {
    private val sb = StringBuilder()
    private val entities = mutableListOf<TextEntity>()

    /** Start and end (exclusive) of the text [build] may cut, if any. */
    private var shrinkableRange: Pair<Int, Int>? = null

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

    /**
     * Marks the text appended in [block] as the part to cut from its end when the whole text is
     * longer than [maxLength], typically a description. Only one region may be shrinkable.
     */
    fun shrinkable(block: FormattedTextBuilder.() -> Unit) {
        check(shrinkableRange == null) { "Only one shrinkable region is supported" }
        val start = sb.length
        this.block()
        shrinkableRange = start to sb.length
    }

    fun build(): FormattedText {
        val range = shrinkableRange
        val overflow = maxLength?.let { sb.length - it } ?: 0
        if (range == null || overflow <= 0) return FormattedText(sb.toString(), entities.toTypedArray())

        val (regionStart, cutEnd) = range
        var cutStart = (cutEnd - overflow).coerceAtLeast(regionStart)
        // Never keep half of a surrogate pair.
        if (cutStart > regionStart && sb[cutStart - 1].isHighSurrogate()) cutStart--
        val removed = cutEnd - cutStart

        fun shift(position: Int) = when {
            position <= cutStart -> position
            position >= cutEnd -> position - removed
            else -> cutStart
        }

        val shifted = entities.mapNotNull { entity ->
            val start = shift(entity.offset)
            val end = shift(entity.offset + entity.length)
            if (end > start) TextEntity(start, end - start, entity.type) else null
        }

        return FormattedText(StringBuilder(sb).delete(cutStart, cutEnd).toString(), shifted.toTypedArray())
    }
}

fun formattedText(maxLength: Int? = null, block: FormattedTextBuilder.() -> Unit): FormattedText =
    FormattedTextBuilder(maxLength).apply(block).build()
