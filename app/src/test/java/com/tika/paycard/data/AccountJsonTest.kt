package com.tika.paycard.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Account 的 JSON 序列化契约。toJson/fromJson 是多账号持久化的落库入口,
 * 坏数据不能让 AccountStore.list() 整体崩掉,这里锁住往返一致与坏项丢弃。
 */
class AccountJsonTest {

    @Test
    fun `toJson fromJson 往返保持字段一致`() {
        val a = Account(
            openid = "abcd1234",
            cardId = "12",
            name = "郎振杰",
            cardNo = "1073325020407",
            balance = "12.50",
            cachedCode = "deadbeef",
            cachedAt = 1_700_000_000_000L,
            alias = "饭卡"
        )
        val back = Account.fromJson(a.toJson())
        assertEquals(a, back)
    }

    @Test
    fun `fromJson 缺 openid 丢弃`() {
        val o = JSONObject().put("cardId", "9").put("name", "无凭证")
        assertNull(Account.fromJson(o))
    }

    @Test
    fun `fromJson 空 openid 丢弃`() {
        val o = JSONObject().put("openid", "").put("cardId", "9")
        assertNull(Account.fromJson(o))
    }

    @Test
    fun `fromJson 缺 cardId 回落默认卡`() {
        val o = JSONObject().put("openid", "abcd1234")
        val a = Account.fromJson(o)
        assertEquals(Account.DEFAULT_CARD_ID, a?.cardId)
    }

    @Test
    fun `fromJson 缺可选字段回落空值`() {
        val o = JSONObject().put("openid", "abcd1234").put("cardId", "9")
        val a = Account.fromJson(o)!!
        assertEquals("", a.name)
        assertEquals("", a.cardNo)
        assertEquals("", a.balance)
        assertEquals("", a.cachedCode)
        assertEquals(0L, a.cachedAt)
        assertEquals("", a.alias)
    }
}
