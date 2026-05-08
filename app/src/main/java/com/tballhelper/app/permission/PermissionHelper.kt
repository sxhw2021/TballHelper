package com.tballhelper.app.permission

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

class PermissionHelper(private val activity: Activity) {

    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        if (Settings.canDrawOverlays(activity)) {
            callback(true)
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            activity.startActivity(intent)
            callback(false)
        }
    }

    fun requestMediaProjection(callback: ActivityResultLauncher<Intent>) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        callback.launch(intent)
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(activity)
    }
}