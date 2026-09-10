package com.github.purofle.remakebot.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


private val hexDigits = "0123456789abcdef".toCharArray()

fun ByteArray.toHexString() = buildString(this.size shl 1) {
    this@toHexString.forEach { byte ->
        append(hexDigits[byte.toInt() ushr 4 and 15])
        append(hexDigits[byte.toInt() and 15])
    }
}

fun String.toMD5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.toByteArray())
    return digest.toHexString()
}

fun Any.encodeURIComponent() = URLEncoder.encode(this.toString(), StandardCharsets.UTF_8).replace("+", "%20")

fun Map<String, Any?>.toQueryString() = this.filterValues { it != null }.entries.joinToString("&") { (k, v) ->
    "${k.encodeURIComponent()}=${v!!.encodeURIComponent()}"
}