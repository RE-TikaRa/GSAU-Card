package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account 的纯逻辑测试。displayName 的四级回退和 sameCard 复合键判定
 * 是多账号切换与刷新回写的判定核心,这里锁住契约。不碰 JSON 序列化。
 */
class AccountTest {

    @Test
    fun `displayName 优先用备注`() {
        val a = Account(openid = "abcd1234", cardId = "9", name = "郎振杰", cardNo = "107", alias = "饭卡")
        assertEquals("饭卡", a.displayName())
    }

    @Test
    fun `displayName 无备注回落姓名`() {
        val a = Account(openid = "abcd1234", cardId = "9", name = "郎振杰", cardNo = "107")
        assertEquals("郎振杰", a.displayName())
    }

    @Test
    fun `displayName 无备注无姓名回落卡号`() {
        val a = Account(openid = "abcd1234", cardId = "9", cardNo = "1073325020407")
        assertEquals("1073325020407", a.displayName())
    }

    @Test
    fun `displayName 全空回落 openid 尾四位`() {
        val a = Account(openid = "abcdef123456", cardId = "9")
        assertEquals("卡3456", a.displayName())
    }

    @Test
    fun `sameCard openid 与 cardId 都同才算同卡`() {
        val a = Account(openid = "abcd", cardId = "9")
        assertTrue(a.sameCard(Account(openid = "abcd", cardId = "9")))
    }

    @Test
    fun `sameCard 同 openid 不同卡号不算同卡`() {
        val a = Account(openid = "abcd", cardId = "9")
        assertFalse(a.sameCard(Account(openid = "abcd", cardId = "12")))
    }

    @Test
    fun `sameCard 不同 openid 不算同卡`() {
        val a = Account(openid = "abcd", cardId = "9")
        assertFalse(a.sameCard(Account(openid = "ffff", cardId = "9")))
    }

    @Test
    fun `sameCard 传入 null 返回 false`() {
        val a = Account(openid = "abcd", cardId = "9")
        assertFalse(a.sameCard(null))
    }

    @Test
    fun `付款码在一分钟内有效`() {
        val a = Account(
            openid = "abcd",
            cardId = "9",
            cachedCode = "code",
            cachedAt = 1_000L
        )
        assertTrue(a.hasFreshCode(now = 60_999L))
    }

    @Test
    fun `付款码满一分钟失效`() {
        val a = Account(
            openid = "abcd",
            cardId = "9",
            cachedCode = "code",
            cachedAt = 1_000L
        )
        assertFalse(a.hasFreshCode(now = 61_000L))
    }

    @Test
    fun `空付款码始终无效`() {
        val a = Account(openid = "abcd", cardId = "9", cachedAt = 1_000L)
        assertFalse(a.hasFreshCode(now = 1_001L))
    }

    @Test
    fun `未记录获取时间的付款码无效`() {
        val a = Account(openid = "abcd", cardId = "9", cachedCode = "code")
        assertFalse(a.hasFreshCode(now = 1L))
    }

    @Test
    fun `获取时间晚于当前时间的付款码无效`() {
        val a = Account(
            openid = "abcd",
            cardId = "9",
            cachedCode = "code",
            cachedAt = 2_000L
        )
        assertFalse(a.hasFreshCode(now = 1_000L))
    }

    @Test
    fun `组件只展示手动获取的当前付款码`() {
        val a = Account(
            openid = "abcd",
            cardId = "9",
            cachedCode = "code",
            cachedAt = 1_000L,
            widgetCachedAt = 1_000L
        )
        assertTrue(a.hasWidgetCode(now = 1_001L))

        a.cachedAt = 2_000L
        assertFalse(a.hasWidgetCode(now = 2_001L))
    }
}
