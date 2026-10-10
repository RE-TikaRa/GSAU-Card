package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查的冒烟测试。锁住 Releases JSON 的解析契约与版本比对规则,
 * GitHub 接口字段或返回结构一变,这里最先失效。
 */
class UpdateCheckerTest {

    private fun json(tag: String = "v1.2", apk: String = APK_URL) =
        """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/RE-TikaRa/GSAU-Card/releases/tag/$tag",
          "assets": [
            { "browser_download_url": "$apk" }
          ]
        }
        """.trimIndent()

    @Test
    fun `有更高版本时返回镜像化的下载链接`() {
        val r = UpdateChecker.parse(json(), currentVersion = "1.0")
        assertTrue(r is UpdateChecker.Result.NewVersion)
        r as UpdateChecker.Result.NewVersion
        assertEquals("1.2", r.version)
        assertEquals("https://gh.re-tikara.fun/RE-TikaRa/GSAU-Card/releases/download/v1.2/GSAU-Card-v1.2.apk", r.apkUrl)
        assertTrue(r.pageUrl.endsWith("/RE-TikaRa/GSAU-Card/releases/tag/v1.2"))
        assertTrue(r.apkUrl.startsWith("https://"))
    }

    @Test
    fun `版本相同判为已是最新`() {
        assertTrue(UpdateChecker.parse(json(tag = "v1.0"), "1.0") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `当前版本更高判为已是最新`() {
        assertTrue(UpdateChecker.parse(json(tag = "v1.1"), "1.2") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `多段版本号逐段比较`() {
        assertTrue(UpdateChecker.parse(json(tag = "v1.2.1"), "1.2") is UpdateChecker.Result.NewVersion)
        assertTrue(UpdateChecker.parse(json(tag = "v1.2"), "1.2.1") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `Debug 补丁版本递增时提示更新`() {
        val r = UpdateChecker.parse(json(tag = "v2.3.2-debug"), "2.3.1-debug")
        assertTrue(r is UpdateChecker.Result.NewVersion)
        assertEquals("2.3.2-debug", (r as UpdateChecker.Result.NewVersion).version)
    }

    @Test
    fun `相同或较旧 Debug 版本不提示更新`() {
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug"), "2.3.1-debug") is UpdateChecker.Result.UpToDate)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3-debug"), "2.3.1-debug") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `同版本正式版高于 Debug 版`() {
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1"), "2.3.1-debug") is UpdateChecker.Result.NewVersion)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug"), "2.3.1") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `Debug 与正式版优先比较数字版本`() {
        assertTrue(UpdateChecker.parse(json(tag = "v2.4-debug"), "2.3.1") is UpdateChecker.Result.NewVersion)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3"), "2.3.1-debug") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `预发布数字后缀按数值排序`() {
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug.10"), "2.3.1-debug.2") is UpdateChecker.Result.NewVersion)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug.2"), "2.3.1-debug.10") is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `预发布标识逐段比较数字和文本`() {
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug.beta"), "2.3.1-debug.1") is UpdateChecker.Result.NewVersion)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug.1"), "2.3.1-debug.beta") is UpdateChecker.Result.UpToDate)
        assertTrue(UpdateChecker.parse(json(tag = "v2.3.1-debug.1.1"), "2.3.1-debug.1") is UpdateChecker.Result.NewVersion)
    }

    @Test
    fun `缺 tag 字段判为解析失败`() {
        val noTag = """{ "assets": [] }"""
        assertTrue(UpdateChecker.parse(noTag, "1.0") is UpdateChecker.Result.Error)
    }

    @Test
    fun `有新版但无 apk 资源判为错误`() {
        val noApk = """
            { "tag_name": "v2.0", "html_url": "https://x", "assets": [] }
        """.trimIndent()
        assertTrue(UpdateChecker.parse(noApk, "1.0") is UpdateChecker.Result.Error)
    }

    companion object {
        private const val APK_URL =
            "https://github.com/RE-TikaRa/GSAU-Card/releases/download/v1.2/GSAU-Card-v1.2.apk"
    }
}
