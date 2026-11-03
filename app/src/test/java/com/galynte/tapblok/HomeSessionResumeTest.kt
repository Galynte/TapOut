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

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The home screen reads attempt count and remaining time only on resume.
 * Starting a session has to publish those values before that resume.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeSessionResumeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(GrantHomePermissions())
        .around(composeRule)

    @Test
    fun resumeShowsNewSessionInsteadOfPreviousAttemptCountAndRemainingTime() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val previousStart = System.currentTimeMillis() - 20 * 60_000L
        base.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_monitoring_active", false)
            .putInt("blocked_app_attempts", 7)
            .putInt("monitoring_duration_minutes", 30)
            .putLong("session_start_timestamp", previousStart)
            .putBoolean(QrToggle.PREF_ENABLED, false)
            .commit()

        val context = object : ContextWrapper(base) {
            override fun startForegroundService(service: Intent): ComponentName {
                return ComponentName(base, AppMonitoringService::class.java)
            }

            override fun startService(service: Intent): ComponentName {
                return ComponentName(base, AppMonitoringService::class.java)
            }
        }
        toggleMonitoring(context)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        val start = base.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getLong("session_start_timestamp", 0L)
        val elapsedMs = System.currentTimeMillis() - start
        val remainingMinutes = ((30 * 60_000L - elapsedMs).coerceAtLeast(0) / 60_000L).toInt()
        assertTrue(remainingMinutes == 29 || remainingMinutes == 30)

        composeRule.onNodeWithText("Monitoring is Active").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked App Attempts: 0").assertIsDisplayed()
        composeRule.onNode(
            hasText("Session auto-ends in 30 minutes") or hasText("Session auto-ends in 29 minutes")
        ).assertIsDisplayed()
    }
}

private class GrantHomePermissions : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                shadowOf(appOps).setMode(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                    AppOpsManager.MODE_ALLOWED
                )
                ShadowSettings.setCanDrawOverlays(true)
                base.evaluate()
            }
        }
    }
}
