package com.tika.paycard.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
