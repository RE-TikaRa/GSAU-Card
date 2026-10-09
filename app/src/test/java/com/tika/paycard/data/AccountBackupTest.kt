package com.tika.paycard.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountBackupTest {

    @Test
    fun `多卡配置往返保留链接备注顺序和当前卡片`() {
        val accounts = listOf(
            Account("first", "9", name = "张三", cardNo = "123", alias = "饭卡"),
            Account("first", "10", name = "李四", alias = "另一张卡")
        )

        val config = AccountBackup.decode(AccountBackup.encode(accounts, 1))

        assertEquals(accounts, config.accounts)
        assertEquals(1, config.currentIndex)
    }

    @Test
    fun `配置不保存付款码与余额`() {
        val account = Account("first", "9", cachedCode = "secret-code", cachedAt = 123L, balance = "20元")
        val text = AccountBackup.encode(listOf(account), 0)
        val record = JSONObject(text).getJSONArray("accounts").getJSONObject(0)

        assertFalse(record.has("cachedCode"))
        assertFalse(record.has("cachedAt"))
        assertFalse(record.has("balance"))
        assertEquals(account.copy(cachedCode = "", cachedAt = 0L, balance = ""), AccountBackup.decode(text).accounts.single())
    }

    @Test
    fun `UTF8 BOM 和空配置均可读取`() {
        val text = AccountBackup.encode(emptyList(), 0)

        assertEquals(AccountBackup.Config(emptyList(), -1), AccountBackup.decode("\uFEFF$text"))
    }

    @Test
    fun `其他文件格式和未知版本拒绝导入`() {
        val root = JSONObject(AccountBackup.encode(listOf(Account("first", "9")), 0))
        root.put("version", 2)
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
        root.put("version", 1).put("format", "other")
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
    }

    @Test
    fun `非法绑定链接拒绝导入`() {
        val root = JSONObject(AccountBackup.encode(listOf(Account("first", "9")), 0))
        val record = root.getJSONArray("accounts").getJSONObject(0)
        listOf(
            "https://example.com/virtualcard/openVirtualcard?openid=first&id=9",
            "https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard?openid=first&id=9x",
            "https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard?openid=first%26other&id=9"
        ).forEach { link ->
            record.put("link", link)
            assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
        }
    }

    @Test
    fun `重复卡片和错误当前索引拒绝导入`() {
        val root = JSONObject(AccountBackup.encode(listOf(Account("first", "9")), 0))
        root.put("currentIndex", 8)
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
        root.put("currentIndex", 0)
        val record = root.getJSONArray("accounts").getJSONObject(0)
        root.put("accounts", JSONArray().put(record).put(record))
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
    }
}
