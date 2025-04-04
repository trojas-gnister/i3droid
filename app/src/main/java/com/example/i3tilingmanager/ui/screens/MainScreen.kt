import com.example.i3tilingmanager.viewmodel.MainViewModel
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

            // If the service is running, notify it about the workspace change
            if (isAccessibilityServiceEnabled.value) {
                // This is a simplified approach - ideally we would use a bound service connection
                val intent = Intent(getApplication(), TilingManagerService::class.java)
                intent.action = "SWITCH_WORKSPACE"
                intent.putExtra("workspace_index", index)
                getApplication<Application>().startService(intent)

                _statusMessage.value = "Switched to workspace: ${tilingConfig.workspaces[index].name}"
            }
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