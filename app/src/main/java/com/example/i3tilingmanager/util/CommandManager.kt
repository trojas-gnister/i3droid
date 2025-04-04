package com.example.i3tilingmanager.util

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Utility class for sending commands between the app components.
 */
object CommandManager {
    private const val TAG = "CommandManager"

    // Command action constants
    const val ACTION_SWITCH_WORKSPACE = "com.example.i3tilingmanager.SWITCH_WORKSPACE"
    const val ACTION_REFRESH_LAYOUT = "com.example.i3tilingmanager.REFRESH_LAYOUT"
    const val ACTION_WINDOW_ACTION = "com.example.i3tilingmanager.WINDOW_ACTION"

    // Extra keys
    const val EXTRA_WORKSPACE_INDEX = "workspace_index"
    const val EXTRA_WINDOW_PACKAGE = "window_package"
    const val EXTRA_WINDOW_ACTION = "window_action"

    // Window action types
    const val WINDOW_ACTION_MAXIMIZE = "maximize"
    const val WINDOW_ACTION_CLOSE = "close"
    const val WINDOW_ACTION_FOCUS = "focus"

    /**
     * Send a command to switch to a different workspace.
     */
    fun switchWorkspace(context: Context, workspaceIndex: Int) {
        val intent = Intent(ACTION_SWITCH_WORKSPACE)
        intent.putExtra(EXTRA_WORKSPACE_INDEX, workspaceIndex)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

        Log.d(TAG, "Broadcasting workspace switch command: $workspaceIndex")
    }

    /**
     * Send a command to refresh the current layout.
     */
    fun refreshLayout(context: Context) {
        val intent = Intent(ACTION_REFRESH_LAYOUT)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

        Log.d(TAG, "Broadcasting layout refresh command")
    }

    /**
     * Send a command to perform an action on a window.
     */
    fun windowAction(context: Context, packageName: String, action: String) {
        val intent = Intent(ACTION_WINDOW_ACTION)
        intent.putExtra(EXTRA_WINDOW_PACKAGE, packageName)
        intent.putExtra(EXTRA_WINDOW_ACTION, action)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

        Log.d(TAG, "Broadcasting window action: $action for package: $packageName")
    }

    /**
     * Register a receiver for workspace switch commands.
     */
    fun registerForWorkspaceSwitchCommands(
        context: Context,
        callback: (Int) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_SWITCH_WORKSPACE) {
                    val index = intent.getIntExtra(EXTRA_WORKSPACE_INDEX, 0)
                    callback(index)
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(ACTION_SWITCH_WORKSPACE)
        )

        return receiver
    }

    /**
     * Register a receiver for layout refresh commands.
     */
    fun registerForLayoutRefreshCommands(
        context: Context,
        callback: () -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_REFRESH_LAYOUT) {
                    callback()
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(ACTION_REFRESH_LAYOUT)
        )

        return receiver
    }

    /**
     * Register a receiver for window action commands.
     */
    fun registerForWindowActionCommands(
        context: Context,
        callback: (String, String) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_WINDOW_ACTION) {
                    val packageName = intent.getStringExtra(EXTRA_WINDOW_PACKAGE) ?: return
                    val action = intent.getStringExtra(EXTRA_WINDOW_ACTION) ?: return
                    callback(packageName, action)
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(ACTION_WINDOW_ACTION)
        )

        return receiver
    }

    /**
     * Unregister a broadcast receiver.
     */
    fun unregisterReceiver(
        context: Context,
        receiver: BroadcastReceiver
    ) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
    }
}