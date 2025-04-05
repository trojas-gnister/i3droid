package com.i3droid

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/**
 * A dmenu-like application launcher that displays a search field
 * and a list of installed applications.
 */
class AppLauncher(private val context: Context) {

    private var dialog: Dialog? = null
    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private var appList = mutableListOf<AppInfo>()
    private var filteredAppList = mutableListOf<AppInfo>()

    /**
     * Shows the app launcher dialog
     * @param onAppSelected Callback function when an app is selected
     */
    fun show(onAppSelected: (AppInfo) -> Unit) {
        // Load app list if not already loaded
        if (appList.isEmpty()) {
            loadAppList()
        }

        // Filter app list based on current search text
        filteredAppList = appList.toMutableList()

        // Create and show the dialog
        dialog = Dialog(context, R.style.AppLauncherTheme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(createDialogLayout(onAppSelected))
            window?.apply {
                setGravity(Gravity.TOP)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
            show()
        }

        // Focus on search field
        searchEditText.requestFocus()
    }

    /**
     * Creates the dialog layout with search field and app list
     */
    private fun createDialogLayout(onAppSelected: (AppInfo) -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#222222"))

            // Create search field
            searchEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = "Search apps..."
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                typeface = Typeface.MONOSPACE
                background = context.getDrawable(R.drawable.app_item_background)
                setPadding(16, 16, 16, 16)

                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        filterAppList(s.toString())
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            addView(searchEditText)

            // Create RecyclerView for app list
            recyclerView = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    // Limit height to show max ~5 items
                    height = 250
                }
                layoutManager = LinearLayoutManager(context)

                appListAdapter = AppListAdapter(filteredAppList) { appInfo ->
                    onAppSelected(appInfo)
                    dialog?.dismiss()
                }
                adapter = appListAdapter
            }
            addView(recyclerView)
        }
    }

    /**
     * Loads the list of installed applications
     */
    private fun loadAppList() {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        // Get all apps with launchers
        val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)

        appList.clear()

        for (resolveInfo in resolveInfoList) {
            val appInfo = AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                activityName = resolveInfo.activityInfo.name,
                icon = resolveInfo.loadIcon(packageManager)
            )

            // Exclude our own app from the list
            if (appInfo.packageName != context.packageName) {
                appList.add(appInfo)
            }
        }

        // Sort alphabetically
        appList.sortBy { it.label.lowercase(Locale.getDefault()) }
    }

    /**
     * Filters the app list based on search query
     */
    private fun filterAppList(query: String) {
        filteredAppList.clear()

        if (query.isEmpty()) {
            filteredAppList.addAll(appList)
        } else {
            // Filter based on app name
            filteredAppList.addAll(appList.filter {
                it.label.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
            })
        }

        appListAdapter.notifyDataSetChanged()
    }

    /**
     * Adapter for the app list RecyclerView
     */
    inner class AppListAdapter(
        private val apps: List<AppInfo>,
        private val onItemClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
                textSize = 16f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.textView.text = app.label
            holder.textView.setOnClickListener {
                onItemClick(app)
            }
        }

        override fun getItemCount() = apps.size
    }
}