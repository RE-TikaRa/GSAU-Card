package com.tika.paycard.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 把付款码内容渲染成二维码 Bitmap。内容就是页面 id="code" 的那串十六进制。
 */
object QrGenerator {

    /** 渲染像素尺寸档位:组件小、主界面中、全屏大。数值越大越清晰。 */
    const val SIZE_WIDGET = 288
    const val SIZE_CARD = 600
    const val SIZE_FULLSCREEN = 700

    fun encode(
        content: String,
        size: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.TRANSPARENT
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix[x, y]) foreground else background
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
