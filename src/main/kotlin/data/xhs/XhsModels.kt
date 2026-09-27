package com.github.purofle.remakebot.data.xhs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `window.__INITIAL_STATE__` blob of a note page, cut down to the paths we actually read.
 *
 * Two things to keep in mind when changing these:
 * - The source is JavaScript, and the HTML reader turns its `undefined` into `null`. Decoding
 *   therefore has to run with `coerceInputValues = true` (see `XhsAPI`), otherwise a null value
 *   fails the whole parse instead of landing on the default.
 * - Every field has a default and the container types are nullable, because a page can be a login
 *   wall or be laid out differently; `type` is a plain String rather than an enum so an unknown
 *   value cannot throw.
 */
@Serializable
data class XhsInitialState(
    val note: XhsNoteState? = null,
)

@Serializable
data class XhsNoteState(
    @SerialName("firstNoteId") val firstNoteId: String = "",
    @SerialName("noteDetailMap") val noteDetailMap: Map<String, XhsNoteDetail> = emptyMap(),
)

@Serializable
data class XhsNoteDetail(
    val note: XhsNote? = null,
)

@Serializable
data class XhsNote(
    val type: String = "",
    val title: String = "",
    val desc: String = "",
    val user: XhsUser? = null,
    @SerialName("imageList") val imageList: List<XhsImage> = emptyList(),
    val video: XhsVideo? = null,
)

@Serializable
data class XhsUser(
    @SerialName("userId") val userId: String = "",
    val nickname: String = "",
)

@Serializable
data class XhsImage(
    @SerialName("urlDefault") val urlDefault: String = "",
    @SerialName("urlPre") val urlPre: String = "",
)

@Serializable
data class XhsVideo(
    val media: XhsMedia? = null,
)

@Serializable
data class XhsMedia(
    val stream: XhsStream? = null,
)

@Serializable
data class XhsStream(
    val h264: List<XhsStreamEntry> = emptyList(),
    val av1: List<XhsStreamEntry> = emptyList(),
    val h265: List<XhsStreamEntry> = emptyList(),
    val h266: List<XhsStreamEntry> = emptyList(),
)

@Serializable
data class XhsStreamEntry(
    @SerialName("masterUrl") val masterUrl: String = "",
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
)
