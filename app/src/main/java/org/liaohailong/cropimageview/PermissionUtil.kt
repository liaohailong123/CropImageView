package org.liaohailong.cropimageview

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * Author: liaohailong
 * Date: 2019/3/18
 * Time: 8:08 PM
 * Description: 权限请求
 **/
object PermissionUtil {

    fun requestIfNot(activity: Activity, permission: String, requestCode: Int): Boolean {
        return PermissionUtil.requestIfNot(activity, listOf(permission), requestCode)
    }

    fun requestIfNot(activity: Activity, permissions: List<String>, requestCode: Int): Boolean {
        val deniedPermissions = mutableListOf<String>()
        for (permission in permissions) {
            if (PackageManager.PERMISSION_DENIED == ActivityCompat.checkSelfPermission(activity, permission)) {
                deniedPermissions.add(permission)
            }
        }
        if (deniedPermissions.isEmpty()) return true
        ActivityCompat.requestPermissions(activity, deniedPermissions.toTypedArray(), requestCode)
        return false
    }
}