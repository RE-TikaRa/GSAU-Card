package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountStoreTest {

    private lateinit var store: AccountStore

    @Before
    fun setup() {
        store = AccountStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `重新绑定期间删除原卡不会覆盖后面的卡`() {
        val original = Account(openid = "first", cardId = "9")
        val other = Account(openid = "second", cardId = "9")
        store.add(original)
        store.add(other)
        store.removeAt(0)

        assertNull(store.replace(original, Account(openid = "rebound", cardId = "9")))
        assertEquals(listOf(other), store.list())
    }

    @Test
    fun `重新绑定期间列表前移仍然替换原卡`() {
        val original = Account(openid = "second", cardId = "9")
        store.add(Account(openid = "first", cardId = "9"))
        store.add(original)
        store.setCurrentIndex(1)
        store.removeAt(0)
        val rebound = Account(openid = "rebound", cardId = "9")

        assertEquals(0, store.replace(original, rebound))
        assertEquals(rebound, store.current())
    }

    @Test
    fun `使用旧快照改名保留新付款码和余额`() {
        val original = Account(openid = "first", cardId = "9", cachedCode = "old", cachedAt = 1L)
        store.add(original)
        val refreshed = original.copy(cachedCode = "new", cachedAt = 2L, balance = "20.00")
        store.update(refreshed)

        store.rename(original, "饭卡")

        assertEquals(refreshed.copy(alias = "饭卡"), store.current())
    }

    @Test
    fun `刷新写回保留期间修改的备注`() {
        val original = Account(openid = "first", cardId = "9")
        store.add(original)
        store.rename(original, "饭卡")
        val refreshed = original.copy(cachedCode = "new", cachedAt = 2L)

        store.update(refreshed)

        assertEquals("饭卡", store.current()?.alias)
        assertEquals("new", store.current()?.cachedCode)
    }

    @Test
    fun `重新绑定保留期间修改的备注`() {
        val original = Account(openid = "first", cardId = "9", alias = "旧备注")
        store.add(original)
        store.rename(original, "饭卡")
        val rebound = Account(openid = "rebound", cardId = "9", cachedCode = "new")

        store.replace(original, rebound)

        assertEquals(rebound.copy(alias = "饭卡"), store.current())
    }
}
