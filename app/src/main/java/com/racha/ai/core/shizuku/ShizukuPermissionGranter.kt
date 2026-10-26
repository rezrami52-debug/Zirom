package com.racha.ai.core.shizuku

object ShizukuPermissionGranter {

    fun grantPermission(packageName: String, permission: String): Boolean {
        return ShizukuShellRunner.grantPermission(packageName, permission)
    }

    /**
     * منح جميع الصلاحيات التكتيكية الحساسة لتطبيق رشا بضغطة واحدة
     */
    fun grantAllRachaPermissions(packageName: String): Boolean {
        val permissions = listOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.DUMP",
            "android.permission.PACKAGE_USAGE_STATS"
        )
        return permissions.all { perm ->
            ShizukuShellRunner.grantPermission(packageName, perm)
        }
    }
}
