package com.tika.paycard.data

import java.io.ByteArrayInputStream
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
    fun `流式读取保留 UTF8 备注和 BOM`() {
        val account = Account("first", "9", alias = "饭卡")
        val text = "\uFEFF" + AccountBackup.encode(listOf(account), 0)

        assertEquals(listOf(account), AccountBackup.read(ByteArrayInputStream(text.toByteArray())).accounts)
    }

    @Test
    fun `超大文件最多读取限制加一字节后拒绝`() {
        val input = ByteArrayInputStream(ByteArray(AccountBackup.MAX_BYTES + 8192) { 32 })

        assertThrows(IllegalArgumentException::class.java) { AccountBackup.read(input) }
        assertEquals(8191, input.available())
    }

    @Test
    fun `文件大小按 UTF8 字节限制且边界文件可导入`() {
        val text = AccountBackup.encode(listOf(Account("first", "9")), 0)
        val padded = text.padEnd(AccountBackup.MAX_BYTES, ' ')
        assertEquals(1, AccountBackup.read(ByteArrayInputStream(padded.toByteArray())).accounts.size)
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode("中".repeat(AccountBackup.MAX_BYTES / 2)) }
    }

    @Test
    fun `超过记录上限的配置拒绝导入`() {
        val accounts = (0..AccountBackup.MAX_ACCOUNTS).map { Account("card$it", "9") }
        val records = JSONArray()
        accounts.forEach { records.put(JSONObject().put("link", "https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard?openid=${it.openid}&id=9")) }
        val root = JSONObject(AccountBackup.encode(emptyList(), -1)).put("accounts", records).put("currentIndex", 0)

        assertThrows(IllegalArgumentException::class.java) { AccountBackup.decode(root.toString()) }
        assertThrows(IllegalArgumentException::class.java) { AccountBackup.encode(accounts, 0) }
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
