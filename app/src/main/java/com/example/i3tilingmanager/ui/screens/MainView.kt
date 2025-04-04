package com.example.i3tilingmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i3tilingmanager.viewmodel.MainViewModel
import android.content.Intent

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val isAccessibilityServiceEnabled by viewModel.isAccessibilityServiceEnabled
    val isFreeformEnabled by viewModel.isFreeformEnabled
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Accessibility Service Enabled: $isAccessibilityServiceEnabled")
        Text(text = "Freeform Mode Enabled: $isFreeformEnabled")

        statusMessage?.let {
            Text(text = "Status: $it")
        }

        // Add buttons for testing
        Button(
            onClick = {
                viewModel.refreshLayout()
            }
        ) {
            Text("Refresh Layout")
        }

        Button(
            onClick = {
                val intent = Intent()
                intent.action = "LAUNCH_APP_IN_FREEFORM"
                intent.putExtra("package_name", "com.android.settings")
                context.sendBroadcast(intent)
            }
        ) {
            Text("Launch Settings in Freeform")
        }

        Button(
            onClick = {
                val intent = Intent()
                intent.action = "LAUNCH_APP_IN_FREEFORM"
                intent.putExtra("package_name", "com.google.android.calculator")
                context.sendBroadcast(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Launch Calculator in Freeform")
        }

        // Add a section to switch workspaces
        Text(
            text = "Workspaces",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 4) {
                Button(
                    onClick = { viewModel.switchWorkspace(i) }
                ) {
                    Text("Workspace ${i+1}")
                }
            }
        }
    }
}