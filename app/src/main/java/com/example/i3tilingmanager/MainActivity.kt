package com.example.i3tilingmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i3tilingmanager.service.TilingManagerService
import com.example.i3tilingmanager.ui.screens.MainScreen
import com.example.i3tilingmanager.ui.theme.I3TilingManagerTheme
import com.example.i3tilingmanager.util.AccessibilityUtil
import com.example.i3tilingmanager.util.CommandManager
import com.example.i3tilingmanager.util.FreeformUtil
import com.example.i3tilingmanager.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            I3TilingManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val app = I3TilingManagerApplication.getInstance()
                    
                    MainContent(
                        isFreeformEnabled = app.isFreeformEnabled.value,
                        isAccessibilityEnabled = viewModel.isAccessibilityServiceEnabled.value,
                        onEnableFreeform = { viewModel.enableFreeformMode() },
                        onStartService = { startTilingService() }
                    )
                }
            }
        }
    }
    
    private fun startTilingService() {
        if (!AccessibilityUtil.isAccessibilityServiceEnabled(this, TilingManagerService::class.java)) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }
}

@Composable
fun MainContent(
    isFreeformEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    onEnableFreeform: () -> Unit,
    onStartService: () -> Unit
) {

    val context = LocalContext.current
    Button(
        onClick = {
            CommandManager.refreshLayout(context)
        }
    ) {
        Text("Force Apply Tiling")
    }
    if (!isFreeformEnabled || !isAccessibilityEnabled) {
        SetupScreen(
            isFreeformEnabled = isFreeformEnabled,
            isAccessibilityEnabled = isAccessibilityEnabled,
            onEnableFreeform = onEnableFreeform,
            onStartService = onStartService
        )
    } else {
        // Main application UI
        MainScreen()
    }
}

@Composable
fun SetupScreen(
    isFreeformEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    onEnableFreeform: () -> Unit,
    onStartService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "i3 Tiling Window Manager for Android",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SetupCard(
            title = "Enable Freeform Mode",
            description = "Freeform mode allows windows to be resized and positioned freely.",
            isEnabled = isFreeformEnabled,
            buttonText = "Enable Freeform Mode",
            onClick = onEnableFreeform
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SetupCard(
            title = "Enable Accessibility Service",
            description = "This service is required to manage windows and applications.",
            isEnabled = isAccessibilityEnabled,
            buttonText = "Open Accessibility Settings",
            onClick = onStartService
        )
    }
}

@Composable
fun SetupCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (isEnabled) {
                    Text(
                        text = "Enabled",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (!isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = onClick) {
                    Text(text = buttonText)
                }
            }
        }
    }
}
