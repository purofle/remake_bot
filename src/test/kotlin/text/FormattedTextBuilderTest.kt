package text

import com.github.purofle.remakebot.text.formattedText
import org.drinkless.tdlib.TdApi.TextEntityTypeBlockQuote
import org.drinkless.tdlib.TdApi.TextEntityTypeExpandableBlockQuote
import org.drinkless.tdlib.TdApi.TextEntityTypeTextUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FormattedTextBuilderTest {
    @Test
    fun `shrinkable region is cut to fit and later entities are shifted`() {
        val text = formattedText(maxLength = 20) {
            line { url("title", "https://example.com") }
            line { shrinkable { expandableBlockQuote("0123456789abcdef") } }
            line { blockQuote("end") }
        }

        // 27 characters before the cut, so 7 come off the end of the description.
        assertEquals("title\n012345678\nend\n", text.text)
        val (title, desc, end) = text.entities.toList()
        assertIs<TextEntityTypeTextUrl>(title.type)
        assertEquals(0 to 5, title.offset to title.length)
        assertIs<TextEntityTypeExpandableBlockQuote>(desc.type)
        assertEquals(6 to 9, desc.offset to desc.length)
        assertIs<TextEntityTypeBlockQuote>(end.type)
        assertEquals(16 to 3, end.offset to end.length)
    }

    @Test
    fun `text within the limit is left alone`() {
        val text = formattedText(maxLength = 100) {
            line { shrinkable { expandableBlockQuote("short") } }
        }

        assertEquals("short\n", text.text)
        assertEquals(5, text.entities.single().length)
    }
}
