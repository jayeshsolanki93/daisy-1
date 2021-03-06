/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.freshtab

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.concept.toolbar.Toolbar
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.freshtab.toolbar.ActionContainer
import org.mozilla.reference.browser.freshtab.toolbar.MenuButton

class FreshTabToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppBarLayout(context, attrs, defStyleAttr) {

    private val freshTabMenu: MenuButton
    private val browserActions: ActionContainer
    private val urlBarView: View

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.fresh_tab_toolbar, this, true)
        freshTabMenu = view.findViewById(R.id.fresh_tab_toolbar_menu)
        browserActions = view.findViewById(R.id.fresh_tab_toolbar_browser_actions)
        urlBarView = view.findViewById(R.id.url_bar_view)
    }

    fun setMenuBuilder(menuBuilder: BrowserMenuBuilder) {
        freshTabMenu.menuBuilder = menuBuilder
    }

    fun setSearchBarClickListener(clickListener: OnClickListener) {
        urlBarView.setOnClickListener(clickListener)
    }

    fun addBrowserAction(browserAction: Toolbar.Action) {
        browserActions.addAction(browserAction)
    }
}
