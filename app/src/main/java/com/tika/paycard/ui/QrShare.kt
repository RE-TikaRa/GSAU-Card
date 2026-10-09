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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 取新付款码并分享白底二维码图片,文件保存在应用缓存中。 */
object QrShare {

    suspend fun share(context: Context, anchor: View, onRefreshed: () -> Unit) {
        val store = AccountStore.get(context)
        val account = store.current() ?: return
        AppDialog.notice(anchor, context.getString(R.string.share_preparing))
        try {
            val result = PayCodeManager.refresh(context, account)
            if (!account.sameCard(store.current())) return
            when (result) {
                is PayCodeRepository.Result.Ok -> {
                    onRefreshed()
                    PayWidgetProvider.refreshAll(context)
                    val intent = createIntent(context, result.code)
                    if (!account.sameCard(store.current())) return
                    if (!account.hasFreshCode()) {
                        AppDialog.notice(anchor, context.getString(R.string.share_expired))
                        return
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_qr)))
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
        }
    }

    private suspend fun createIntent(context: Context, code: String): Intent {
        val file = createImage(context, code)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(context.getString(R.string.share_qr), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal suspend fun createImage(context: Context, code: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "qr").apply { mkdirs() }
        val file = File.createTempFile("paycode-", ".png", directory)
        val bitmap = QrGenerator.encode(
            code, QrGenerator.SIZE_FULLSCREEN, background = Color.WHITE, margin = 4
        )
        try {
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException(context.getString(R.string.share_image_failed))
                }
            }
        } finally {
            bitmap.recycle()
        }
        file
    }
}
