package com.firmmy.dashcam.feature.recorder

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCodeImage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(text) { createQrBitmap(text) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Remote connection QR code",
        modifier = modifier.size(220.dp),
    )
}

private fun createQrBitmap(text: String): Bitmap {
    val size = 768
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
}
