package com.tika.paycard.data

import android.content.Context
import org.json.JSONArray

/**
 * 多账号存储。用 SharedPreferences 存账号列表和当前选中索引。
 * app 与桌面组件共享同一份数据,切换用户即改动 currentIndex。
 */
class AccountStore private constructor(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): MutableList<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { Account.fromJson(arr.getJSONObject(it)) }
    }

    fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun currentIndex(): Int {
        val size = list().size
        if (size == 0) return -1
        return prefs.getInt(KEY_CURRENT, 0).coerceIn(0, size - 1)
    }

    fun setCurrentIndex(i: Int) {
        prefs.edit().putInt(KEY_CURRENT, i).apply()
    }

    fun current(): Account? {
        val all = list()
        val i = currentIndex()
        return all.getOrNull(i)
    }

    /** 切到下一个账号,返回切换后的账号。组件点姓名条时用。 */
    fun switchToNext(): Account? {
        val all = list()
        if (all.isEmpty()) return null
        val next = (currentIndex() + 1) % all.size
        setCurrentIndex(next)
        return all[next]
    }

    fun add(account: Account) {
        val all = list()
        val existing = all.indexOfFirst { it.openid == account.openid && it.cardId == account.cardId }
        if (existing >= 0) all[existing] = account else all.add(account)
        save(all)
    }

    fun removeAt(i: Int) {
        val all = list()
        if (i !in all.indices) return
        all.removeAt(i)
        save(all)
        val cur = prefs.getInt(KEY_CURRENT, 0)
        if (cur >= all.size) setCurrentIndex((all.size - 1).coerceAtLeast(0))
    }

    /** 回填抓取结果并持久化 */
    fun update(account: Account) {
        val all = list()
        val i = all.indexOfFirst { it.openid == account.openid && it.cardId == account.cardId }
        if (i >= 0) {
            all[i] = account
            save(all)
        }
    }

    companion object {
        private const val PREFS = "paycard_store"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT = "current_index"

        @Volatile
        private var instance: AccountStore? = null

        fun get(context: Context): AccountStore =
            instance ?: synchronized(this) {
                instance ?: AccountStore(context.applicationContext).also { instance = it }
            }
    }
}
