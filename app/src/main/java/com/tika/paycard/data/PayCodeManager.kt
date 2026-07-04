package com.tika.paycard.data

import android.content.Context

/**
 * 刷新协调器:抓当前账号(或指定账号)的付款码,成功后回填缓存并通知组件更新。
 */
object PayCodeManager {

    private val repo = PayCodeRepository()

    suspend fun refresh(context: Context, account: Account): PayCodeRepository.Result {
        val result = repo.fetch(account.openid, account.cardId)
        if (result is PayCodeRepository.Result.Ok) {
            val store = AccountStore.get(context)
            account.apply {
                cachedCode = result.code
                cachedAt = System.currentTimeMillis()
                if (result.name.isNotBlank()) name = result.name
                if (result.cardNo.isNotBlank()) cardNo = result.cardNo
                if (result.balance.isNotBlank()) balance = result.balance
            }
            store.update(account)
        }
        return result
    }

    suspend fun refreshCurrent(context: Context): PayCodeRepository.Result {
        val account = AccountStore.get(context).current()
            ?: return PayCodeRepository.Result.Error("无账号")
        return refresh(context, account)
    }
}
