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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.galynte.tapblok.ui.components.TapOutScaffold
import com.galynte.tapblok.ui.theme.TapOutTheme

class SettingsActivity : ComponentActivity() {
    companion object {
        private const val PREF_BREAK_DURATION_MINUTES = "break_duration_minutes"
        private const val PREF_ENABLE_BREAKS = "enable_breaks"
        private const val PREF_MONITORING_DURATION_MINUTES = "monitoring_duration_minutes"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isMonitoringActive = intent.getBooleanExtra("is_monitoring_active", false)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentEnableQrCode = prefs.getBoolean(QrToggle.PREF_ENABLED, false)
        val currentBreakDurationMinutes = prefs.getInt(PREF_BREAK_DURATION_MINUTES, 0) // 0 = disabled
        val currentMonitoringDurationMinutes = prefs.getInt(PREF_MONITORING_DURATION_MINUTES, 0)
        setContent {
            TapOutTheme {
                SettingsScreen(
                    onBackClick = { finish() },
                    isMonitoringActive = isMonitoringActive,
                    enableQrCode = currentEnableQrCode,
                    breakDurationMinutes = currentBreakDurationMinutes,
                    monitoringDurationMinutes = currentMonitoringDurationMinutes,
                    onEnableQrCodeChanged = { enabled ->
                        prefs.edit { putBoolean(QrToggle.PREF_ENABLED, enabled) }
                    },
                    onBreakOptionChanged = { minutes ->
                        prefs.edit {
                            putInt(PREF_BREAK_DURATION_MINUTES, minutes)
                            putBoolean(PREF_ENABLE_BREAKS, minutes > 0)
                        }
                    },
                    onMonitoringDurationChanged = { minutes ->
                        prefs.edit { putInt(PREF_MONITORING_DURATION_MINUTES, minutes) }
                    },
                    onWriteNfcTagClick = {
                        startActivity(Intent(this@SettingsActivity, NfcWriteActivity::class.java))
                    },
                    onShowQrCodeClick = {
                        startActivity(Intent(this@SettingsActivity, QrCodeActivity::class.java))
                    },
                    onEditPermissionsClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    isMonitoringActive: Boolean,
    enableQrCode: Boolean,
    breakDurationMinutes: Int, // 0 = disabled
    monitoringDurationMinutes: Int, // 0 = infinite
    onEnableQrCodeChanged: (Boolean) -> Unit,
    onBreakOptionChanged: (Int) -> Unit,
    onMonitoringDurationChanged: (Int) -> Unit,
    onWriteNfcTagClick: () -> Unit,
    onShowQrCodeClick: () -> Unit,
    onEditPermissionsClick: () -> Unit
) {
    var localEnableQrCode by remember { mutableStateOf(enableQrCode) }
    var localBreakDurationMinutes by remember { mutableStateOf(breakDurationMinutes) }
    var localMonitoringDurationMinutes by remember { mutableStateOf(monitoringDurationMinutes) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    LaunchedEffect(enableQrCode, breakDurationMinutes, monitoringDurationMinutes) {
        localEnableQrCode = enableQrCode
        localBreakDurationMinutes = breakDurationMinutes
        localMonitoringDurationMinutes = monitoringDurationMinutes
    }
    TapOutScaffold(
        title = "Settings",
        onBackClick = onBackClick
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // General Settings
            Text(
                text = "General Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Activate NFC Tag") },
                supportingContent = { Text("Authorizes an NFC tag") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onWriteNfcTagClick)
            )
            HorizontalDivider()
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Enable QR Code controls") },
                        supportingContent = {
                            Text("Displays Scan QR Code button on the main screen")
                        },
                        trailingContent = {
                            Switch(
                                checked = localEnableQrCode,
                                onCheckedChange = { isChecked ->
                                    localEnableQrCode = isChecked
                                    onEnableQrCodeChanged(isChecked)
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (localEnableQrCode) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                        ListItem(
                            headlineContent = { Text("Display QR Code") },
                            supportingContent = { Text("Printable code for the phone camera") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.SubdirectoryArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(onClick = onShowQrCodeClick)
                        )
                    }
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Edit Permissions") },
                supportingContent = { Text("Manage app permissions in system settings") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onEditPermissionsClick)
            )
            HorizontalDivider()
            // Monitoring Settings
            Text(
                text = "Monitoring Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Monitoring Duration") },
                supportingContent = {
                    Column {
                        Text(
                            text = "Automatically stop monitoring after selected time duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val options = listOf(0, 15, 30, 60)
                        val labels = listOf("Disabled", "15 min", "30 min", "60 min")
                        val selectedIndex = options.indexOf(localMonitoringDurationMinutes).coerceAtLeast(0)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            options.forEachIndexed { index, minutes ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = {
                                        if (!isMonitoringActive) {
                                            localMonitoringDurationMinutes = minutes
                                            onMonitoringDurationChanged(minutes)
                                        }
                                    },
                                    selected = index == selectedIndex,
                                    enabled = !isMonitoringActive
                                ) {
                                    Text(labels[index])
                                }
                            }
                        }
                        if (isMonitoringActive) {
                            Text(
                                text = "Stop monitoring to change session duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            )
            HorizontalDivider()

            // Focus Session Settings
            Text(
                text = "Focus Session Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Take a Break") },
                supportingContent = {
                    Column {
                        Text(
                            text = "Allow bypassing blocked apps for a set duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val options = listOf(0, 5, 15, 30)
                        val labels = listOf("Disabled", "5 min", "15 min", "30 min")
                        val selectedIndex = options.indexOf(localBreakDurationMinutes).coerceAtLeast(0)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            options.forEachIndexed { index, minutes ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = {
                                        if (!isMonitoringActive) {
                                            localBreakDurationMinutes = minutes
                                            onBreakOptionChanged(minutes)
                                        }
                                    },
                                    selected = index == selectedIndex,
                                    enabled = !isMonitoringActive
                                ) {
                                    Text(labels[index])
                                }
                            }
                        }
                        if (isMonitoringActive) {
                            Text(
                                text = "Stop monitoring to change break settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            )
            HorizontalDivider()

            // Open Source Licenses
            ListItem(
                headlineContent = { Text("Open Source Licenses") },
                supportingContent = { Text("Third-party library licenses used in TapOut") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    showLicenseDialog = true
                }
            )
            HorizontalDivider()
            if (showLicenseDialog) {
                AlertDialog(
                    onDismissRequest = { showLicenseDialog = false },
                    title = { Text("Apache License 2.0") },
                    text = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            Text(
                                text = """
                                TapOut is a modified version of the original TapBlok project
                                created by cajdata (https://github.com/cajdata/TapBlok).

                                Apache License
                                Version 2.0, January 2004
                                http://www.apache.org/licenses/
                                TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
                                1. Definitions.
                                "License" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.
                                "Licensor" shall mean the copyright owner or entity authorized by the copyright owner that is granting the License.
                                "Legal Entity" shall mean the union of the acting entity and all other entities that control, are controlled by, or are under common control with that entity. For the purposes of this definition, "control" means (i) the power, direct or indirect, to cause the direction or management of such entity, whether by contract or otherwise, or (ii) ownership of fifty percent (50%) or more of the outstanding shares, or (iii) beneficial ownership of such entity.
                                "You" (or "Your") shall mean an individual or Legal Entity exercising permissions granted by this License.
                                ... (full text continues)
                                See full license at: http://www.apache.org/licenses/LICENSE-2.0
                                TapOut uses several open source libraries under Apache 2.0, including:
                                • Jetpack Compose
                                • AndroidX libraries
                                • Room persistence library
                                • Kotlin standard library
                                Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                            """.trimIndent(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLicenseDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}