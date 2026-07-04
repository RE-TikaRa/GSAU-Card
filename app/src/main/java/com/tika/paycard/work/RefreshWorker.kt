package com.tika.paycard.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodeRepository
import com.tika.paycard.widget.PayWidgetProvider
import java.util.concurrent.TimeUnit

/**
 * 后台周期刷新。WorkManager 最短周期 15 分钟,作为兜底调度。
 * 想要更密的刷新靠前台服务(RefreshService)。
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val result = PayCodeManager.refreshCurrent(applicationContext)
        PayWidgetProvider.refreshAll(applicationContext)
        return when (result) {
            is PayCodeRepository.Result.Ok -> Result.success()
            is PayCodeRepository.Result.Invalid -> Result.success()
            is PayCodeRepository.Result.Error -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "paycode_refresh"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
