package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.*
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.utils.toMD5
import com.github.purofle.remakebot.utils.toQueryString
import kotlin.time.Clock

object BiliBiliAPI {

    typealias VideoInfoResponse = BiliBiliResponse<VideoInfo>
    typealias VideoUrlResponse = BiliBiliResponse<VideoUrl>

    suspend fun getRemoteWbiImg(): WbiImg {
        val navData = HttpRequest.get<BiliBiliResponse<NavData>>(NAV_URL)
        BiliBiliCache.updateCache(navData.data.wbiImg)

        return navData.data.wbiImg
    }

    suspend fun getVideoInfo(videoId: VideoId): VideoInfoResponse {
        val params = when (videoId) {
            is VideoId.Aid -> mapOf("aid" to videoId.value)
            is VideoId.Bvid -> mapOf("bvid" to videoId.value)
        }

        val req = HttpRequest.get<VideoInfoResponse>(GET_VIDEO_INFO, params)

        return req
    }

    suspend fun getPlayUrl(videoId: VideoId, cid: Long): VideoUrlResponse {
        val params: MutableMap<String, Any> = mutableMapOf("cid" to cid)
        when (videoId) {
            is VideoId.Aid -> params["aid"] = videoId.value
            is VideoId.Bvid -> params["bvid"] = videoId.value
        }

        signParams(params)

        return HttpRequest.get(PLAY_URL, params)
    }

    suspend fun signParams(params: MutableMap<String, Any>) {
        if (!BiliBiliCache.isCacheValid()) {
            getRemoteWbiImg()
        }

        val wts = Clock.System.now().epochSeconds
        params["wts"] = wts

        val mixinKey = WbiParams(
            BiliBiliCache.content!!.wbiImg
        ).mixinKey

        val sorted = params.toSortedMap()
        params["w_rid"] = (sorted.toQueryString() + mixinKey).toMD5()
    }

    private const val GET_VIDEO_INFO = "https://api.bilibili.com/x/web-interface/view"

    private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
    private const val PLAY_URL = "https://api.bilibili.com/x/player/wbi/playurl"
}