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
}
