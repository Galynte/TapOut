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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Receives tagout://TAGOUT_TOGGLE from the phone camera, or any other app
 * that opens that link, and toggles the monitoring session.
 */
class QrLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = intent?.dataString
        if (!QrToggle.matches(payload)) {
            finish()
            return
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(QrToggle.PREF_ENABLED, false)) {
            Toast.makeText(
                this,
                "Turn on QR code controls in TapOut settings to use this code.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val message = when (toggleMonitoring(this)) {
            MonitoringToggle.Started -> "Monitoring started."
            MonitoringToggle.Stopped -> "Monitoring stopped."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }
}
