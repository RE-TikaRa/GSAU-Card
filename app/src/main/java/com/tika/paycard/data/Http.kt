package com.tika.paycard.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全应用共享的 OkHttpClient。连接池与线程池只此一份,各处按需 newBuilder 派生覆盖超时。
 */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}
