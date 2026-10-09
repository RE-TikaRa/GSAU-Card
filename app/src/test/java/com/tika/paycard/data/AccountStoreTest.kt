package com.tika.paycard.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun `导入多卡配置保留本机备注缓存和当前选择`() {
        val existing = Account("first", "9", alias = "本机备注", balance = "20", cachedCode = "new", cachedAt = 2L)
        val selected = Account("second", "9")
        store.add(existing)
        store.add(selected)
        store.setCurrentIndex(1)
        val added = Account("third", "9", alias = "新卡片")
        val config = AccountBackup.Config(listOf(existing.copy(alias = "导出备注"), added), 0)

        assertEquals(1, store.importConfig(config))
        assertEquals(listOf(existing, selected, added), store.list())
        assertEquals(selected, store.current())
    }

    @Test
    fun `空安装导入后恢复原来的当前卡片并清除旧付款码`() {
        val accounts = listOf(Account("first", "9"), Account("second", "10", cachedCode = "old", cachedAt = 1L))

        assertEquals(2, store.importConfig(AccountBackup.Config(accounts, 1)))
        assertEquals(accounts[1].copy(cachedCode = "", cachedAt = 0L), store.current())
        val exported = AccountBackup.decode(store.exportConfig())
        assertEquals(store.list(), exported.accounts)
        assertEquals(1, exported.currentIndex)
    }

    @Test
    fun `重复导入不增加卡片或覆盖刷新结果`() {
        val config = AccountBackup.Config(listOf(Account("first", "9", alias = "饭卡")), 0)
        store.importConfig(config)
        val refreshed = config.accounts.single().copy(cachedCode = "new", cachedAt = 2L, balance = "18")
        store.update(refreshed)

        assertEquals(0, store.importConfig(config))
        assertEquals(listOf(refreshed), store.list())
    }

    @Test
    fun `配置包含损坏记录时不部分导入`() {
        val existing = Account("first", "9", alias = "原卡片")
        store.add(existing)
        val root = JSONObject(AccountBackup.encode(listOf(Account("second", "9"), Account("third", "9")), 0))
        root.getJSONArray("accounts").getJSONObject(1).put("link", "invalid")

        assertThrows(IllegalArgumentException::class.java) {
            store.importConfig(AccountBackup.decode(root.toString()))
        }
        assertEquals(listOf(existing), store.list())
        assertEquals(existing, store.current())
    }
}
