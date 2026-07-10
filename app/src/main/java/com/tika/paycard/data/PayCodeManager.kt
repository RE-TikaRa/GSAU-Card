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

    private val repo = PayCodeRepository()
    private val refreshMutexes = ConcurrentHashMap<CardKey, Mutex>()
    private val outcomes = ConcurrentHashMap<CardKey, Outcome>()

    suspend fun refresh(context: Context, account: Account): PayCodeRepository.Result {
        val key = CardKey(account.openid, account.cardId)
        val invokedAt = System.nanoTime()
        return refreshMutexes.computeIfAbsent(key) { Mutex() }.withLock {
            outcomes[key]?.takeIf { it.completedAt >= invokedAt }?.let { outcome ->
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
            outcomes[key] = Outcome(
                completedAt = System.nanoTime(),
                result = result,
                account = if (result is PayCodeRepository.Result.Ok) account.copy() else null
            )
            return result
        }
    }

    private fun Account.copyFrom(source: Account) {
        name = source.name
        cardNo = source.cardNo
        balance = source.balance
        cachedCode = source.cachedCode
        cachedAt = source.cachedAt
        widgetCachedAt = source.widgetCachedAt
        alias = source.alias
    }

    suspend fun refreshCurrent(context: Context): PayCodeRepository.Result {
        val account = AccountStore.get(context).current()
            ?: return PayCodeRepository.Result.Error("无账号")
        return refresh(context, account)
    }
}
