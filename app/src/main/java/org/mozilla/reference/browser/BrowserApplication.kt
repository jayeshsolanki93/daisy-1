/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import io.sentry.android.core.SentryAndroid
import mozilla.components.browser.session.Session
import mozilla.components.concept.push.PushProcessor
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.rustlog.RustLog
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.reference.browser.ext.isCrashReportActive

open class BrowserApplication : Application() {

    open val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()

        setupSentry(this)

        RustHttpConfig.setClient(lazy { components.core.client })
        setupLogging()

        if (!isMainProcess()) {
            // If this is not the main process then do not continue with the initialization here. Everything that
            // follows only needs to be done in our app's main process and should not be done in other processes like
            // a GeckoView child process or the crash handling process. Most importantly we never want to end up in a
            // situation where we create a GeckoRuntime from the Gecko child process (
            return
        }

        components.core.engine.warmUp()
        GlobalAddonDependencyProvider.initialize(
                components.core.addonManager,
                components.core.addonUpdater
        )
        WebExtensionSupport.initialize(
            runtime = components.core.engine,
            store = components.core.store,
            onNewTabOverride = { _, engineSession, url ->
                val session = Session(url)
                components.core.sessionManager.add(session, true, engineSession)
                session.id
            },
            onCloseTabOverride = { _, sessionId ->
                components.useCases.tabsUseCases.removeTab(sessionId)
            },
            onSelectTabOverride = { _, sessionId ->
                val selected = components.core.sessionManager.findSessionById(sessionId)
                selected?.let { components.useCases.tabsUseCases.selectTab(it) }
            },
            onUpdatePermissionRequest = components.core.addonUpdater::onUpdatePermissionRequest
        )

        components.backgroundServices.pushFeature?.let {
            PushProcessor.install(it)

            it.initialize()
        }

        setupDayNightTheme()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            components.core.sessionManager.onTrimMemory(level)
        }
    }

    private fun setupDayNightTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}

private fun setupLogging() {
    // We want the log messages of all builds to go to Android logcat
    Log.addSink(AndroidLogSink())
    RustLog.enable()
}

private fun setupSentry(application: BrowserApplication) {
    if (isCrashReportActive) {
        // Set options here if needed
        SentryAndroid.init(application)
    }
}
