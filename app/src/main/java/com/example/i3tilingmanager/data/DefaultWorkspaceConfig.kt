package com.example.i3tilingmanager.data

import com.example.i3tilingmanager.model.*

/**
 * Provides default workspace configurations for the i3 Tiling Manager.
 */
object DefaultWorkspaceConfig {
    
    /**
     * Get the default tiling configuration with predefined workspaces.
     */
    fun getDefaultConfig(): TilingConfiguration {
        val workspaces = listOf(
            createDefaultWorkspace(0, "Main"),
            createWorkspaceWithHorizontalSplit(1, "Work"),
            createWorkspaceWithVerticalSplit(2, "Social"),
            createWorkspaceWithQuadrants(3, "Productivity")
        )
        
        return TilingConfiguration(
            workspaces = workspaces,
            activeWorkspace = 0
        )
    }
    
    /**
     * Create a default workspace with a single container.
     */
    private fun createDefaultWorkspace(id: Int, name: String): Workspace {
        return Workspace(
            id = id,
            name = name,
            layout = SingleAppLayout()
        )
    }
    
    /**
     * Create a workspace with a horizontal split layout.
     */
    private fun createWorkspaceWithHorizontalSplit(id: Int, name: String): Workspace {
        return Workspace(
            id = id,
            name = name,
            layout = HorizontalSplitLayout(
                left = SingleAppLayout(),
                right = SingleAppLayout(),
                ratio = 0.6f
            )
        )
    }
    
    /**
     * Create a workspace with a vertical split layout.
     */
    private fun createWorkspaceWithVerticalSplit(id: Int, name: String): Workspace {
        return Workspace(
            id = id,
            name = name,
            layout = VerticalSplitLayout(
                top = SingleAppLayout(),
                bottom = SingleAppLayout(),
                ratio = 0.7f
            )
        )
    }
    
    /**
     * Create a workspace with four quadrants (2x2 grid).
     */
    private fun createWorkspaceWithQuadrants(id: Int, name: String): Workspace {
        return Workspace(
            id = id,
            name = name,
            layout = VerticalSplitLayout(
                top = HorizontalSplitLayout(
                    left = SingleAppLayout(),
                    right = SingleAppLayout()
                ),
                bottom = HorizontalSplitLayout(
                    left = SingleAppLayout(),
                    right = SingleAppLayout()
                )
            )
        )
    }
}
