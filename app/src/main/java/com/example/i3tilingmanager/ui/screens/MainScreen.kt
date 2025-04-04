package com.example.i3tilingmanager.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.model.Workspace
import com.example.i3tilingmanager.service.TilingManagerService
import com.example.i3tilingmanager.util.AccessibilityUtil
import com.example.i3tilingmanager.util.CommandManager
import com.example.i3tilingmanager.util.FreeformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced ViewModel for the main activity with better state management.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val app = getApplication<I3TilingManagerApplication>()

    // Track whether the accessibility service is enabled
    val isAccessibilityServiceEnabled = mutableStateOf(false)

    // Track whether freeform mode is enabled
    val isFreeformEnabled = mutableStateOf(false)

    // Current workspace flow
    private val _currentWorkspace = MutableStateFlow(0)
    val currentWorkspace: StateFlow<Int> = _currentWorkspace

    // Status message for user feedback
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        // Initialize state
        viewModelScope.launch {
            checkServicesStatus()
        }
    }

    /**
     * Check if the accessibility service and freeform mode are enabled.
     */
    fun checkServicesStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // Check accessibility service
            val isServiceEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(
                getApplication(),
                TilingManagerService::class.java
            )

            // Check freeform mode
            val isFreeform = FreeformUtil.isFreeformModeEnabled(getApplication())

            withContext(Dispatchers.Main) {
                isAccessibilityServiceEnabled.value = isServiceEnabled
                isFreeformEnabled.value = isFreeform
                app.isFreeformEnabled.value = isFreeform
                _isLoading.value = false

                if (isServiceEnabled && isFreeform) {
                    _statusMessage.value = "I3 Tiling Manager is ready"
                } else if (!isServiceEnabled && !isFreeform) {
                    _statusMessage.value = "Accessibility service and freeform mode need to be enabled"
                } else if (!isServiceEnabled) {
                    _statusMessage.value = "Accessibility service needs to be enabled"
                } else {
                    _statusMessage.value = "Freeform mode needs to be enabled"
                }
            }
        }
    }

    /**
     * Enable freeform mode on the device.
     */
    fun enableFreeformMode() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val success = FreeformUtil.enableFreeformMode(getApplication())
            val isFreeform = FreeformUtil.isFreeformModeEnabled(getApplication())

            withContext(Dispatchers.Main) {
                isFreeformEnabled.value = isFreeform
                app.isFreeformEnabled.value = isFreeform

                if (isFreeform) {
                    _statusMessage.value = "Freeform mode enabled successfully"
                } else {
                    _statusMessage.value = if (success)
                        "Please enable freeform mode in Developer Options"
                    else
                        "Failed to enable freeform mode automatically"
                }

                _isLoading.value = false
            }
        }
    }

    /**
     * Request the user to enable the accessibility service.
     */
    fun requestAccessibilityPermission(context: Context) {
        AccessibilityUtil.launchAccessibilitySettings(context)
        _statusMessage.value = "Please enable i3 Tiling Manager accessibility service"
    }

    /**
     * Refresh the current layout forcefully.
     */
    fun refreshLayout() {
        // Use CommandManager to request a layout refresh
        CommandManager.refreshLayout(getApplication())
        _statusMessage.value = "Forcefully refreshing window layout"

        // The command is sent via broadcast and will be picked up by the service
        Log.d(TAG, "Requested forceful layout refresh via CommandManager")
    }

    /**
     * Switch to a different workspace.
     */
    fun switchWorkspace(index: Int) {
        val app = getApplication<I3TilingManagerApplication>()
        val tilingConfig = app.tilingConfiguration.value

        if (index >= 0 && index < tilingConfig.workspaces.size) {
            // Update active workspace in the configuration
            val updatedConfig = tilingConfig.copy(activeWorkspace = index)
            app.tilingConfiguration.value = updatedConfig

            // Update current workspace state
            _currentWorkspace.value = index

            // Log workspace switch
            Log.d(TAG, "Switching to workspace: ${tilingConfig.workspaces[index].name} (index: $index)")

            // Use CommandManager to notify the service of the workspace change
            CommandManager.switchWorkspace(getApplication(), index)

            _statusMessage.value = "Switched to workspace: ${tilingConfig.workspaces[index].name}"
        } else {
            _statusMessage.value = "Invalid workspace index: $index"
        }
    }

    /**
     * Clear the status message.
     */
    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Get a list of all workspaces.
     */
    fun getWorkspaces(): List<Workspace> {
        return app.tilingConfiguration.value.workspaces
    }
}

/**
 * Main screen composable that displays the tiling manager UI
 */
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

        Button(
            onClick = {
                viewModel.refreshLayout()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Force Apply Tiling")
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