package com.tika.paycard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * 读 GitHub Releases 最新版,与当前 versionName 比对。
 * 返回下载链接给外部拉起浏览器,不在应用内下载安装。
 */
object UpdateChecker {

    sealed class Result {
        /** 有新版:版本名、下载链接、Release 页 */
        data class NewVersion(val version: String, val apkUrl: String, val pageUrl: String) : Result()
        object UpToDate : Result()
        data class Error(val message: String) : Result()
    }

    private val client = Http.client

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.await(req).use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("HTTP ${resp.code}")
                parse(resp.body?.string().orEmpty(), currentVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    internal fun parse(json: String, currentVersion: String): Result {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Result.Error("解析失败")
        val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return Result.Error("解析失败")
        val latest = tag.removePrefix("v")
        if (compareVersion(latest, currentVersion) <= 0) return Result.UpToDate
        val assets = root.optJSONArray("assets") ?: return Result.Error("未找到安装包")
        val apkUrl = (0 until assets.length())
            .asSequence()
            .mapNotNull { assets.optJSONObject(it)?.optString("browser_download_url") }
            .firstOrNull { it.endsWith(".apk") }
            ?.let(::proxied)
            ?: return Result.Error("未找到安装包")
        val pageUrl = proxied(root.optString("html_url"))
        return Result.NewVersion(latest, apkUrl, pageUrl)
    }

    /** 把 github.com 原始链接换成走 Cloudflare 的镜像域名,下载与页面都经代理。 */
    private fun proxied(url: String) = url.replaceFirst("^https://github\\.com".toRegex(), PROXY)

    /** 数字版本逐段比较,同版本正式版高于预发布版,预发布数字段按数值排序。 */
    private fun compareVersion(a: String, b: String): Int {
        val va = a.split("-", limit = 2)
        val vb = b.split("-", limit = 2)
        val pa = va[0].split(".")
        val pb = vb[0].split(".")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val na = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val nb = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (na != nb) return na - nb
        }
        if (va.size != vb.size) return if (va.size == 1) 1 else -1
        val sa = va.getOrNull(1).orEmpty().split(".")
        val sb = vb.getOrNull(1).orEmpty().split(".")
        for (i in 0 until maxOf(sa.size, sb.size)) {
            val partA = sa.getOrNull(i) ?: return -1
            val partB = sb.getOrNull(i) ?: return 1
            val na = partA.toIntOrNull()
            val nb = partB.toIntOrNull()
            val result = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> partA.compareTo(partB)
            }
            if (result != 0) return result
        }
        return 0
    }

    private const val PROXY = "https://gh.re-tikara.fun"
    private const val LATEST = "$PROXY/api/repos/RE-TikaRa/GSAU-Card/releases/latest"
}
