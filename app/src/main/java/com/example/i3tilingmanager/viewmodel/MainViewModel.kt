package com.example.i3tilingmanager.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * Handles communication with the TilingManagerService via CommandManager.
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

                // Set appropriate status message based on enabled services
                _statusMessage.value = when {
                    isServiceEnabled && isFreeform -> "I3 Tiling Manager is ready"
                    !isServiceEnabled && !isFreeform -> "Accessibility service and freeform mode need to be enabled"
                    !isServiceEnabled -> "Accessibility service needs to be enabled"
                    else -> "Freeform mode needs to be enabled"
                }

                // If we have the configuration, update the current workspace state
                val tilingConfig = app.tilingConfiguration.value
                _currentWorkspace.value = tilingConfig.activeWorkspace
            }
        }
    }

    /**
     * Enable freeform mode on the device.
     */
    fun enableFreeformMode() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _statusMessage.value = "Enabling freeform mode..."

            val success = FreeformUtil.enableFreeformMode(getApplication())

            // Check if it was successfully enabled
            val isFreeform = FreeformUtil.isFreeformModeEnabled(getApplication())

            withContext(Dispatchers.Main) {
                isFreeformEnabled.value = isFreeform
                app.isFreeformEnabled.value = isFreeform

                _statusMessage.value = if (isFreeform) {
                    "Freeform mode enabled successfully"
                } else if (success) {
                    "Please enable freeform mode in Developer Options"
                } else {
                    "Failed to enable freeform mode. Try using ADB: 'adb shell settings put global enable_freeform_support 1'"
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

        // Start a background check to see when the service is enabled
        viewModelScope.launch {
            // Wait a bit to let the user navigate to settings
            kotlinx.coroutines.delay(1000)

            // Check periodically (every 2 seconds) for 1 minute
            repeat(30) {
                // Check if the service is now enabled
                val isEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(
                    getApplication(),
                    TilingManagerService::class.java
                )

                if (isEnabled) {
                    withContext(Dispatchers.Main) {
                        isAccessibilityServiceEnabled.value = true
                        _statusMessage.value = "Accessibility service enabled"
                    }
                    return@launch
                }

                kotlinx.coroutines.delay(2000)
            }
        }
    }

    /**
     * Switch to a different workspace using CommandManager.
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

            val workspaceName = tilingConfig.workspaces[index].name
            _statusMessage.value = "Switched to workspace: $workspaceName"
        } else {
            _statusMessage.value = "Invalid workspace index: $index"
        }
    }

    /**
     * Refresh the current layout.
     */
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
     * Perform an action on a window.
     */
    fun windowAction(packageName: String, action: String) {
        // Use CommandManager to send the window action
        CommandManager.windowAction(getApplication(), packageName, action)

        // Set appropriate status message
        _statusMessage.value = when(action) {
            CommandManager.WINDOW_ACTION_MAXIMIZE -> "Maximizing window"
            CommandManager.WINDOW_ACTION_CLOSE -> "Closing window"
            CommandManager.WINDOW_ACTION_FOCUS -> "Focusing window"
            else -> "Performing window action: $action"
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

    /**
     * Get the current active workspace.
     */
    fun getCurrentWorkspace(): Workspace? {
        val config = app.tilingConfiguration.value
        val index = _currentWorkspace.value
        return config.workspaces.getOrNull(index)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources when the ViewModel is destroyed
    }
}