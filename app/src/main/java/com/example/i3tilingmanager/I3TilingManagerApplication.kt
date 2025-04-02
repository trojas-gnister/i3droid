package com.example.i3tilingmanager

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.example.i3tilingmanager.data.AppSettings
import com.example.i3tilingmanager.data.DefaultWorkspaceConfig
import com.example.i3tilingmanager.model.TilingConfiguration
import com.example.i3tilingmanager.util.FreeformUtil

class I3TilingManagerApplication : Application() {
    
    // Application-level settings
    val appSettings = AppSettings()
    
    // Tiling configuration for managing workspace layouts
    val tilingConfiguration = mutableStateOf(TilingConfiguration())
    
    // Track whether freeform mode is enabled
    val isFreeformEnabled = mutableStateOf(false)
    
    override fun onCreate() {
        super.onCreate()
        appInstance = this
        
        // Initialize default workspace configurations
        initializeDefaultConfigs()
        
        // Check if freeform mode is enabled
        checkFreeformMode()
    }
    
    private fun initializeDefaultConfigs() {
        // Load default workspace configurations
        val defaultConfig = DefaultWorkspaceConfig.getDefaultConfig()
        tilingConfiguration.value = defaultConfig
    }
    
    private fun checkFreeformMode() {
        isFreeformEnabled.value = FreeformUtil.isFreeformModeEnabled(this)
    }
    
    companion object {
        private lateinit var appInstance: I3TilingManagerApplication
        
        fun getInstance(): I3TilingManagerApplication {
            return appInstance
        }
    }
}
