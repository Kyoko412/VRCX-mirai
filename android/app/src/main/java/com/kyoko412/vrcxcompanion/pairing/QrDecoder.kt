package com.kyoko412.vrcxcompanion.pairing

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

internal fun decodeQrLuma(luma: ByteArray, width: Int, height: Int): String? {
    var pixels = luma
    var currentWidth = width
    var currentHeight = height
    val reader = MultiFormatReader()
    repeat(4) { attempt ->
        val source = PlanarYUVLuminanceSource(
            pixels, currentWidth, currentHeight, 0, 0, currentWidth, currentHeight, false
        )
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: ReaderException) {
            reader.reset()
        }
        if (attempt < 3) {
            val rotated = ByteArray(pixels.size)
            for (y in 0 until currentHeight) for (x in 0 until currentWidth)
                rotated[(currentWidth - 1 - x) * currentHeight + y] = pixels[y * currentWidth + x]
            pixels = rotated
            val oldWidth = currentWidth
            currentWidth = currentHeight
            currentHeight = oldWidth
        }
    }
    return null
}
