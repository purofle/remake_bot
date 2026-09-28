package com.github.purofle.remakebot.data.xhs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * The `window.__INITIAL_STATE__` blob of a note page, cut down to the paths we actually read.
 *
 * The desktop page keeps the note under [note], the mobile one under [noteData]. Both share
 * [XhsNote], whose fields cover the few names the two layouts spell differently.
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
    @SerialName("noteData") val noteData: XhsMobileNoteState? = null,
)

@Serializable
data class XhsMobileNoteState(
    val data: XhsMobileNoteData? = null,
)

@Serializable
data class XhsMobileNoteData(
    @SerialName("noteData") val noteData: XhsNote? = null,
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
    /** Only the mobile page puts the id inside the note; the desktop one keys a map by it. */
    @SerialName("noteId") val noteId: String = "",
    val type: String = "",
    val title: String = "",
    val desc: String = "",
    val user: XhsUser? = null,
    @SerialName("imageList") val imageList: List<XhsImage> = emptyList(),
    val video: XhsVideo? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class XhsUser(
    @SerialName("userId") val userId: String = "",
    @JsonNames("nickName") val nickname: String = "",
)

@Serializable
data class XhsImage(
    @SerialName("urlDefault") val urlDefault: String = "",
    @SerialName("urlPre") val urlPre: String = "",
    /** What the mobile page has instead of [urlDefault]. */
    val url: String = "",
)

@Serializable
data class XhsVideo(
    val media: XhsMedia? = null,
    val consumer: XhsVideoConsumer? = null,
    val capa: XhsVideoCapa? = null,
)

/** Only served to mobile browsers. */
@Serializable
data class XhsVideoConsumer(
    /** The key of the originally uploaded file, see `ORIGIN_VIDEO_HOST` in `XhsParser`. */
    @SerialName("originVideoKey") val originVideoKey: String = "",
)

@Serializable
data class XhsVideoCapa(
    /** In seconds. */
    val duration: Int = 0,
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
