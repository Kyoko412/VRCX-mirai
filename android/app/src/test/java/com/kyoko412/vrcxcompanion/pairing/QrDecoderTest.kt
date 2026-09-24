package com.kyoko412.vrcxcompanion.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    @Test fun blankCameraFrameDoesNotCrash() {
        assertNull(decodeQrLuma(ByteArray(97 * 53) { 0xff.toByte() }, 97, 53))
    }

    @Test fun decodesRotatedQrCode() {
        val value = "vrcx-companion-test"
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 97, 81)
        val width = matrix.width
        val height = matrix.height
        val rotated = ByteArray(width * height) { 0xff.toByte() }
        for (y in 0 until height) for (x in 0 until width)
            rotated[(width - 1 - x) * height + y] = if (matrix[x, y]) 0.toByte() else 0xff.toByte()

        assertEquals(value, decodeQrLuma(rotated, height, width))
    }
}
