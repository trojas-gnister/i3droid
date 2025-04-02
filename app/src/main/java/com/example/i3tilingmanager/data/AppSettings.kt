package com.example.i3tilingmanager.data

import androidx.compose.runtime.mutableStateOf

/**
 * Stores and manages application settings.
 */
class AppSettings {
    // Whether to automatically start the service on boot
    val autoStartOnBoot = mutableStateOf(true)
    
    // Whether to show window borders
    val showWindowBorders = mutableStateOf(true)
    
    // Key binding to switch workspaces (not implemented in this version)
    val workspaceSwitchShortcut = mutableStateOf("Alt+[1-9]")
    
    // Gap between windows in pixels
    val windowGap = mutableStateOf(4)
    
    // Whether to automatically apply tiling to new windows
    val autoTileNewWindows = mutableStateOf(true)
}
