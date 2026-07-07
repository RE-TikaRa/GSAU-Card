package com.tika.paycard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 抓取付款码页面并解析出付款码内容与账户信息。
 * 页面把二维码内容直接放在 id="code" 的隐藏字段,姓名/卡号/余额在 p.bdb 文本里。
 */
class PayCodeRepository {

    sealed class Result {
        data class Ok(val code: String, val name: String, val cardNo: String, val balance: String) : Result()
        /** 页面能打开但取不到 code,通常是凭证失效 */
        object Invalid : Result()
        data class Error(val message: String) : Result()
    }

    private val client = Http.client

    suspend fun fetch(openid: String, cardId: String): Result = withContext(Dispatchers.IO) {
        val url = "$BASE?openid=$openid&displayflag=1&id=$cardId"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("HTTP ${resp.code}")
                val html = resp.body?.string().orEmpty()
                parse(html)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    internal fun parse(html: String): Result {
        val code = CODE_RE.find(html)?.groupValues?.get(1)
        if (code.isNullOrBlank()) return Result.Invalid

        // p.bdb 文本形如: 郎振杰：1073325020407 余额：16.43元
        var name = ""
        var cardNo = ""
        var balance = ""
        BDB_RE.find(html)?.groupValues?.get(1)?.let { line ->
            NAME_CARD_RE.find(line)?.let {
                name = it.groupValues[1].trim()
                cardNo = it.groupValues[2].trim()
            }
            BAL_RE.find(line)?.let { balance = it.groupValues[1].trim() }
        }
        return Result.Ok(code, name, cardNo, balance)
    }

    companion object {
        private const val BASE = "https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/108.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.30"

        private val CODE_RE = Regex("""id="code"\s+value="([0-9A-Fa-f]+)"""")
        private val BDB_RE = Regex("""<p class="bdb">([^<]*)</p>""")
        private val NAME_CARD_RE = Regex("""(.+?)[：:]\s*(\d+)""")
        private val BAL_RE = Regex("""余额[：:]\s*([0-9.]+元?)""")
    }
}
