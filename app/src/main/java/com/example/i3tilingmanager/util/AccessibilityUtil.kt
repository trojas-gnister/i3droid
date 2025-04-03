package com.example.i3tilingmanager.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Utility class for accessibility service related operations.
 */
object AccessibilityUtil {

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
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedServiceName = serviceClass.name
        val colonSplitServices = enabledServices.split(":".toRegex()).dropLastWhile { it.isEmpty() }

        return colonSplitServices.any { serviceName ->
            serviceName.contains(expectedServiceName, ignoreCase = true)
        }
    }
}