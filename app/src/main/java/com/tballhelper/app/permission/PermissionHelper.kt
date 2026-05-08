package com.tballhelper.app.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.hjq.permissions.XXPermissions

class PermissionHelper(private val activity: Activity) {

    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        if (XXPermissions.isHasPermission(activity, android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
            callback(true)
        } else {
            XXPermissions.startPermissionActivity(activity, android.Manifest.permission.SYSTEM_ALERT_WINDOW)
            callback(false)
        }
    }

    fun requestMediaProjection(callback: ActivityResultLauncher<Intent>) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        callback.launch(intent)
    }

    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        XXPermissions.with(activity)
            .permission(android.Manifest.permission.POST_NOTIFICATIONS)
            .request { _, granted -> callback(granted) }
    }
}