package com.tika.paycard.data

import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * 抓取付款码页面并解析出付款码内容与账户信息。
 * 页面把二维码内容直接放在 id="code" 的隐藏字段,姓名/卡号/余额在 p.bdb 文本里。
 */
class PayCodeRepository(private val client: Call.Factory = Http.client) {

    sealed class Result {
        data class Ok(val code: String, val name: String, val cardNo: String, val balance: String) : Result()
        /** 页面能打开但取不到 code,通常是凭证失效 */
        object Invalid : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun fetch(openid: String, cardId: String): Result {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("openid", openid)
            .addQueryParameter("displayflag", "1")
            .addQueryParameter("id", cardId)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        return try {
            client.await(req).use { resp ->
                if (!resp.isSuccessful) Result.Error("HTTP ${resp.code}")
                else parse(resp.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    internal fun parse(html: String): Result {
        val code = CODE_RES
            .asSequence()
            .mapNotNull { it.find(html)?.groupValues?.get(1) }
            .firstOrNull()
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

        private val CODE_RES = listOf(
            Regex(
            """\bid\s*=\s*["']code["'][^>]*\bvalue\s*=\s*["']([0-9A-Fa-f]+)["']""",
            RegexOption.IGNORE_CASE
            ),
            Regex(
                """\bvalue\s*=\s*["']([0-9A-Fa-f]+)["'][^>]*\bid\s*=\s*["']code["']""",
                RegexOption.IGNORE_CASE
            )
        )
        private val BDB_RE = Regex(
            """<p\b[^>]*\bclass\s*=\s*["'][^"']*\bbdb\b[^"']*["'][^>]*>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val NAME_CARD_RE = Regex("""(.+?)[：:]\s*(\d+)""")
        private val BAL_RE = Regex("""余额[：:]\s*([0-9.]+元?)""")
    }
}
