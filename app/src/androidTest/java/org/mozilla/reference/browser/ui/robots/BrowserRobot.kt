/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.containsString
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ext.waitAndInteract

/**
 * Implementation of Robot Pattern for browser action.
 */
class BrowserRobot {
    /* Asserts that the text within DOM element with ID="testContent" has the given text, i.e.
    * document.querySelector('#testContent').innerText == expectedText
    */
    fun verifyPageContent(expectedText: String) {
        val mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mDevice.waitAndInteract(Until.findObject(By.textContains(expectedText))) {}
    }

    fun verifyFXAUrl() {
        verifyUrl("https://accounts.firefox.com")
    }

    fun verifySupportUrl() {
        verifyUrl("https://cliqz.com/en/support")
    }

    fun verifyFreshTabView() {
        val packageName = instrumentation.targetContext.packageName
        mDevice.waitAndInteract(Until.findObject(By.res(packageName, "url_bar_view"))) {}
    }

    private fun verifyUrl(expectedUrl: String) {
        val mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mDevice.waitAndInteract(Until.findObject(By.textContains(expectedUrl))) {}
    }

    fun verifyAboutBrowser() {
        // Testing About Reference Browser crashes in Java String
        // https://github.com/mozilla-mobile/reference-browser/issues/680
    }

    fun verifyCustomUrl(url: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val packageName = instrumentation.targetContext.packageName
        device.waitAndInteract(Until.findObject(By.res(packageName, "mozac_browser_toolbar_url_view"))) {}
        onView(withId(R.id.mozac_browser_toolbar_url_view))
            .check(matches(withText(containsString(url))))
    }

    class Transition {
        private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        fun openNavigationToolbar(interact: NavigationToolbarRobot.() -> Unit): NavigationToolbarRobot.Transition {
            device.pressMenu()

            NavigationToolbarRobot().interact()
            return NavigationToolbarRobot.Transition()
        }

        fun openThreeDotMenu(interact: ThreeDotMenuRobot.() -> Unit): ThreeDotMenuRobot.Transition {
            mDevice.waitAndInteract(Until.findObject(By.desc("Menu"))) {
                click()
            }
            ThreeDotMenuRobot().interact()
            return ThreeDotMenuRobot.Transition()
        }
    }
}

fun browser(interact: BrowserRobot.() -> Unit): BrowserRobot.Transition {
    BrowserRobot().interact()
    return BrowserRobot.Transition()
}
