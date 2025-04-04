package com.example.i3tilingmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i3tilingmanager.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val isAccessibilityServiceEnabled by viewModel.isAccessibilityServiceEnabled
    val isFreeformEnabled by viewModel.isFreeformEnabled
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column {
        Text(text = "Accessibility Service Enabled: $isAccessibilityServiceEnabled")
        Text(text = "Freeform Mode Enabled: $isFreeformEnabled")
        statusMessage?.let {
            Text(text = "Status: $it")
        }
    }
}