package com.example.i3tilingmanager.service


import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.model.*
import com.example.i3tilingmanager.util.AppUtil
import com.example.i3tilingmanager.util.CommandManager
import com.example.i3tilingmanager.util.FreeformUtil
import kotlinx.coroutines.*

/**
 * Accessibility service that manages the tiling of windows in free form mode.
 * This service monitors window changes and applies tiling layouts based on the configuration.
 */
class TilingManagerService : AccessibilityService() {
    private val TAG = "TilingManagerService"

    // Use Dispatchers.IO for background operations
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracking windows and their info
    private val windowInfoList = mutableListOf<WindowInfo>()
    private val _activeWindows = MutableLiveData<List<WindowInfo>>(emptyList())
    val activeWindows: LiveData<List<WindowInfo>> = _activeWindows

    // Track window layout application timing to avoid excessive operations
    private var lastLayoutApplication = 0L
    private val MIN_LAYOUT_INTERVAL = 500L  // ms

    // Track whether the service is running
    private var isRunning = false

    // Current tiling configuration
    private lateinit var tilingConfig: TilingConfiguration

    // The current workspace index
    private var currentWorkspace = 0

    // Map of package names to assigned layouts
    private val packageToLayoutMap = mutableMapOf<String, Rect>()

    // Broadcast receivers
    private var workspaceSwitchReceiver: BroadcastReceiver? = null
    private var layoutRefreshReceiver: BroadcastReceiver? = null
    private var windowActionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TilingManagerService created")

        // Get application instance and config
        val app = I3TilingManagerApplication.getInstance()
        tilingConfig = app.tilingConfiguration.value

        // Register for workspace switch commands
        workspaceSwitchReceiver = CommandManager.registerForWorkspaceSwitchCommands(this) { index ->
            switchWorkspace(index)
        }

        // Register for layout refresh commands
        layoutRefreshReceiver = CommandManager.registerForLayoutRefreshCommands(this) {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    applyTilingLayout()
                }
            }
        }

        // Register for window action commands
        windowActionReceiver = CommandManager.registerForWindowActionCommands(this) { packageName, action ->
            handleWindowAction(packageName, action)
        }

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
        withContext(Dispatchers.Main) {
            // Initial delay to allow system to stabilize
            delay(1000)
            updateWindowsList()
            applyTilingLayout()
        }
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
        val packageName = event.packageName?.toString() ?: return

        // Skip our own app windows
        if (packageName == packageName) return

        // Use IO dispatcher for background processing
        serviceScope.launch(Dispatchers.IO) {
            updateWindowsList()

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
            withContext(Dispatchers.Main) {
                _activeWindows.value = windowInfoList.toList()

                // Apply layout with a small delay to let the window stabilize
                mainHandler.postDelayed({
                    serviceScope.launch {
                        applyTilingLayout()
                    }
                }, 300)
            }
        }
    }

    private fun handleWindowsChanged() {
        // Use IO dispatcher for background processing
        serviceScope.launch(Dispatchers.IO) {
            updateWindowsList()

            // Only reapply layout if enough time has passed since last update
            val now = System.currentTimeMillis()
            if (now - lastLayoutApplication > MIN_LAYOUT_INTERVAL &&
                windowInfoList.any { it.needsRepositioning }) {

                withContext(Dispatchers.Main) {
                    applyTilingLayout()
                }
            }

            // Update active windows
            withContext(Dispatchers.Main) {
                _activeWindows.value = windowInfoList.toList()
            }
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
            if (packageName == "android" || packageName == packageName ||
                packageName == "com.android.systemui") {
                continue
            }

            val bounds = Rect()
            window.getBoundsInScreen(bounds)

            // Check if this window is already in our list
            val existingWindow = windowInfoList.find { it.packageName == packageName }

            if (existingWindow != null) {
                // Update existing window info
                val needsRepositioning = existingWindow.bounds != bounds
                existingWindow.bounds = bounds
                existingWindow.needsRepositioning = needsRepositioning
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
            packageToLayoutMap[appInfo.packageName] = bounds
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
        // Record the time to prevent excessive layout applications
        lastLayoutApplication = System.currentTimeMillis()

        // Apply the tiling layout to all windows in the current workspace
        val workspace = tilingConfig.workspaces.getOrNull(currentWorkspace) ?: return
        val layout = workspace.layout
        val screenBounds = FreeformUtil.getScreenBounds(this)

        // Map window info to layout
        val mappedWindows = mapWindowsToLayout(layout, windowInfoList, screenBounds)

        // Apply window gap if configured
        val app = I3TilingManagerApplication.getInstance()
        val windowGap = app.appSettings.windowGap.value

        // Apply new bounds to windows
        for (windowMapping in mappedWindows) {
            val window = windowMapping.windowInfo
            var bounds = windowMapping.bounds

            // Apply window gap if enabled
            if (windowGap > 0) {
                bounds = Rect(
                    bounds.left + windowGap,
                    bounds.top + windowGap,
                    bounds.right - windowGap,
                    bounds.bottom - windowGap
                )
            }

            if (window.bounds != bounds) {
                // The window needs repositioning
                Log.d(TAG, "Repositioning window: ${window.packageName} to $bounds")

                // Store the expected bounds for this package
                packageToLayoutMap[window.packageName] = bounds

                // Reposition the window using the FreeformUtil
                FreeformUtil.resizeWindow(this, window.windowId, bounds)

                // Mark this window as properly positioned
                window.needsRepositioning = false
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
                } ?: run {
                    // If there's no assigned app but there's only one window, use it
                    if (windows.size == 1) {
                        result.add(WindowMapping(windows.first(), bounds))
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

    private fun handleWindowAction(packageName: String, action: String) {
        val window = windowInfoList.find { it.packageName == packageName } ?: return

        when (action) {
            CommandManager.WINDOW_ACTION_MAXIMIZE -> {
                // Maximize the window to full screen
                val screenBounds = FreeformUtil.getScreenBounds(this)
                FreeformUtil.resizeWindow(this, window.windowId, screenBounds)
            }
            CommandManager.WINDOW_ACTION_CLOSE -> {
                // Try to close the window - this may require additional permissions
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to close window: ${e.message}")
                }
            }
            CommandManager.WINDOW_ACTION_FOCUS -> {
                // Try to focus on this window
                try {
                    windows?.forEach { windowInfo ->
                        if (windowInfo.root?.packageName == packageName) {
                            windowInfo.root?.refresh()
                            windowInfo.root?.performAction(ACTION_FOCUS)
                            return@forEach
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to focus window: ${e.message}")
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "TilingManagerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TilingManagerService destroyed")

        // In the onCreate method, change these lines
        workspaceSwitchReceiver = CommandManager.registerForWorkspaceSwitchCommands(this) { index ->
            switchWorkspace(index)
        }

        layoutRefreshReceiver = CommandManager.registerForLayoutRefreshCommands(this) {
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    applyTilingLayout()
                }
            }
        }

        windowActionReceiver = CommandManager.registerForWindowActionCommands(this) { packageName, action ->
            handleWindowAction(packageName, action)
        }

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

        Log.d(TAG, "Switching to workspace: $index")
        currentWorkspace = index

        // Apply the new workspace layout
        serviceScope.launch {
            // Clear the package-to-layout map for the new workspace
            packageToLayoutMap.clear()

            // Apply the tiling layout for the new workspace
            withContext(Dispatchers.Main) {
                applyTilingLayout()
            }
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

    companion object {
        private val AccessibilityNodeInfo = android.view.accessibility.AccessibilityNodeInfo::class.java
    }
}