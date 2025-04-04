package com.example.i3tilingmanager.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Enhanced utility class for accessibility service related operations.
 */
object AccessibilityUtil {
    private const val TAG = "AccessibilityUtil"

    /**
     * Check if the accessibility service is enabled for a specific service class.
     *
     * @param context The context to use.
     * @param serviceClass The service class to check.
     * @return True if the service is enabled, false otherwise.
     */
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expectedServiceName = context.packageName + "/" + serviceClass.name

        // Method 1: Check via Settings.Secure
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        if (enabledServices.contains(expectedServiceName)) {
            return true
        }

        // Method 2: Check via AccessibilityManager
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val enabledServiceList = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        for (service in enabledServiceList) {
            val serviceId = service.id
            if (serviceId.contains(serviceClass.simpleName, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * Launch accessibility settings to enable the service.
     *
     * @param context The context to use.
     */
    fun launchAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch accessibility settings: ${e.message}")
        }
    }

    /**
     * Check if the accessibility service has the required permissions.
     *
     * @param service The accessibility service instance.
     * @return True if the service has all required permissions, false otherwise.
     */
    fun hasRequiredPermissions(service: AccessibilityService): Boolean {
        val serviceInfo = service.serviceInfo ?: return false

        // Check for window content access permission
        if (!serviceInfo.canRetrieveWindowContent) {
            return false
        }

        // Check other required capabilities
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!serviceInfo.capabilities.hasFlag(AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)) {
                return false
            }
        }

        return true
    }

    /**
     * Extension function to check if a bitmask contains a specific flag.
     */
    private fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
}

/**
 * Extension class containing additional Android SDK related utilities.
 */
private object Build {
    object VERSION_CODES {
        const val O = 26  // Android O API level
    }

    object VERSION {
        val SDK_INT = android.os.Build.VERSION.SDK_INT  // Use val not const
    }
}