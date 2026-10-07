package com.tika.paycard.data

import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * 全应用共享的 OkHttpClient。连接池与线程池只此一份,各处按需 newBuilder 派生覆盖超时。
 * callTimeout 给单次调用一个总上限:组件刷新走 goAsync,广播 ANR 窗口 10 秒,总耗时须压在其内。
 */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.Factory.await(request: Request): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) {
                response.close()
                return
            }
            continuation.resume(response) { response.close() }
        }
    })
}
