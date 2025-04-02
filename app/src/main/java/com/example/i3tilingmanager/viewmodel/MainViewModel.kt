package com.example.i3tilingmanager.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.service.TilingManagerService
import com.example.i3tilingmanager.util.AccessibilityUtil
import com.example.i3tilingmanager.util.FreeformUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel for the main activity.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = getApplication<I3TilingManagerApplication>()
    
    // Track whether the accessibility service is enabled
    val isAccessibilityServiceEnabled = mutableStateOf(false)
    
    init {
        // Initialize state
        checkServiceStatus()
    }
    
    /**
     * Check if the accessibility service is enabled.
     */
    private fun checkServiceStatus() {
        isAccessibilityServiceEnabled.value = AccessibilityUtil.isAccessibilityServiceEnabled(
            getApplication(),
            TilingManagerService::class.java
        )
    }
    
    /**
     * Enable freeform mode on the device.
     * Note: This requires WRITE_SECURE_SETTINGS permission which is only available
     * to system apps or via ADB.
     */
    fun enableFreeformMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = FreeformUtil.enableFreeformMode(getApplication())
            if (success) {
                app.isFreeformEnabled.value = true
            }
        }
    }
    
    /**
     * Request the user to enable the accessibility service.
     */
    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
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
            
            // TODO: Notify the service about the workspace change
        }
    }
}
