package com.i3droid

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings activity for the i3droid launcher
 * Allows users to customize window size and behavior
 */
class LauncherSettings : AppCompatActivity() {

    companion object {
        // SharedPreferences keys
        const val PREFS_NAME = "i3droid_settings"
        const val KEY_WINDOW_SIZE = "window_size"
        const val KEY_WINDOW_MARGIN = "window_margin"

        // Default values
        const val DEFAULT_WINDOW_SIZE = 50 // percentage
        const val DEFAULT_WINDOW_MARGIN = 50 // in dp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load current settings
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentWindowSize = prefs.getInt(KEY_WINDOW_SIZE, DEFAULT_WINDOW_SIZE)
        val currentWindowMargin = prefs.getInt(KEY_WINDOW_MARGIN, DEFAULT_WINDOW_MARGIN)

        // Create settings layout programmatically for a minimal look
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(getColor(R.color.colorPrimary))
        }

        // Title
        val titleText = TextView(this).apply {
            text = getString(R.string.settings)
            textSize = 24f
            setTextColor(getColor(R.color.launcher_text))
            setPadding(0, 0, 0, 48)
        }
        rootLayout.addView(titleText)

        // Window size settings
        val windowSizeText = TextView(this).apply {
            text = "Default Window Size"
            textSize = 18f
            setTextColor(getColor(R.color.launcher_text))
            setPadding(0, 16, 0, 8)
        }
        rootLayout.addView(windowSizeText)

        val windowSizeValueText = TextView(this).apply {
            text = "$currentWindowSize%"
            textSize = 14f
            setTextColor(getColor(R.color.launcher_text))
        }
        rootLayout.addView(windowSizeValueText)

        val windowSizeSeekBar = SeekBar(this).apply {
            max = 100
            progress = currentWindowSize
            setPadding(0, 8, 0, 24)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    windowSizeValueText.text = "$progress%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        rootLayout.addView(windowSizeSeekBar)

        // Window margin settings
        val windowMarginText = TextView(this).apply {
            text = "Window Cascade Margin"
            textSize = 18f
            setTextColor(getColor(R.color.launcher_text))
            setPadding(0, 16, 0, 8)
        }
        rootLayout.addView(windowMarginText)

        val windowMarginValueText = TextView(this).apply {
            text = "$currentWindowMargin dp"
            textSize = 14f
            setTextColor(getColor(R.color.launcher_text))
        }
        rootLayout.addView(windowMarginValueText)

        val windowMarginSeekBar = SeekBar(this).apply {
            max = 100
            progress = currentWindowMargin
            setPadding(0, 8, 0, 24)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    windowMarginValueText.text = "$progress dp"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        rootLayout.addView(windowMarginSeekBar)

        // Save button
        val saveButton = Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                // Save settings to SharedPreferences
                prefs.edit().apply {
                    putInt(KEY_WINDOW_SIZE, windowSizeSeekBar.progress)
                    putInt(KEY_WINDOW_MARGIN, windowMarginSeekBar.progress)
                    apply()
                }
                finish()
            }
        }
        rootLayout.addView(saveButton)

        setContentView(rootLayout)
    }
}