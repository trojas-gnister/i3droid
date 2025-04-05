package com.i3droid

/**
 * Singleton helper to track freeform mode state across the app
 */
class FreeformHelper private constructor() {
    var isFreeformActive = false
    var isInFreeformWorkspace = false

    companion object {
        @Volatile
        private var instance: FreeformHelper? = null

        fun getInstance(): FreeformHelper {
            return instance ?: synchronized(this) {
                instance ?: FreeformHelper().also { instance = it }
            }
        }
    }
}