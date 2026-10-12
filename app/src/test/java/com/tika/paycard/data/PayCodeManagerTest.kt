package com.tika.paycard.data

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayCodeManagerTest {

    private lateinit var context: Context
    private lateinit var repo: PayCodeRepository
    private lateinit var originalClient: Call.Factory
    private val calls = Channel<PendingCall>(Channel.UNLIMITED)
    private val account = Account("first", "9")

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        UserConsent.accept(context)
        repo = ReflectionHelpers.getStaticField(PayCodeManager::class.java, "repo")
        originalClient = ReflectionHelpers.getField(repo, "client")
        ReflectionHelpers.setField(repo, "client", Call.Factory { request ->
            PendingCall(request).also { calls.trySend(it) }
        })
        AccountStore.get(context).add(account)
    }

    @After
    fun restoreClient() {
        ReflectionHelpers.setField(repo, "client", originalClient)
    }

    @Test
    fun `未同意协议时不请求付款码`() = runBlocking {
        context.getSharedPreferences("user_consent", Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(
            PayCodeRepository.Result.Error("请先阅读并同意隐私政策和用户协议"),
            PayCodeManager.refresh(context, account)
        )
        assertTrue(calls.tryReceive().isFailure)
    }

    @Test
    fun `同卡并发刷新共用结果且完成后释放条目`() = runBlocking {
        val results = (1..32).map {
            async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }
        }
        val call = withTimeout(2000) { calls.receive() }
        assertTrue(calls.tryReceive().isFailure)

        call.respond("deadbeef")

        assertTrue(results.awaitAll().all { it == PayCodeRepository.Result.Ok("deadbeef", "", "", "") })
        assertEquals("deadbeef", AccountStore.get(context).current()?.cachedCode)
        val entries = ReflectionHelpers.getStaticField<Map<*, *>>(PayCodeManager::class.java, "entries")
        assertTrue(entries.isEmpty())
        val next = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }
        withTimeout(2000) { calls.receive() }.respond("abcdef")
        assertEquals(PayCodeRepository.Result.Ok("abcdef", "", "", ""), next.await())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `取消等待者不会移除仍在使用的同卡条目`() = runBlocking {
        val owner = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }
        val call = withTimeout(2000) { calls.receive() }
        val canceled = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }
        canceled.cancelAndJoin()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }

        assertFalse(call.isCanceled())
        assertTrue(calls.tryReceive().isFailure)
        call.respond("deadbeef")

        assertEquals(owner.await(), waiter.await())
    }

    @Test
    fun `取消正在请求的刷新后等待者继续获取新码`() = runBlocking {
        val owner = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }
        val oldCall = withTimeout(2000) { calls.receive() }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { PayCodeManager.refresh(context, account.copy()) }

        owner.cancelAndJoin()
        val freshCall = withTimeout(2000) { calls.receive() }
        freshCall.respond("abcdef")

        assertTrue(oldCall.isCanceled())
        assertEquals(PayCodeRepository.Result.Ok("abcdef", "", "", ""), waiter.await())
    }

    private class PendingCall(private val request: Request) : Call {
        private lateinit var callback: Callback
        private var canceled = false

        override fun request() = request
        override fun execute(): Response = error("Asynchronous request expected")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { canceled = true }
        override fun isExecuted() = this::callback.isInitialized
        override fun isCanceled() = canceled
        override fun timeout() = Timeout()
        override fun clone() = PendingCall(request)

        fun respond(code: String) {
            callback.onResponse(this, Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("response")
                .body("<input id=\"code\" value=\"$code\" />".toResponseBody("text/html".toMediaType()))
                .build())
        }
    }
}
