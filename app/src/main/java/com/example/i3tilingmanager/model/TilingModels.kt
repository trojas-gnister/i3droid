package com.example.i3tilingmanager.model

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Represents information about an installed application.
 */
data class AppInfo(
    val packageName: String,
    val label: String? = null,
    val icon: androidx.compose.ui.graphics.ImageBitmap? = null
)

/**
 * Represents a workspace that contains a tiling layout configuration.
 */
data class Workspace(
    val id: Int,
    val name: String,
    val layout: TilingLayout,
    val runningApps: List<AppInfo> = emptyList()
)

/**
 * Base class for different tiling layout strategies.
 */
sealed class TilingLayout

/**
 * Represents a layout that displays a single application.
 */
data class SingleAppLayout(
    val appInfo: AppInfo? = null,
    val bounds: Rect? = null
) : TilingLayout()

/**
 * Represents a horizontal split layout with left and right containers.
 */
data class HorizontalSplitLayout(
    val left: TilingLayout,
    val right: TilingLayout,
    val ratio: Float = 0.5f
) : TilingLayout()

/**
 * Represents a vertical split layout with top and bottom containers.
 */
data class VerticalSplitLayout(
    val top: TilingLayout,
    val bottom: TilingLayout,
    val ratio: Float = 0.5f
) : TilingLayout()

/**
 * Main tiling configuration for managing multiple workspaces.
 */
data class TilingConfiguration(
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspace: Int = 0
)
