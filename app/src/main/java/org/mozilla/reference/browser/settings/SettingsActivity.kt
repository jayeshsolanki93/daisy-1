/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.R.id.content
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.reference.browser.R.color.navigationBarColor
import org.mozilla.reference.browser.R.color.statusBarColor
import org.mozilla.reference.browser.ext.setSystemBarsTheme

class SettingsActivity : AppCompatActivity(), SettingsFragment.ActionBarUpdater {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSystemBarsTheme(statusBarColor, navigationBarColor)

        if (savedInstanceState == null) {
            with(supportFragmentManager.beginTransaction()) {
                replace(content, SettingsFragment())
                commit()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun updateTitle(titleResId: Int) {
        setTitle(titleResId)
    }

    override fun onBackPressed() {
        supportFragmentManager.fragments.forEach {
            if (it is UserInteractionHandler && it.onBackPressed()) {
                return
            } else {
                super.onBackPressed()
            }
        }
    }
}
