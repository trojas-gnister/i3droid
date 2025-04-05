package com.example.i3tilingmanager.service


import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS
import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.hardware.display.DisplayManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager


/**
 * Accessibility service that manages the tiling of windows in free form mode.
 * This service monitors window changes and applies tiling layouts based on the configuration.
 */
class TilingManagerService : AccessibilityService() {
    private val TAG = "TilingManagerService"

    private val workspacesByDisplay = mutableMapOf<Int, Int>().apply {
        // Default workspace 0 for all displays
        this[0] = 0  // Display 0 -> Workspace 0
        this[2] = 0  // Display 2 -> Workspace 0
    }
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
    private var launchAppReceiver: BroadcastReceiver? = null



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TilingManagerService created")
// Register in onCreate()
        val debugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "DEBUG_WINDOWS") {
                    val debugInfo = debugLogAllWindows()
                    // Show this info in a notification or send it back to the UI
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Create notification channel if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            "debug_channel",
                            "Debug Information",
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                        notificationManager.createNotificationChannel(channel)
                    }

                    val notification = NotificationCompat.Builder(this@TilingManagerService, "debug_channel")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Window Debug Info")
                        .setContentText("Total windows: ${windowInfoList.size}")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(debugInfo))
                        .build()

                    notificationManager.notify(1, notification)
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            debugReceiver,
            IntentFilter("DEBUG_WINDOWS")
        )
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

        // Register for app launch commands
        launchAppReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "LAUNCH_APP_IN_FREEFORM") {
                    val pkgName = intent.getStringExtra("package_name") ?: return
                    launchAppInFreeform(pkgName)
                }
            }
        }
        val intentFilter = IntentFilter("LAUNCH_APP_IN_FREEFORM")
        registerReceiver(launchAppReceiver, intentFilter, RECEIVER_NOT_EXPORTED)

        // Start monitoring windows
        isRunning = true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // **** ADDED LOG ****
        Log.i(TAG, "onServiceConnected: Service is now connected to the system.")

        // Initialize the service
        try { // Add try-catch for safety
            // **** ADDED LOG ****
            Log.d(TAG, "onServiceConnected: Launching initService coroutine...")
            serviceScope.launch {
                // **** ADDED LOG ****
                Log.i(TAG, "onServiceConnected: Coroutine launched for initService.")
                initService()
            }
        } catch (e: Exception) {
            // **** ADDED LOG ****
            Log.e(TAG, "onServiceConnected: Failed to launch initService coroutine", e)
        }
    }

    private suspend fun initService() {
        Log.i(TAG, "initService: Entered function.") // Existing log

        Log.d(TAG, "Initializing service state...")
        if (!FreeformUtil.isFreeformModeEnabled(this)) {
            Log.e(TAG, "Free form mode is not enabled, cannot initialize service properly.")
            isRunning = false
            return
        }
        Log.d(TAG, "initService: Freeform check passed.")

        currentWorkspace = tilingConfig.activeWorkspace
        Log.d(TAG, "initService: Set current workspace to $currentWorkspace")

        withContext(Dispatchers.Main) {
            Log.d(TAG, "initService: Scheduling initial layout application.")
            delay(1500)
            Log.i(TAG, "initService: Applying initial layout after delay.")

            // **** ADDED LOG ****
            Log.d(TAG, "initService: Calling updateWindowsList() before initial applyTilingLayout...")
            updateWindowsList() // Get current windows

            // **** ADDED LOG ****
            Log.d(TAG, "initService: Calling applyTilingLayout() for initial layout...")
            applyTilingLayout() // Apply layout
        }
        Log.i(TAG, "initService: Finished function.") // Existing log
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.v(TAG, "onAccessibilityEvent: FUNCTION CALLED.") // Existing log

        if (!isRunning) {
            Log.d(TAG, "onAccessibilityEvent: Ignoring event - isRunning is false.")
            return
        }
        if (event == null) {
            Log.d(TAG, "onAccessibilityEvent: Ignoring event - event is null.")
            return
        }

        val eventTypeString = AccessibilityEvent.eventTypeToString(event.eventType)
        val sourceClassName = event.source?.className?.toString() ?: "null"
        Log.d(
            TAG,
            "onAccessibilityEvent received: $eventTypeString, pkg: ${event.packageName}, source: $sourceClassName"
        ) // Existing log

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // **** ADDED LOG ****
                Log.v(TAG, "onAccessibilityEvent: Relevant event ($eventTypeString), scheduling window update.")
                scheduleWindowUpdate() // Existing call
            }
            else -> {
                Log.v(TAG, "onAccessibilityEvent: Ignoring event type $eventTypeString") // Existing log
            }
        }
        try {
            event.recycle()
        } catch (e: IllegalStateException) {
            // Ignore.
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Skip our own app windows
        if (packageName == "com.example.i3tilingmanager") return

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
    // Update the updateWindowsList() method in TilingManagerService.kt
    private fun updateWindowsList() {
        val windows = windows?.filterNotNull() ?: return

        // Add debug log to see all windows before filtering
        Log.d(TAG, "Found ${windows.size} total windows")

        // Update our window info list
        val updatedList = mutableListOf<WindowInfo>()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        Log.d(TAG, "Detected ${displays.size} displays")
        for (display in displays) {
            Log.d(TAG, "Display ${display.displayId}: ${display.name}")
        }

        // VERY IMPORTANT: Dump all window information for debugging
        Log.d(TAG, "=== ALL WINDOW INFO (PRE-FILTER) ===")
        windows.forEachIndexed { index, accessibilityWindowInfo ->
            val pkg = accessibilityWindowInfo.root?.packageName?.toString() ?: "null"
            val title = accessibilityWindowInfo.title?.toString() ?: "null"
            val displayId = accessibilityWindowInfo.displayId
            val bounds = Rect()
            accessibilityWindowInfo.getBoundsInScreen(bounds)
            Log.d(TAG, "Window[$index]: pkg=$pkg, title=$title, display=$displayId, bounds=$bounds")
        }

        // Only skip a very minimal set of system packages
        val systemPackagesToSkip = setOf(
            "com.android.systemui",
            "com.example.i3tilingmanager"
        )

        for (window in windows) {
            val packageName = window.root?.packageName?.toString() ?: continue
            val title = window.title?.toString() ?: "No Title"

            // More permissive filtering
            if (packageName in systemPackagesToSkip) {
                Log.d(TAG, "Skipping system window/own app: $packageName")
                continue
            }

            // Get the display ID for this window
            val displayId = window.displayId

            Log.d(TAG, "Processing window for management: $packageName (Title: $title, Display: $displayId)")

            val bounds = Rect()
            window.getBoundsInScreen(bounds)

            // Check if this window is already in our list
            val existingWindow = windowInfoList.find { it.packageName == packageName }

            if (existingWindow != null) {
                // Update existing window info
                val needsRepositioning = existingWindow.bounds != bounds
                existingWindow.bounds = bounds
                existingWindow.needsRepositioning = needsRepositioning
                existingWindow.displayId = displayId
                existingWindow.title = title
                updatedList.add(existingWindow)
                Log.d(TAG, "Updated window: $packageName, needs repositioning: $needsRepositioning")
            } else {
                // Add new window info
                updatedList.add(
                    WindowInfo(
                        packageName = packageName,
                        bounds = bounds,
                        windowId = window.id,
                        displayId = displayId,
                        needsRepositioning = true,
                        title = title
                    )
                )
                Log.d(TAG, "Added new window: $packageName")
            }
        }

        windowInfoList.clear()
        windowInfoList.addAll(updatedList)
        Log.d(TAG, "Window list updated, total managed windows: ${windowInfoList.size}")
    }

    private suspend fun updateWindowsAndApplyLayout() {
        // **** ADDED LOG ****
        Log.i(TAG, "updateWindowsAndApplyLayout: Entered function.") // Log entry

        Log.d(TAG, "updateWindowsAndApplyLayout: Starting...") // Existing log
        updateWindowsList()

        val now = System.currentTimeMillis()
        // **** ADDED LOG ****
        Log.d(TAG, "updateWindowsAndApplyLayout: Checking if layout needed. Time since last: ${now - lastLayoutApplication}ms, Needs Repo: ${windowInfoList.any { it.needsRepositioning }}")
        if (now - lastLayoutApplication > MIN_LAYOUT_INTERVAL &&
            windowInfoList.any { it.needsRepositioning }) {
            Log.i(TAG, "updateWindowsAndApplyLayout: Needs repositioning, applying layout.") // Existing log
            withContext(Dispatchers.Main) {
                // **** ADDED LOG ****
                Log.d(TAG, "updateWindowsAndApplyLayout: Calling applyTilingLayout from debounced update.")
                applyTilingLayout()
            }
        } else {
            Log.d(TAG, "updateWindowsAndApplyLayout: No repositioning needed or too soon.") // Existing log
        }

        withContext(Dispatchers.Main) {
            _activeWindows.value = windowInfoList.toList()
        }
        Log.d(TAG, "updateWindowsAndApplyLayout: Finished.") // Existing log
    }

    private val windowUpdateRunnable = Runnable {
        // **** ADDED LOG ****
        Log.i(TAG, "windowUpdateRunnable: EXECUTING runnable.") // Log execution start

        Log.d(TAG, "Executing debounced/throttled window update.") // Existing log
        try { // Add try-catch for safety
            serviceScope.launch {
                // **** ADDED LOG ****
                Log.i(TAG, "windowUpdateRunnable: Coroutine launched for updateWindowsAndApplyLayout.")
                updateWindowsAndApplyLayout()
            }
        } catch (e: Exception) {
            Log.e(TAG, "windowUpdateRunnable: Failed to launch update coroutine", e)
        }
    }

    private fun scheduleWindowUpdate() {
        // **** ADDED LOG ****
        Log.d(TAG, "scheduleWindowUpdate: Posting runnable with delay ${WINDOW_UPDATE_DELAY_MS}ms")
        mainHandler.removeCallbacks(windowUpdateRunnable) // Remove pending updates
        mainHandler.postDelayed(windowUpdateRunnable, WINDOW_UPDATE_DELAY_MS) // Schedule new one
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

    /**
     * Launch an app in freeform mode for testing
     */
    fun launchAppInFreeform(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            // Important: Set the right flags for freeform mode
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

            Log.d(TAG, "Launching app in freeform: $packageName")

            // On Android 10+, we can specify bounds directly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val screenBounds = FreeformUtil.getScreenBounds(this)
                val options = ActivityOptions.makeBasic()
                options.launchBounds = Rect(0, 100, screenBounds.width() / 2, screenBounds.height() / 2)
                startActivity(intent, options.toBundle())
                Log.d(TAG, "Launched $packageName in freeform with bounds")
            } else {
                startActivity(intent)
                Log.d(TAG, "Launched $packageName without bounds (older Android)")
            }
        } else {
            Log.e(TAG, "Could not find launch intent for package: $packageName")
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

    // Add this method to TilingManagerService.kt
    fun refreshAllWindows() {
        serviceScope.launch {
            Log.d(TAG, "Manually refreshing all windows and layouts")

            // Update windows list
            updateWindowsList()

            // Force reapplication of layout
            withContext(Dispatchers.Main) {
                applyTilingLayout(forceUpdate = true)
            }

            // Update UI with new window list
            withContext(Dispatchers.Main) {
                _activeWindows.value = windowInfoList.toList()
            }
        }
    }

    private fun applyTilingLayout(forceUpdate: Boolean = false, displayId: Int = 0) {
        Log.i(TAG, "<<< applyTilingLayout: Entered function for workspace $currentWorkspace on display $displayId >>>")

        lastLayoutApplication = System.currentTimeMillis()

        val workspaceIndex = workspacesByDisplay[displayId] ?: 0

        Log.i(TAG, "<<< applyTilingLayout: Entered function for workspace $workspaceIndex on display $displayId >>>")

        lastLayoutApplication = System.currentTimeMillis()

        Log.d(TAG, "applyTilingLayout: Getting workspace config...")
        val workspace = tilingConfig.workspaces.getOrNull(workspaceIndex)
        if (workspace == null) {
            Log.e(TAG, "applyTilingLayout: Cannot apply layout, invalid workspace index: $workspaceIndex")
            return
        }
        val layout = workspace.layout

        Log.d(TAG, "applyTilingLayout: Getting screen bounds...")
        val screenBounds = FreeformUtil.getScreenBounds(this, displayId, forceRefresh = true)
        if (screenBounds.isEmpty) {
            Log.e(TAG, "applyTilingLayout: Cannot apply layout, screen bounds are empty.")
            return
        }
        Log.d(TAG, "applyTilingLayout: Screen bounds for layout: $screenBounds")

        Log.d(TAG, "applyTilingLayout: Getting window list snapshot...")
        // Filter windows for the current display
        val currentWindows = windowInfoList.filter { it.displayId == displayId }

        if (currentWindows.isEmpty()) {
            Log.d(TAG, "applyTilingLayout: No windows found for display $displayId to apply layout to.")
            // Try to launch default app for this workspace if configured
            tryLaunchDefaultApps(workspace, screenBounds, displayId)
            packageToLayoutMap.clear()
            return
        }

        Log.d(TAG, "applyTilingLayout: Mapping windows to layout...")
        val mappedWindows = mapWindowsToLayout(layout, currentWindows, screenBounds)
        Log.d(TAG, "applyTilingLayout: Mapped ${mappedWindows.size} windows to layout bounds.")
        if (mappedWindows.isEmpty()) {
            Log.w(TAG, "applyTilingLayout: Window list was not empty, but mapping resulted in zero windows.")
            return
        }

        val app = I3TilingManagerApplication.getInstance()
        val windowGap = app.appSettings.windowGap.value
        Log.d(TAG, "applyTilingLayout: Using window gap: $windowGap")

        // Only clear the map if we're doing a full refresh
        if (forceUpdate) {
            packageToLayoutMap.clear()
        }

        Log.d(TAG, "applyTilingLayout: Starting loop to resize windows...")
        for (windowMapping in mappedWindows) {
            val window = windowMapping.windowInfo
            var targetBounds = Rect(windowMapping.bounds)

            if (windowGap > 0 && targetBounds.width() > 2 * windowGap && targetBounds.height() > 2 * windowGap) {
                targetBounds.inset(windowGap, windowGap)
            }

            packageToLayoutMap[window.packageName] = targetBounds

            // Only reposition if bounds changed or forcing update
            if (forceUpdate || window.bounds != targetBounds) {
                Log.i(TAG, "Repositioning window: ${window.packageName} (ID: ${window.windowId}) from ${window.bounds} to $targetBounds")
                val resizeSuccess = FreeformUtil.resizeWindow(this, window.windowId, targetBounds)

                val windowInMainList = windowInfoList.find { it.windowId == window.windowId }
                if (resizeSuccess) {
                    windowInMainList?.needsRepositioning = false
                    window.bounds = targetBounds  // Update the bounds in memory
                } else {
                    Log.e(TAG, "Failed to resize window: ${window.packageName} (ID: ${window.windowId})")
                    windowInMainList?.needsRepositioning = true
                }
            } else {
                Log.v(TAG, "Window ${window.packageName} (ID: ${window.windowId}) already at target bounds $targetBounds.")
                windowInfoList.find { it.windowId == window.windowId }?.needsRepositioning = false
            }
        }
        Log.i(TAG, "<<< applyTilingLayout: Finished function. >>>")
    }

    // Helper method to launch default apps for a workspace
    private fun tryLaunchDefaultApps(workspace: Workspace, screenBounds: Rect, displayId: Int) {
        // Example implementation - you'd need to configure default apps per workspace in your model
        val defaultApps = when (workspace.id) {
            0 -> listOf("com.android.settings", "com.google.android.calculator")
            1 -> listOf("com.google.android.apps.docs", "com.google.android.calendar")
            2 -> listOf("com.google.android.apps.messaging", "com.google.android.gm")
            else -> emptyList()
        }

        if (defaultApps.isNotEmpty()) {
            Log.d(TAG, "Trying to launch default apps for workspace ${workspace.name}: $defaultApps")
            for (app in defaultApps) {
                // Calculate position based on index and total
                val index = defaultApps.indexOf(app)
                val total = defaultApps.size
                val appBounds = calculateDefaultBounds(screenBounds, index, total)
                FreeformUtil.launchAppInFreeform(this, app, appBounds)
            }
        }
    }

    private fun calculateDefaultBounds(screenBounds: Rect, index: Int, total: Int): Rect {
        // Simple implementation for 1 or 2 apps
        return when {
            total == 1 -> Rect(screenBounds) // Full screen
            total == 2 && index == 0 -> Rect(
                screenBounds.left, screenBounds.top,
                screenBounds.left + screenBounds.width() / 2, screenBounds.bottom
            ) // Left half
            total == 2 && index == 1 -> Rect(
                screenBounds.left + screenBounds.width() / 2, screenBounds.top,
                screenBounds.right, screenBounds.bottom
            ) // Right half
            else -> Rect(screenBounds) // Default to full screen
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

    // Add to TilingManagerService.kt
    private fun fixWindowFocus() {
        try {
            // Try to clear focus first
            performGlobalAction(GLOBAL_ACTION_HOME)
            Thread.sleep(100) // Small delay

            // Now focus the window we want
            windows?.forEach { windowInfo ->
                if (windowInfo.isFocused && windowInfo.root?.packageName.toString() != "com.example.i3tilingmanager") {
                    windowInfo.root?.refresh()
                    windowInfo.root?.performAction(ACTION_FOCUS)
                    return@forEach
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fixing window focus: ${e.message}")
        }
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

        // Unregister all receivers
        if (workspaceSwitchReceiver != null) {
            CommandManager.unregisterReceiver(this, workspaceSwitchReceiver!!)
        }

        if (layoutRefreshReceiver != null) {
            CommandManager.unregisterReceiver(this, layoutRefreshReceiver!!)
        }

        if (windowActionReceiver != null) {
            CommandManager.unregisterReceiver(this, windowActionReceiver!!)
        }

        if (launchAppReceiver != null) {
            unregisterReceiver(launchAppReceiver)
        }

        isRunning = false
        serviceScope.cancel()
    }

    /**
     * Switch to a different workspace.
     */
    fun switchWorkspace(index: Int, displayId: Int = 0) {
        if (index < 0 || index >= tilingConfig.workspaces.size) {
            Log.e(TAG, "Invalid workspace index: $index")
            return
        }

        Log.d(TAG, "Switching to workspace: $index for display: $displayId")
        workspacesByDisplay[displayId] = index

        // Apply the new workspace layout for the specific display
        serviceScope.launch {
            // Clear the package-to-layout map for the new workspace on this display
            val displaySpecificPackages = packageToLayoutMap.filterKeys {
                windowInfoList.find { info -> info.packageName == it }?.displayId == displayId
            }.keys

            for (pkg in displaySpecificPackages) {
                packageToLayoutMap.remove(pkg)
            }

            // Apply the tiling layout for the new workspace
            withContext(Dispatchers.Main) {
                applyTilingLayout(forceUpdate = true, displayId = displayId)
            }
        }
    }

    fun debugLogAllWindows(): String {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val sb = StringBuilder()

        sb.appendLine("=== Window Debug Information ===")
        sb.appendLine("Total managed windows: ${windowInfoList.size}")

        // Group windows by display
        val windowsByDisplay = windowInfoList.groupBy { it.displayId }

        for (display in displayManager.displays) {
            sb.appendLine("\nDisplay ${display.displayId}: ${display.name}")
            sb.appendLine("Resolution: ${display.width}x${display.height}")

            val windowsOnDisplay = windowsByDisplay[display.displayId] ?: emptyList()
            if (windowsOnDisplay.isEmpty()) {
                sb.appendLine("No managed windows on this display")
            } else {
                sb.appendLine("Windows on this display:")
                for (window in windowsOnDisplay) {
                    sb.appendLine("- ${window.packageName} (${window.bounds})")
                }
            }
        }

        val logOutput = sb.toString()
        Log.d(TAG, logOutput)
        return logOutput
    }

    // Add this method to TilingManagerService.kt
    fun forceRefreshAllDisplays() {
        serviceScope.launch {
            Log.d(TAG, "Forcing refresh of all displays")

            // Clear all window info
            windowInfoList.clear()

            // Update windows list with less strict filtering
            val windows = windows?.filterNotNull() ?: return@launch

            Log.d(TAG, "Found ${windows.size} total windows")

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            Log.d(TAG, "Detected ${displays.size} displays")
            for (display in displays) {
                Log.d(TAG, "Display ${display.displayId}: ${display.name}")

                // For each display, try to apply a layout
                applyTilingLayout(forceUpdate = true, displayId = display.displayId)
            }
        }
    }

    // Register a receiver for the force refresh command
    val forceRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "FORCE_REFRESH_DISPLAY") {
                val displayId = intent.getIntExtra("display_id", -1)
                if (displayId >= 0) {
                    Log.d(TAG, "Force refreshing display: $displayId")
                    serviceScope.launch {
                        applyTilingLayout(forceUpdate = true, displayId = displayId)
                    }
                } else {
                    forceRefreshAllDisplays()
                }
            }
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(
    forceRefreshReceiver,
    IntentFilter("FORCE_REFRESH_DISPLAY")
    )

    /**
     * Information about a window in the system.
     */
    data class WindowInfo(
        val packageName: String,
        var bounds: Rect,
        val windowId: Int,
        var displayId: Int = 0,
        var needsRepositioning: Boolean = false,
        var title: String = ""
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
        const val WINDOW_UPDATE_DELAY_MS = 500L // Delay in milliseconds for window updates
        const val DEBUG_WINDOW_DETECTION = true // Flag for extra logging
    }
}