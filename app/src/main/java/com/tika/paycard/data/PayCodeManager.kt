package com.tika.paycard.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 刷新协调器:抓当前账号(或指定账号)的付款码,成功后回填缓存并通知组件更新。
 */
object PayCodeManager {

    private data class CardKey(val openid: String, val cardId: String)

    private data class Outcome(
        val completedAt: Long,
        val result: PayCodeRepository.Result,
        val account: Account?
    )

    private class Entry(
        val mutex: Mutex = Mutex(),
        var waiters: Int = 0,
        @Volatile var outcome: Outcome? = null
    )

    private val repo = PayCodeRepository()
    private val entries = ConcurrentHashMap<CardKey, Entry>()

    suspend fun refresh(context: Context, account: Account): PayCodeRepository.Result {
        if (!UserConsent.hasAccepted(context)) {
            return PayCodeRepository.Result.Error("请先阅读并同意隐私政策和用户协议")
        }
        val key = CardKey(account.openid, account.cardId)
        val invokedAt = System.nanoTime()
        val entry = entries.compute(key) { _, current ->
            (current ?: Entry()).apply { waiters++ }
        }!!
        try {
            return entry.mutex.withLock {
                entry.outcome?.takeIf { it.completedAt >= invokedAt }?.let { outcome ->
                    outcome.account?.let { account.copyFrom(it) }
                    return@withLock outcome.result
                }

                val store = AccountStore.get(context)
                val requestedAt = System.currentTimeMillis()
                val result = repo.fetch(account.openid, account.cardId)
                if (result is PayCodeRepository.Result.Ok) {
                    store.list().firstOrNull { it.sameCard(account) }?.let { account.copyFrom(it) }
                    account.apply {
                        cachedCode = result.code
                        cachedAt = requestedAt
                        if (result.name.isNotBlank()) name = result.name
                        if (result.cardNo.isNotBlank()) cardNo = result.cardNo
                        if (result.balance.isNotBlank()) balance = result.balance
                    }
                    store.update(account)
                }
                entry.outcome = Outcome(
                    completedAt = System.nanoTime(),
                    result = result,
                    account = if (result is PayCodeRepository.Result.Ok) account.copy() else null
                )
                return@withLock result
            }
        } finally {
            entries.computeIfPresent(key) { _, current ->
                current.takeIf { --it.waiters > 0 }
            }
        }
    }

    private fun Account.copyFrom(source: Account) {
        name = source.name
        cardNo = source.cardNo
        balance = source.balance
        cachedCode = source.cachedCode
        cachedAt = source.cachedAt
        alias = source.alias
    }

    suspend fun refreshCurrent(context: Context): PayCodeRepository.Result {
        val account = AccountStore.get(context).current()
            ?: return PayCodeRepository.Result.Error("无账号")
        return refresh(context, account)
    }
}
