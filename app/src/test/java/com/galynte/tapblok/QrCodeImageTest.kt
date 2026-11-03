/*
 * Copyright 2026 Galynte
 *
 * This file is part of TapOut, a fork of TapBlok by cajdata.
 * Original: https://github.com/cajdata/TapBlok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.galynte.tapblok

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The README image has to be the symbol Display QR Code draws.
 * Decoding to the same link is not enough: another encoder paints a different
 * symbol for that link.
 */
class QrCodeImageTest {

    @Test
    fun printableImageMatchesDisplayQrCode() {
        val file = listOf(
            File("docs/assets/images/tagout-toggle-qr.png"),
            File("../docs/assets/images/tagout-toggle-qr.png")
        ).firstOrNull { it.isFile } ?: error("Printable QR image is missing from the repo")

        val image = ImageIO.read(file)
        val matrix = QrToggle.matrix()
        assertEquals(matrix.width, image.width)
        assertEquals(matrix.height, image.height)

        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = pixels[y * image.width + x]
                val dark = ((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF) < 128 * 3
                assertEquals("module at $x,$y", matrix.get(x, y), dark)
            }
        }

        val source = RGBLuminanceSource(image.width, image.height, pixels)
        val text = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        assertEquals(QrToggle.CONTENT, text)
    }
}
