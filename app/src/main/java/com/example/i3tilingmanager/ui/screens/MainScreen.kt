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
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.unit.dp


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

    // Define theme colors for consistent styling
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "i3 Tiling Manager Status",
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accessibility Service:",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAccessibilityServiceEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isAccessibilityServiceEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Freeform Mode:",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFreeformEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isFreeformEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    statusMessage?.let {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "Status: $it",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Action Buttons Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Force Apply Tiling
                    Button(
                        onClick = { viewModel.refreshLayout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "Force Apply Tiling",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Launch Settings
                    Button(
                        onClick = {
                            val intent = Intent()
                            intent.action = "LAUNCH_APP_IN_FREEFORM"
                            intent.putExtra("package_name", "com.android.settings")
                            context.sendBroadcast(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "Launch Settings in Freeform",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Debug Windows
                    Button(
                        onClick = {
                            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                            val services = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                            val service = services.find { service -> service.id.contains("com.example.i3tilingmanager") }

                            val debugInfo = if (service != null) {
                                val intent = Intent("DEBUG_WINDOWS")
                                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                                "Debug command sent to service"
                            } else {
                                "Service not found"
                            }

                            Toast.makeText(context, debugInfo, Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "Debug Windows Info",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Launch Calculator
                    Button(
                        onClick = {
                            val intent = Intent()
                            intent.action = "LAUNCH_APP_IN_FREEFORM"
                            intent.putExtra("package_name", "com.google.android.calculator")
                            context.sendBroadcast(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "Launch Calculator in Freeform",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Workspaces Card
            // Add to MainScreen.kt, inside your Card for workspaces
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Android Desktop (Display 2)",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Button(
                        onClick = {
                            // Refresh layout specifically for Display 2
                            val intent = Intent("FORCE_REFRESH_DISPLAY")
                            intent.putExtra("display_id", 2)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            Toast.makeText(context, "Refreshing Display 2 layout", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "Force Apply Tiling on Display 2",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(4) { i ->
                            Button(
                                onClick = {
                                    // Switch workspace specifically for Display 2
                                    CommandManager.switchWorkspace(context, i, 2)
                                    Toast.makeText(context, "Switched Display 2 to Workspace ${i+1}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor
                                )
                            ) {
                                Text(
                                    text = "D2 Workspace ${i+1}",
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}