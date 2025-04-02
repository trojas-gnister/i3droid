package com.example.i3tilingmanager.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i3tilingmanager.I3TilingManagerApplication
import com.example.i3tilingmanager.model.*
import com.example.i3tilingmanager.util.AppUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = I3TilingManagerApplication.getInstance()
    val tilingConfig = app.tilingConfiguration.value
    
    var currentWorkspace by remember { mutableStateOf(0) }
    var showAppDrawer by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    
    // Cache installed apps
    val installedApps = remember {
        AppUtil.getInstalledApps(context.packageManager)
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Workspaces",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()
                
                // Workspace list
                tilingConfig.workspaces.forEachIndexed { index, workspace ->
                    NavigationDrawerItem(
                        label = { Text(workspace.name) },
                        selected = currentWorkspace == index,
                        onClick = {
                            currentWorkspace = index
                            coroutineScope.launch {
                                drawerState.close()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        // Navigate to settings
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("i3 Tiling Manager") },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAppDrawer = true }) {
                            Icon(Icons.Default.Apps, contentDescription = "Launch App")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    // Add a new application to the current workspace
                    showAppDrawer = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add App")
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Current workspace content
                val workspace = tilingConfig.workspaces.getOrNull(currentWorkspace)
                if (workspace != null) {
                    WorkspaceContent(workspace)
                }
                
                // App drawer
                if (showAppDrawer) {
                    AppDrawerDialog(
                        installedApps = installedApps,
                        onAppSelected = { packageName ->
                            launchAppInFreeform(context, packageName, workspace)
                            showAppDrawer = false
                        },
                        onDismiss = { showAppDrawer = false }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceContent(workspace: Workspace) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Display the tiling layout visualization
        TilingLayoutVisualization(
            layout = workspace.layout,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        )
        
        // Display running apps in this workspace
        RunningAppsBar(
            apps = workspace.runningApps,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun TilingLayoutVisualization(
    layout: TilingLayout,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        // This is a simplified visualization of the tiling layout
        // In a real implementation, this would show the actual windows
        when (layout) {
            is HorizontalSplitLayout -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(layout.ratio)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(4.dp)
                    ) {
                        TilingLayoutVisualization(
                            layout = layout.left,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1 - layout.ratio)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(4.dp)
                    ) {
                        TilingLayoutVisualization(
                            layout = layout.right,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            is VerticalSplitLayout -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(layout.ratio)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(4.dp)
                    ) {
                        TilingLayoutVisualization(
                            layout = layout.top,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1 - layout.ratio)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(4.dp)
                    ) {
                        TilingLayoutVisualization(
                            layout = layout.bottom,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            is SingleAppLayout -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    layout.appInfo?.let { appInfo ->
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = appInfo.label ?: appInfo.packageName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } ?: run {
                        Text(
                            text = "Empty Container",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RunningAppsBar(
    apps: List<AppInfo>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        apps.forEach { app ->
            Text(
                text = app.label ?: app.packageName,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppDrawerDialog(
    installedApps: List<AppInfo>,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select an application") },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                items(installedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app.packageName) }
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.icon?.let {
                            Icon(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = app.label ?: app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}

fun launchAppInFreeform(
    context: android.content.Context,
    packageName: String,
    workspace: Workspace?
) {
    // Get the launch intent for the app
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    
    // If we can't launch the app, do nothing
    if (launchIntent == null) return
    
    // Set flags to open in a new window
    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    
    // Launch the app
    context.startActivity(launchIntent)
}
