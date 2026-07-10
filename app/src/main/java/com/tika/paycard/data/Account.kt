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
    var cachedAt: Long = 0L,
    var alias: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("openid", openid)
        put("cardId", cardId)
        put("name", name)
        put("cardNo", cardNo)
        put("balance", balance)
        put("cachedCode", cachedCode)
        put("cachedAt", cachedAt)
        put("alias", alias)
    }

    /** 展示用标题:优先用户备注,再姓名,再卡号,最后 openid 尾段 */
    fun displayName(): String = when {
        alias.isNotBlank() -> alias
        name.isNotBlank() -> name
        cardNo.isNotBlank() -> cardNo
        else -> "卡" + openid.takeLast(4)
    }

    /** 同一张卡的判定:openid + cardId 复合键。异步刷新回调据此确认当前卡未被切换。 */
    fun sameCard(other: Account?): Boolean =
        other != null && openid == other.openid && cardId == other.cardId

    fun hasFreshCode(now: Long = System.currentTimeMillis()): Boolean {
        val age = now - cachedAt
        return cachedCode.isNotBlank() && cachedAt > 0L && age in 0 until PayCodePolicy.VALIDITY_MS
    }

    companion object {
        fun fromJson(o: JSONObject) = Account(
            openid = o.getString("openid"),
            cardId = o.optString("cardId", "9"),
            name = o.optString("name", ""),
            cardNo = o.optString("cardNo", ""),
            balance = o.optString("balance", ""),
            cachedCode = o.optString("cachedCode", ""),
            cachedAt = o.optLong("cachedAt", 0L),
            alias = o.optString("alias", "")
        )
    }
}
