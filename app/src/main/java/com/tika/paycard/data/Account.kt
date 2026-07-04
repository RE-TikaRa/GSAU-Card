package com.tika.paycard.data

import org.json.JSONObject

/**
 * 一张校园卡账号。openid 是身份凭证,id 指向具体卡片。
 * name/cardNo/balance 由抓取结果回填,cachedCode 是最近一次成功取到的付款码内容。
 */
data class Account(
    val openid: String,
    val cardId: String,
    var name: String = "",
    var cardNo: String = "",
    var balance: String = "",
    var cachedCode: String = "",
    var cachedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("openid", openid)
        put("cardId", cardId)
        put("name", name)
        put("cardNo", cardNo)
        put("balance", balance)
        put("cachedCode", cachedCode)
        put("cachedAt", cachedAt)
    }

    /** 展示用标题:优先姓名,没有则用卡号,再没有用 openid 尾段 */
    fun displayName(): String = when {
        name.isNotBlank() -> name
        cardNo.isNotBlank() -> cardNo
        else -> "卡" + openid.takeLast(4)
    }

    companion object {
        fun fromJson(o: JSONObject) = Account(
            openid = o.getString("openid"),
            cardId = o.optString("cardId", "9"),
            name = o.optString("name", ""),
            cardNo = o.optString("cardNo", ""),
            balance = o.optString("balance", ""),
            cachedCode = o.optString("cachedCode", ""),
            cachedAt = o.optLong("cachedAt", 0L)
        )
    }
}
