package com.example.i3tilingmanager.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.model.*
import com.example.i3tilingmanager.util.AppUtil
import com.example.i3tilingmanager.util.FreeformUtil
import kotlinx.coroutines.*

/**
 * Accessibility service that manages the tiling of windows in free form mode.
 * This service monitors window changes and applies tiling layouts based on the configuration.
 */
class TilingManagerService : AccessibilityService() {
    private val TAG = "TilingManagerService"
    
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val windowInfoList = mutableListOf<WindowInfo>()
    
    private val _activeWindows = MutableLiveData<List<WindowInfo>>(emptyList())
    val activeWindows: LiveData<List<WindowInfo>> = _activeWindows
    
    // Track whether the service is running
    private var isRunning = false
    
    // Current tiling configuration
    private lateinit var tilingConfig: TilingConfiguration
    
    // The current workspace index
    private var currentWorkspace = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TilingManagerService created")
        
        // Get application instance and config
        val app = I3TilingManagerApplication.getInstance()
        tilingConfig = app.tilingConfiguration.value
        
        // Start monitoring windows
        isRunning = true
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "TilingManagerService connected")
        
        // Initialize the service
        serviceScope.launch {
            initService()
        }
    }
    
    private suspend fun initService() {
        // Check if free form mode is enabled
        if (!FreeformUtil.isFreeformModeEnabled(this)) {
            Log.e(TAG, "Free form mode is not enabled")
            return
        }
        
        // Apply initial tiling layout
        applyTilingLayout()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRunning) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // A window's state has changed (e.g., opened, closed, etc.)
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Windows have changed (e.g., moved, resized, etc.)
                handleWindowsChanged()
            }
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        serviceScope.launch {
            updateWindowsList()
            
            val packageName = event.packageName?.toString() ?: return@launch
            
            // Check if this is a new window
            val isNewWindow = windowInfoList.none { it.packageName == packageName }
            
            if (isNewWindow) {
                Log.d(TAG, "New window opened: $packageName")
                
                // Get app info
                val appInfo = AppUtil.getAppInfo(packageManager, packageName)
                
                // Find an appropriate place for this app in the tiling layout
                val workspace = tilingConfig.workspaces.getOrNull(currentWorkspace)
                if (workspace != null && appInfo != null) {
                    addAppToLayout(workspace, appInfo)
                }
            }
            
            // Update active windows
            _activeWindows.value = windowInfoList.toList()
        }
    }
    
    private fun handleWindowsChanged() {
        serviceScope.launch {
            updateWindowsList()
            
            // Check if we need to re-apply the tiling layout
            if (windowInfoList.any { it.needsRepositioning }) {
                applyTilingLayout()
            }
            
            // Update active windows
            _activeWindows.value = windowInfoList.toList()
        }
    }
    
    private fun updateWindowsList() {
        val windows = windows?.filterNotNull() ?: return
        
        // Update our window info list
        val updatedList = mutableListOf<WindowInfo>()
        
        for (window in windows) {
            if (!window.isActive) continue
            
            val packageName = window.root?.packageName?.toString() ?: continue
            
            // Skip system windows and our own app
            if (packageName == "android" || packageName == packageName) {
                continue
            }
            
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            
            // Check if this window is already in our list
            val existingWindow = windowInfoList.find { it.packageName == packageName }
            
            if (existingWindow != null) {
                // Update existing window info
                existingWindow.bounds = bounds
                existingWindow.needsRepositioning = false
                updatedList.add(existingWindow)
            } else {
                // Add new window info
                updatedList.add(
                    WindowInfo(
                        packageName = packageName,
                        bounds = bounds,
                        windowId = window.id,
                        needsRepositioning = true
                    )
                )
            }
        }
        
        windowInfoList.clear()
        windowInfoList.addAll(updatedList)
    }
    
    private fun addAppToLayout(workspace: Workspace, appInfo: AppInfo) {
        // Find a suitable location for the app based on the workspace's layout
        val layout = workspace.layout
        val screenBounds = FreeformUtil.getScreenBounds(this)
        
        // Calculate the bounds for the new app based on the layout
        val bounds = calculateBounds(layout, screenBounds)
        
        // Launch the app in free form mode with the calculated bounds
        if (bounds != null) {
            FreeformUtil.launchAppInFreeform(this, appInfo.packageName, bounds)
        }
    }
    
    private fun calculateBounds(layout: TilingLayout, screenBounds: Rect): Rect? {
        return when (layout) {
            is SingleAppLayout -> {
                if (layout.appInfo == null) {
                    // This is an empty container, we can use it
                    Rect(screenBounds)
                } else {
                    // This container already has an app, can't use it
                    null
                }
            }
            is HorizontalSplitLayout -> {
                // Try left side first
                calculateBounds(layout.left, getLeftRegion(screenBounds, layout.ratio))
                    ?: calculateBounds(layout.right, getRightRegion(screenBounds, layout.ratio))
            }
            is VerticalSplitLayout -> {
                // Try top side first
                calculateBounds(layout.top, getTopRegion(screenBounds, layout.ratio))
                    ?: calculateBounds(layout.bottom, getBottomRegion(screenBounds, layout.ratio))
            }
        }
    }
    
    private fun getLeftRegion(rect: Rect, ratio: Float): Rect {
        val width = (rect.width() * ratio).toInt()
        return Rect(rect.left, rect.top, rect.left + width, rect.bottom)
    }
    
    private fun getRightRegion(rect: Rect, ratio: Float): Rect {
        val width = (rect.width() * ratio).toInt()
        return Rect(rect.left + width, rect.top, rect.right, rect.bottom)
    }
    
    private fun getTopRegion(rect: Rect, ratio: Float): Rect {
        val height = (rect.height() * ratio).toInt()
        return Rect(rect.left, rect.top, rect.right, rect.top + height)
    }
    
    private fun getBottomRegion(rect: Rect, ratio: Float): Rect {
        val height = (rect.height() * ratio).toInt()
        return Rect(rect.left, rect.top + height, rect.right, rect.bottom)
    }
    
    private fun applyTilingLayout() {
        // Apply the tiling layout to all windows in the current workspace
        val workspace = tilingConfig.workspaces.getOrNull(currentWorkspace) ?: return
        val layout = workspace.layout
        val screenBounds = FreeformUtil.getScreenBounds(this)
        
        // Map window info to layout
        val mappedWindows = mapWindowsToLayout(layout, windowInfoList, screenBounds)
        
        // Apply new bounds to windows
        for (windowMapping in mappedWindows) {
            val window = windowMapping.windowInfo
            val bounds = windowMapping.bounds
            
            if (window.bounds != bounds) {
                // The window needs repositioning
                Log.d(TAG, "Repositioning window: ${window.packageName}")
                
                // TODO: Implement window repositioning
                // This requires using the WindowManager API to resize freeform windows,
                // which is complex and requires additional permissions.
                // For a fully functional implementation, a solution would need to be developed
                // that works with different Android versions.
            }
        }
    }
    
    private fun mapWindowsToLayout(
        layout: TilingLayout,
        windows: List<WindowInfo>,
        bounds: Rect
    ): List<WindowMapping> {
        val result = mutableListOf<WindowMapping>()
        
        when (layout) {
            is SingleAppLayout -> {
                layout.appInfo?.let { appInfo ->
                    // Find the window for this app
                    val window = windows.find { it.packageName == appInfo.packageName }
                    if (window != null) {
                        result.add(WindowMapping(window, bounds))
                    }
                }
            }
            is HorizontalSplitLayout -> {
                val leftBounds = getLeftRegion(bounds, layout.ratio)
                val rightBounds = getRightRegion(bounds, layout.ratio)
                
                result.addAll(mapWindowsToLayout(layout.left, windows, leftBounds))
                result.addAll(mapWindowsToLayout(layout.right, windows, rightBounds))
            }
            is VerticalSplitLayout -> {
                val topBounds = getTopRegion(bounds, layout.ratio)
                val bottomBounds = getBottomRegion(bounds, layout.ratio)
                
                result.addAll(mapWindowsToLayout(layout.top, windows, topBounds))
                result.addAll(mapWindowsToLayout(layout.bottom, windows, bottomBounds))
            }
        }
        
        return result
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "TilingManagerService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TilingManagerService destroyed")
        
        isRunning = false
        serviceScope.cancel()
    }
    
    /**
     * Switch to a different workspace.
     */
    fun switchWorkspace(index: Int) {
        if (index < 0 || index >= tilingConfig.workspaces.size) {
            Log.e(TAG, "Invalid workspace index: $index")
            return
        }
        
        currentWorkspace = index
        
        // Apply the new workspace layout
        serviceScope.launch {
            applyTilingLayout()
        }
    }
    
    /**
     * Information about a window in the system.
     */
    data class WindowInfo(
        val packageName: String,
        var bounds: Rect,
        val windowId: Int,
        var needsRepositioning: Boolean = false
    )
    
    /**
     * Mapping between a window and its desired bounds.
     */
    data class WindowMapping(
        val windowInfo: WindowInfo,
        val bounds: Rect
    )
}
