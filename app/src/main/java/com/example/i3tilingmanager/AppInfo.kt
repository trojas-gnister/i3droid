package com.i3droid

import android.graphics.drawable.Drawable

/**
 * Data class to hold information about installed applications.
 */
data class AppInfo(
    val label: String,           // App name/label
    val packageName: String,     // Package name (e.g., com.example.app)
    val activityName: String,    // Main activity name
    val icon: Drawable           // App icon
)