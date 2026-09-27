package parser.bilibili

import com.github.purofle.remakebot.parser.bilibili.BiliBiliParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BiliBiliParserTest {
    private fun urlOf(text: String) = BiliBiliParser(text).extractUrlOrNull()

    @Test
    fun `a message that is only a video id is parsed`() {
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", urlOf("BV1xx411c7mD"))
        assertEquals("https://www.bilibili.com/video/av170001", urlOf("  av170001\n"))
        assertEquals("https://www.bilibili.com/video/AV170001", urlOf("AV170001"))
    }

    @Test
    fun `video links are parsed wherever they are`() {
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            urlOf("看看这个 https://www.bilibili.com/video/BV1xx411c7mD/?p=2 好玩"),
        )
        assertEquals("https://www.bilibili.com/video/av170001", urlOf("https://m.bilibili.com/video/av170001"))
        assertEquals("https://b23.tv/abc123", urlOf("【标题】 https://b23.tv/abc123"))
    }

    @Test
    fun `ids inside other text are ignored`() {
        assertNull(urlOf("这个视频用的是 av1 编码"))
        assertNull(urlOf("看看 BV1xx411c7mD 这个"))
        assertNull(urlOf("https://www.xiaohongshu.com/explore/66a1b2c3?xsec_token=ABav12BV1xx411c7mDzz"))
        assertNull(urlOf("BVxxx"))
    }
}
