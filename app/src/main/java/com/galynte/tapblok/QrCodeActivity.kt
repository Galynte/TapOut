/*
 * Copyright 2025-2026 Galynte
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

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.galynte.tapblok.ui.components.TapOutScaffold
import com.galynte.tapblok.ui.theme.TapOutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrCodeActivity : ComponentActivity() {

    companion object {
        const val QR_CODE_CONTENT = QrToggle.CONTENT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapOutTheme {
                TapOutScaffold(
                    title = "Your QR Code",
                    onBackClick = { finish() }
                ) { padding ->
                    QrCodeScreen(
                        modifier = Modifier.padding(padding),
                        content = QR_CODE_CONTENT
                    )
                }
            }
        }
    }
}

@Composable
fun QrCodeScreen(modifier: Modifier = Modifier, content: String) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, producer = {
        value = withContext(Dispatchers.Default) {
            generateQrCode(content)
        }
    })

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Scan this with your phone's camera to start or stop a monitoring session.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val bitMatrix = QrToggle.matrix(content)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

