package com.i3droid

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Main Activity that serves as the launcher's home screen.
 * Handles gestures and key combinations to launch the app menu.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appLauncher: AppLauncher
    private lateinit var rootLayout: LinearLayout
    private lateinit var freeformWindowManager: FreeformWindowManager

    // Gesture detection variables
    private var startY: Float = 0f
    private val SWIPE_THRESHOLD = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root_layout)

        // Initialize the app launcher and freeform window manager
        appLauncher = AppLauncher(this)
        freeformWindowManager = FreeformWindowManager(this)

        // Set up touch listener to handle gestures
        rootLayout.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide system UI for a more immersive experience
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Store initial touch position
                startY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                // If there was a swipe up from the bottom, show the app launcher
                val deltaY = startY - event.y
                if (deltaY > SWIPE_THRESHOLD && startY > rootLayout.height - 200) {
                    showAppLauncher()
                    return true
                }
            }
        }
        return false
    }

    private fun showAppLauncher() {
        lifecycleScope.launch {
            appLauncher.show { appInfo ->
                // Launch the selected app in freeform mode
                freeformWindowManager.launchAppInFreeformMode(appInfo.packageName, appInfo.activityName)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Listen for Alt+Space key combination to show app launcher (like dmenu)
        if (keyCode == KeyEvent.KEYCODE_SPACE && event?.isAltPressed == true) {
            showAppLauncher()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        // Prevent back button from exiting the launcher
        showAppLauncher()
    }
}