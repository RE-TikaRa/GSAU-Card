package com.tika.paycard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Error("HTTP ${resp.code}")
                parse(resp.body?.string().orEmpty(), currentVersion)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    internal fun parse(json: String, currentVersion: String): Result {
        val tag = TAG_RE.find(json)?.groupValues?.get(1) ?: return Result.Error("解析失败")
        val latest = tag.removePrefix("v")
        if (compareVersion(latest, currentVersion) <= 0) return Result.UpToDate
        val apkUrl = APK_RE.find(json)?.groupValues?.get(1)?.let(::proxied)
            ?: return Result.Error("未找到安装包")
        val pageUrl = PAGE_RE.find(json)?.groupValues?.get(1)?.let(::proxied).orEmpty()
        return Result.NewVersion(latest, apkUrl, pageUrl)
    }

    /** 把 github.com 原始链接换成走 Cloudflare 的镜像域名,下载与页面都经代理。 */
    private fun proxied(url: String) = url.replace("https://github.com", PROXY)

    /** 按点分段逐段比较数字,a>b 返回正数,相等返回 0。 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".")
        val pb = b.split(".")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val na = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val nb = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (na != nb) return na - nb
        }
        return 0
    }

    private const val PROXY = "https://gh-proxy.lgq3218483753.workers.dev"
    private const val LATEST = "$PROXY/api/repos/RE-TikaRa/GSAU-Card/releases/latest"
    private val TAG_RE = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
    private val APK_RE = Regex(""""browser_download_url"\s*:\s*"([^"]+\.apk)"""")
    private val PAGE_RE = Regex(""""html_url"\s*:\s*"([^"]+/releases/tag/[^"]+)"""")
}
