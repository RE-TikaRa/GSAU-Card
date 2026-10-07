package com.tika.paycard.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCodeRequestTest {

    @Test
    fun `取消刷新立即取消网络调用`() = runBlocking {
        lateinit var call: PendingCall
        val repo = PayCodeRepository { request -> PendingCall(request).also { call = it } }
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }

        result.cancelAndJoin()

        assertTrue(call.isCanceled())
        assertTrue(result.isCancelled)
    }

    @Test
    fun `取消后到达的响应仍然关闭且不恢复刷新`() = runBlocking {
        lateinit var call: PendingCall
        val repo = PayCodeRepository { request -> PendingCall(request).also { call = it } }
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }
        result.cancelAndJoin()
        val body = Body("<input id=\"code\" value=\"deadbeef\" />")

        call.respond(body)

        assertTrue(body.closed)
        assertTrue(result.isCancelled)
    }

    @Test
    fun `取消旧请求后新请求可以成功完成`() = runBlocking {
        val calls = mutableListOf<PendingCall>()
        val repo = PayCodeRepository { request -> PendingCall(request).also { calls.add(it) } }
        val old = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }
        old.cancelAndJoin()
        val fresh = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }
        val body = Body("<input id=\"code\" value=\"deadbeef\" />")

        calls[1].respond(body)

        assertEquals(PayCodeRepository.Result.Ok("deadbeef", "", "", ""), fresh.await())
        assertTrue(calls[0].isCanceled())
        assertFalse(calls[1].isCanceled())
        assertTrue(body.closed)
    }

    @Test
    fun `网络错误正常返回且不会取消调用`() = runBlocking {
        lateinit var call: PendingCall
        val repo = PayCodeRepository { request -> PendingCall(request).also { call = it } }
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }

        call.callback.onFailure(call, IOException("network unavailable"))

        assertEquals(PayCodeRepository.Result.Error("network unavailable"), result.await())
        assertFalse(call.isCanceled())
    }

    @Test
    fun `HTTP 错误返回状态码并关闭响应`() = runBlocking {
        lateinit var call: PendingCall
        val repo = PayCodeRepository { request -> PendingCall(request).also { call = it } }
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }
        val body = Body("unavailable")

        call.respond(body, 503)

        assertEquals(PayCodeRepository.Result.Error("HTTP 503"), result.await())
        assertTrue(body.closed)
    }

    @Test
    fun `响应体读取在线程池执行`() = runBlocking {
        lateinit var call: PendingCall
        val caller = Thread.currentThread()
        val responseThread = AtomicReference<Thread>()
        val repo = PayCodeRepository { request -> PendingCall(request).also { call = it } }
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.fetch("first", "9") }
        val body = Body("<input id=\"code\" value=\"deadbeef\" />") {
            responseThread.set(Thread.currentThread())
        }

        call.respond(body)

        assertEquals(PayCodeRepository.Result.Ok("deadbeef", "", "", ""), result.await())
        assertNotSame(caller, responseThread.get())
        assertTrue(body.closed)
    }

    private class PendingCall(private val request: Request) : Call {
        lateinit var callback: Callback
        private var canceled = false

        override fun request() = request
        override fun execute(): Response = error("Asynchronous request expected")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
        }
        override fun cancel() {
            canceled = true
        }
        override fun isExecuted() = this::callback.isInitialized
        override fun isCanceled() = canceled
        override fun timeout() = Timeout()
        override fun clone() = PendingCall(request)

        fun respond(body: ResponseBody, code: Int = 200) {
            callback.onResponse(
                this,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("response")
                    .body(body)
                    .build()
            )
        }
    }

    private class Body(content: String, private val onRead: () -> Unit = {}) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(content)
        private val length = buffer.size
        var closed = false

        override fun contentType() = "text/html; charset=utf-8".toMediaType()
        override fun contentLength() = length
        override fun source() = buffer.apply { onRead() }
        override fun close() {
            closed = true
            super.close()
        }
    }
}
