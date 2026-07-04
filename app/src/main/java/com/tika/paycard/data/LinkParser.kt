package com.tika.paycard.data

import android.net.Uri

/**
 * 从用户粘贴的付款码链接里解析 openid 和 id。
 * 形如 https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard?openid=XXXX&displayflag=1&id=9
 */
object LinkParser {

    data class Parsed(val openid: String, val cardId: String)

    fun parse(text: String): Parsed? {
        val trimmed = text.trim()
        // 先按标准 URL 解析
        runCatching {
            val uri = Uri.parse(trimmed)
            val openid = uri.getQueryParameter("openid")
            if (!openid.isNullOrBlank()) {
                val id = uri.getQueryParameter("id") ?: "9"
                return Parsed(openid, id)
            }
        }
        // 退回正则:有些粘贴内容可能带多余文字
        val openid = Regex("openid=([0-9A-Fa-f]+)").find(trimmed)?.groupValues?.get(1)
        if (!openid.isNullOrBlank()) {
            val id = Regex("[?&]id=(\\d+)").find(trimmed)?.groupValues?.get(1) ?: "9"
            return Parsed(openid, id)
        }
        return null
    }
}
