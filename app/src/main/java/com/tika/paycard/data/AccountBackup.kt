package com.tika.paycard.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** 卡片配置保存绑定链接与显示信息,付款码和余额由服务器重新获取。 */
object AccountBackup {

    data class Config(val accounts: List<Account>, val currentIndex: Int)

    fun encode(accounts: List<Account>, currentIndex: Int): String {
        val records = JSONArray()
        accounts.forEach { account ->
            val link = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("openid", account.openid)
                .addQueryParameter("displayflag", "1")
                .addQueryParameter("id", account.cardId)
                .build()
            records.put(JSONObject().apply {
                put("link", link.toString())
                put("alias", account.alias)
                put("name", account.name)
                put("cardNo", account.cardNo)
            })
        }
        return JSONObject().apply {
            put("format", "gsau-card")
            put("version", 1)
            put("currentIndex", AccountStore.clampIndex(currentIndex, accounts.size))
            put("accounts", records)
        }.toString(2)
    }

    fun decode(text: String): Config {
        val root = JSONObject(text.removePrefix("\uFEFF"))
        require(root.getString("format") == "gsau-card" && root.getInt("version") == 1) {
            "不支持的卡片配置格式"
        }
        val records = root.getJSONArray("accounts")
        val accounts = (0 until records.length()).map { i ->
            val record = records.getJSONObject(i)
            val link = record.getString("link").toHttpUrl()
            require(link.scheme == "https" && link.host == "yktapp.gsau.edu.cn" &&
                link.encodedPath == "/virtualcard/openVirtualcard") { "配置中存在无效链接" }
            val openid = link.queryParameter("openid")
            val cardId = link.queryParameter("id") ?: Account.DEFAULT_CARD_ID
            require(openid != null && openid.matches(OPENID_PATTERN) && cardId.matches(ID_PATTERN)) {
                "配置中存在无效卡片"
            }
            Account(
                openid = openid,
                cardId = cardId,
                alias = record.optString("alias", ""),
                name = record.optString("name", ""),
                cardNo = record.optString("cardNo", "")
            )
        }
        require(accounts.distinctBy { it.openid to it.cardId }.size == accounts.size) { "配置中存在重复卡片" }
        val currentIndex = root.getInt("currentIndex")
        require(currentIndex == -1 && accounts.isEmpty() || currentIndex in accounts.indices) {
            "配置中的当前卡片无效"
        }
        return Config(accounts, currentIndex)
    }

    private const val BASE = "https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard"
    private val OPENID_PATTERN = Regex("[A-Za-z0-9_-]+")
    private val ID_PATTERN = Regex("\\d+")
}
