package parser.bilibili

import com.github.purofle.remakebot.parser.bilibili.BiliBiliAPI
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class BiliBiliAPITest {
    @Test
    fun `test signParams`() = runBlocking {
        val params: MutableMap<String, Any> = mutableMapOf("bvid" to "BV148tzzVEoK", "cid" to 31548378411)
        BiliBiliAPI.signParams(
            params
        )

        println(params)
    }
}