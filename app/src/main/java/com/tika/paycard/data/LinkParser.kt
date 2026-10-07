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
            if (openid != null && isValidOpenid(openid)) {
                val rawId = uri.getQueryParameter("id")
                if (rawId == null) return Parsed(openid, Account.DEFAULT_CARD_ID)
                parseCardId(rawId)?.let { return Parsed(openid, it) }
                return null
            }
        }
        // 退回正则:有些粘贴内容可能带多余文字
        val openid = OPENID_RE.find(trimmed)?.groupValues?.get(1)
        if (openid != null && isValidOpenid(openid)) {
            val rawId = RAW_ID_RE.find(trimmed)?.groupValues?.get(1)
            if (rawId == null) return Parsed(openid, Account.DEFAULT_CARD_ID)
            if (rawId.matches(ID_PATTERN)) return Parsed(openid, rawId)
            return null
        }
        return null
    }

    private fun parseCardId(value: String?): String? = when {
        value == null -> Account.DEFAULT_CARD_ID
        value.matches(ID_PATTERN) -> value
        else -> null
    }

    private fun isValidOpenid(value: String?): Boolean = value != null && value.matches(OPENID_PATTERN)

    private val OPENID_PATTERN = Regex("[A-Za-z0-9_-]+")
    private val OPENID_RE = Regex("openid=([A-Za-z0-9_-]+)(?![A-Za-z0-9_-])")
    private val ID_PATTERN = Regex("\\d+")
    private val RAW_ID_RE = Regex("(?:^|[?&])id=([^&\\s]+)")
}
