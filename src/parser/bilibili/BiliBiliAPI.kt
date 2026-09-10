package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.BiliBiliResponse
import com.github.purofle.remakebot.data.bilibili.NavData
import com.github.purofle.remakebot.data.bilibili.WbiImg
import com.github.purofle.remakebot.data.bilibili.WbiParams
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.utils.toMD5
import com.github.purofle.remakebot.utils.toQueryString
import kotlin.time.Clock

object BiliBiliAPI {
    suspend fun getRemoteWbiImg(): WbiImg {
        val navData = HttpRequest.get<BiliBiliResponse<NavData>>(NAV_URL)
        BiliBiliCache.updateCache(navData.data.wbiImg)

        return navData.data.wbiImg
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

    const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
}