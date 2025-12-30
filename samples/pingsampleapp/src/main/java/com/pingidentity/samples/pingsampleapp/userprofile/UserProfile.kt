/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.userprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun UserProfile(userProfileViewModel: UserProfileViewModel) {
    val state by userProfileViewModel.state.collectAsState()

    LaunchedEffect(true) {
        // Not relaunch when recomposition
        userProfileViewModel.userinfo()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier =
                Modifier.padding(8.dp)
                    .fillMaxWidth(),
        ) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "First name: ${state.user?.get("name")}",
                    Modifier.fillMaxWidth().padding(4.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    "Family name: ${state.user?.get("family_name")}",
                    Modifier.fillMaxWidth().padding(4.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Email: ${state.user?.get("email")}", Modifier.fillMaxWidth().padding(4.dp))

                Button(
                    modifier = Modifier.padding(8.dp).align(Alignment.End),
                    onClick = { userProfileViewModel.toggleDeviceInfo() }
                ) {
                    Text(text = if (state.showRawUserInfo) "Hide Info" else "Show Raw User Info")
                }

                if (state.showRawUserInfo) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = userProfileViewModel.formattedUserInfo,
                    )
                }
            }
        }
    }

}

@Preview
@Composable
fun PreviewUserProfile() {
    UserProfile(UserProfileViewModel())
}