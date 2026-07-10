package com.tika.paycard.data

import android.content.Context
import org.json.JSONArray

/**
 * 多账号存储。用 SharedPreferences 存账号列表和当前选中索引。
 * app 与桌面组件共享同一份数据,切换用户即改动 currentIndex。
 */
class AccountStore private constructor(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(): MutableList<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return mutableListOf()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return mutableListOf()
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.let(Account::fromJson) }
            .toMutableList()
    }

    fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun currentIndex(): Int = clampIndex(prefs.getInt(KEY_CURRENT, 0), list().size)

    fun setCurrentIndex(i: Int) {
        prefs.edit().putInt(KEY_CURRENT, i).apply()
    }

    fun current(): Account? {
        val all = list()
        val i = currentIndex()
        return all.getOrNull(i)
    }

    /** 切到下一个账号,返回切换后的账号。组件点姓名条时用。 */
    fun switchToNext(): Account? = synchronized(lock) {
        val all = list()
        if (all.isEmpty()) return@synchronized null
        val next = nextIndex(currentIndex(), all.size)
        setCurrentIndex(next)
        all[next]
    }

    fun add(account: Account) = synchronized(lock) {
        val all = list()
        val existing = all.indexOfFirst { it.sameCard(account) }
        if (existing >= 0) all[existing] = account else all.add(account)
        save(all)
    }

    fun removeAt(i: Int) = synchronized(lock) {
        val all = list()
        if (i !in all.indices) return@synchronized
        all.removeAt(i)
        save(all)
        setCurrentIndex(indexAfterRemoval(prefs.getInt(KEY_CURRENT, 0), i, all.size))
    }

    /** 就地替换某张卡,保持它在列表中的位置。凭证失效后重新绑定用。 */
    fun replaceAt(i: Int, account: Account) = synchronized(lock) {
        val all = list()
        if (i !in all.indices) return@synchronized
        all[i] = account
        save(all)
    }

    /** 回填抓取结果并持久化 */
    fun update(account: Account) = synchronized(lock) {
        val all = list()
        val i = all.indexOfFirst { it.sameCard(account) }
        if (i >= 0) {
            all[i] = account
            save(all)
        }
    }

    companion object {
        private const val PREFS = "paycard_store"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT = "current_index"

        /** 把存储的选中索引夹到合法范围;空列表返回 -1。 */
        fun clampIndex(stored: Int, size: Int): Int =
            if (size == 0) -1 else stored.coerceIn(0, size - 1)

        /** 环形切到下一个;空列表返回 -1。 */
        fun nextIndex(current: Int, size: Int): Int =
            if (size == 0) -1 else (current + 1) % size

        /** 删掉 removed 位后重算选中索引:删当前之前的卡列表前移,索引跟着前移。 */
        fun indexAfterRemoval(current: Int, removed: Int, sizeAfter: Int): Int = when {
            sizeAfter == 0 -> 0
            removed < current -> current - 1
            else -> current.coerceAtMost(sizeAfter - 1)
        }

        @Volatile
        private var instance: AccountStore? = null

        fun get(context: Context): AccountStore =
            instance ?: synchronized(this) {
                instance ?: AccountStore(context.applicationContext).also { instance = it }
            }
    }
}
