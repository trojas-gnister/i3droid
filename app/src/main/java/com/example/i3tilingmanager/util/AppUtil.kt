package com.example.i3tilingmanager.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.i3tilingmanager.model.AppInfo

/**
 * Utility class for application related operations.
 */
object AppUtil {

    /**
     * Get information about an installed application.
     *
     * @param packageManager The package manager to use.
     * @param packageName The package name of the application.
     * @return AppInfo object or null if the application is not found.
     */
    fun getAppInfo(packageManager: PackageManager, packageName: String): AppInfo? {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(applicationInfo).toString()
            val icon = drawableToBitmap(packageManager.getApplicationIcon(packageName))?.asImageBitmap()

            AppInfo(packageName, label, icon)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get a list of all installed applications.
     *
     * @param packageManager The package manager to use.
     * @return List of AppInfo objects.
     */
    fun getInstalledApps(packageManager: PackageManager): List<AppInfo> {
        val installedApps = mutableListOf<AppInfo>()

        val flags = PackageManager.GET_META_DATA
        val apps = packageManager.getInstalledApplications(flags)

        for (appInfo in apps) {
            // Skip system apps
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                continue
            }

            // Make sure the app has a launcher
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent == null) {
                continue
            }

            val label = packageManager.getApplicationLabel(appInfo).toString()
            val icon = drawableToBitmap(packageManager.getApplicationIcon(appInfo.packageName))?.asImageBitmap()

            installedApps.add(AppInfo(appInfo.packageName, label, icon))
        }

        return installedApps.sortedBy { it.label }
    }

    /**
     * Convert a drawable to a bitmap.
     *
     * @param drawable The drawable to convert.
     * @return The converted bitmap or null if the conversion failed.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}