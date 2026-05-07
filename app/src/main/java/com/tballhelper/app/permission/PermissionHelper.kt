package com.tballhelper.app.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class PermissionHelper(private val activity: Activity) {

    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        if (XXPermissions.isHasPermission(activity, Permission.SYSTEM_ALERT_WINDOW)) {
            callback(true)
        } else {
            XXPermissions.startPermissionActivity(activity, Permission.SYSTEM_ALERT_WINDOW) { _, granted ->
                callback(granted)
            }
        }
    }

    fun requestMediaProjection(callback: ActivityResultLauncher<Intent>) {
        val intent = Intent("android.media.action.VIDEO_CAPTURE")
        if (intent.resolveActivity(activity.packageManager) != null) {
            callback.launch(intent)
        } else {
            callback.launch(Intent().apply {
                setClassName(
                    "com.android.systemui",
                    "com.android.systemui.mediaProjection.ModalDialogFactory\$ModalDialogActivity"
                )
            })
        }
    }

    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        XXPermissions.with(activity)
            .permission(Permission.POST_NOTIFICATIONS)
            .request { _, granted -> callback(granted) }
    }
}
