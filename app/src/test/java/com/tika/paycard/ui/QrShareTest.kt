package com.tika.paycard.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrShareTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `分享 PNG 为不透明白底并能解码为原付款码`() = runBlocking {
        val file = QrShare.createImage(context, CODE)
        val signature = file.inputStream().use { it.readNBytes(8) }
        assertTrue(signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        val bitmap = readBitmap(file)
        val pixels = pixels(bitmap)
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })
        assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
        assertEquals(CODE, decode(bitmap))
    }

    @Test
    fun `再次分享不会覆盖前一张图片`() = runBlocking {
        val first = QrShare.createImage(context, CODE)
        val second = QrShare.createImage(context, OTHER_CODE)

        assertNotEquals(first, second)
        assertEquals(CODE, decode(readBitmap(first)))
        assertEquals(OTHER_CODE, decode(readBitmap(second)))
    }

    @Test
    fun `新分享清除旧图片并保留最近分享`() = runBlocking {
        val old = QrShare.createImage(context, CODE)
        assertTrue(old.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)))
        val recent = QrShare.createImage(context, OTHER_CODE)

        assertFalse(old.exists())
        assertEquals(OTHER_CODE, decode(readBitmap(recent)))
    }

    @Test
    fun `生成失败不留下空文件且保留已分享图片`() = runBlocking {
        val recent = QrShare.createImage(context, CODE)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { QrShare.createImage(context, "") }
        }

        assertEquals(listOf(recent), recent.parentFile!!.listFiles()!!.toList())
        assertEquals(CODE, decode(readBitmap(recent)))
    }

    @Test
    fun `图片生成后取消交付删除未分享文件`() = runBlocking {
        val recent = QrShare.createImage(context, OTHER_CODE)
        val directory = File(context.cacheDir, "qr")
        val resumes = LinkedBlockingQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                resumes.add(block)
            }
        }
        val result = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            QrShare.createImage(context, CODE)
        }
        val resume = checkNotNull(resumes.poll(10, TimeUnit.SECONDS))
        assertEquals(2, directory.listFiles()!!.size)

        result.cancel()
        resume.run()
        while (!result.isCompleted) {
            checkNotNull(resumes.poll(10, TimeUnit.SECONDS)).run()
        }
        result.join()

        assertTrue(result.isCancelled)
        assertEquals(listOf(recent), directory.listFiles()!!.toList())
        assertEquals(OTHER_CODE, decode(readBitmap(recent)))
    }

    @Test
    fun `清理仅删除超过一天的付款码图片`() = runBlocking {
        val old = QrShare.createImage(context, CODE)
        val recent = QrShare.createImage(context, OTHER_CODE)
        val directory = old.parentFile!!
        val unrelated = File(directory, "notes.txt").apply { writeText("保留") }
        val otherImage = File(directory, "other.png").apply { writeText("保留") }
        val child = File(directory, "paycode-folder.png").apply { mkdir() }
        val outside = File(context.cacheDir, "paycode-outside.png").apply { writeText("保留") }
        val oldTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        listOf(old, unrelated, otherImage, child, outside).forEach {
            assertTrue(it.setLastModified(oldTime))
        }
        assertTrue(recent.setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12)))

        QrShare.clearOldImages(context)

        assertFalse(old.exists())
        assertEquals(OTHER_CODE, decode(readBitmap(recent)))
        assertTrue(listOf(unrelated, otherImage, child, outside).all { it.exists() })
    }

    @Test
    fun `重复清理不删除保留期内的图片`() = runBlocking {
        val old = QrShare.createImage(context, CODE)
        val recent = QrShare.createImage(context, OTHER_CODE)
        assertTrue(old.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)))

        QrShare.clearOldImages(context)
        QrShare.clearOldImages(context)

        assertFalse(old.exists())
        assertEquals(OTHER_CODE, decode(readBitmap(recent)))
    }

    @Test
    fun `分享提供器仅开放二维码缓存目录并要求临时授权`() = runBlocking {
        val info = context.packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider", PackageManager.GET_META_DATA
        )!!
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
        val roots = mutableListOf<Pair<String, String>>()
        info.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS").use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name != "paths") {
                    roots.add(parser.name to parser.getAttributeValue(null, "path"))
                }
            }
        }
        assertEquals(listOf("cache-path" to "qr/"), roots)
        val file = QrShare.createImage(context, CODE)
        assertEquals(File(context.cacheDir, "qr").canonicalFile, file.parentFile?.canonicalFile)
    }

    private fun readBitmap(file: File): Bitmap = file.inputStream().use { BitmapFactory.decodeStream(it)!! }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).apply {
        bitmap.getPixels(this, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun decode(bitmap: Bitmap): String {
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels(bitmap))
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    companion object {
        private const val CODE = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"
        private const val OTHER_CODE = "1234567890abcdef1234567890abcdef"
    }
}
