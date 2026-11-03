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

import android.net.Uri

/**
 * Printed codes use [CONTENT] so a phone camera can hand the link to TapOut.
 * The host is the [TOKEN] the previous plain-text code used to carry.
 */
object QrToggle {
    const val PREF_ENABLED = "enable_qr_code"
    const val TOKEN = "TAGOUT_TOGGLE"
    const val SCHEME = "tagout"

    /** Link encoded by the on-screen code and docs/assets/images/tagout-toggle-qr.png. */
    const val CONTENT = "$SCHEME://$TOKEN"

    fun matches(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return false
        if (value.equals(TOKEN, ignoreCase = true)) return true
        val uri = Uri.parse(value)
        return SCHEME.equals(uri.scheme, ignoreCase = true) &&
            TOKEN.equals(uri.host, ignoreCase = true)
    }
}
