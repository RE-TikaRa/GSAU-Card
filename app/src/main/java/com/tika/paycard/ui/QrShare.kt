package com.tika.paycard.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.core.content.FileProvider
import com.tika.paycard.R
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.data.PayCodeRepository
import com.tika.paycard.qr.QrGenerator
import com.tika.paycard.widget.PayWidgetProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 取新付款码并分享白底二维码图片,文件保存在应用缓存中。 */
object QrShare {

    suspend fun share(context: Context, anchor: View, onRefreshed: () -> Unit) {
        val store = AccountStore.get(context)
        val account = store.current() ?: return
        AppDialog.notice(anchor, context.getString(R.string.share_preparing))
        var image: File? = null
        var shared = false
        try {
            val result = PayCodeManager.refresh(context, account)
            if (!account.sameCard(store.current())) return
            when (result) {
                is PayCodeRepository.Result.Ok -> {
                    onRefreshed()
                    PayWidgetProvider.refreshAll(context)
                    val file = createImage(context, result.code)
                    image = file
                    if (!account.sameCard(store.current())) return
                    if (!account.hasFreshCode()) {
                        AppDialog.notice(anchor, context.getString(R.string.share_expired))
                        return
                    }
                    val intent = createIntent(context, file)
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_qr)))
                    shared = true
                }
                is PayCodeRepository.Result.Invalid ->
                    AppDialog.notice(anchor, context.getString(R.string.pay_invalid))
                is PayCodeRepository.Result.Error ->
                    AppDialog.notice(anchor, context.getString(R.string.share_failed, result.message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: context.getString(R.string.share_image_failed)
            AppDialog.notice(anchor, context.getString(R.string.share_failed, message))
        } finally {
            if (!shared) image?.let { deleteImage(it) }
        }
    }

    private fun createIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(context.getString(R.string.share_qr), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal suspend fun createImage(context: Context, code: String): File {
        var image: File? = null
        var delivered = false
        try {
            val file = withContext(Dispatchers.IO) {
                clearOldImages(context)
                val directory = File(context.cacheDir, "qr").apply { mkdirs() }
                val bitmap = QrGenerator.encode(
                    code, QrGenerator.SIZE_FULLSCREEN, background = Color.WHITE, margin = 4
                )
                try {
                    val outputFile = File.createTempFile("paycode-", ".png", directory).also { image = it }
                    outputFile.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            throw IOException(context.getString(R.string.share_image_failed))
                        }
                    }
                    outputFile
                } finally {
                    bitmap.recycle()
                }
            }
            delivered = true
            return file
        } finally {
            // IO 已写完但尚未恢复调用协程时取消,文件仍由生成方清理。
            if (!delivered) image?.let { deleteImage(it) }
        }
    }

    internal suspend fun clearOldImages(context: Context) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            File(context.cacheDir, "qr").listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("paycode-") && file.extension == "png" &&
                    now - file.lastModified() >= IMAGE_RETENTION_MS) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun deleteImage(file: File) = withContext(NonCancellable + Dispatchers.IO) {
        file.delete()
    }

    // 分享目标可能延迟读取 URI,图片保留一天,不随分享面板关闭而删除。
    private val IMAGE_RETENTION_MS = TimeUnit.DAYS.toMillis(1)
}
