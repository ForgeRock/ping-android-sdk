/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfapp.R

@Composable
fun AppDrawer(
    navigateTo: (String) -> Unit,
    closeDrawer: () -> Unit,
) {
    val scroll = rememberScrollState(0)

    ModalDrawerSheet(
        modifier = Modifier.verticalScroll(scroll),
    ) {
        Logo(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(id = R.string.accounts_drawer_label)) },
            selected = false,
            icon = { Icon(Icons.Filled.People, null) },
            onClick = {
                navigateTo("accounts")
                closeDrawer()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(id = R.string.drawer_launch_davinci)) },
            selected = false,
            icon = { Icon(Icons.Filled.RocketLaunch, null) },
            onClick = {
                navigateTo("davinci-launcher")
                closeDrawer()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(id = R.string.settings_screen_title)) },
            selected = false,
            icon = { Icon(Icons.Filled.Settings, null) },
            onClick = {
                navigateTo("settings")
                closeDrawer()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(id = R.string.about_screen_title)) },
            selected = false,
            icon = { Icon(Icons.Filled.Info, null) },
            onClick = {
                navigateTo("about")
                closeDrawer()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = Color.Black)
                .then(modifier),
    ) {
        Icon(
            painterResource(R.drawable.ping_logo),
            contentDescription = null,
            modifier =
                Modifier
                    .height(100.dp)
                    .padding(8.dp)
                    .then(modifier),
            tint = Color.Unspecified,
        )
    }
}
