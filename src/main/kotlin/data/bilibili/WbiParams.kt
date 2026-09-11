package com.github.purofle.remakebot.data.bilibili

import com.github.purofle.remakebot.utils.toMD5
import com.github.purofle.remakebot.utils.toQueryString
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

private val mixinKeyEncTab = intArrayOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
    61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
    36, 20, 34, 44, 52
)

@Serializable
data class WbiParams(
    val wbiImg: WbiImg,
) {

    // .dropLast .png
    private val imgKey = wbiImg.imgUrl.toHttpUrl().pathSegments.last().dropLast(4)
    private val subKey = wbiImg.subUrl.toHttpUrl().pathSegments.last().dropLast(4)

    val mixinKey: String
        get() = (imgKey + subKey).let { s ->
            buildString {
                repeat(32) {
                    append(s[mixinKeyEncTab[it]])
                }
            }
        }

    fun enc(params: Map<String, Any?>): String {
        val sorted = params.filterValues { it != null }.toSortedMap()
        return buildString {
            append(sorted.toQueryString())
            val wts = System.currentTimeMillis() / 1000
            sorted["wts"] = wts
            append("&wts=")
            append(wts)
            append("&w_rid=")
            append((sorted.toQueryString() + mixinKey).toMD5())
        }
    }
}