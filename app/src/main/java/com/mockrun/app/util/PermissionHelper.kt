package com.mockrun.app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class PermissionIssueType {
    NONE,
    LOCATION_PERMISSION_MISSING,
    MOCK_LOCATION_APP_NOT_SET
}

object PermissionHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isMockLocationAppSet(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                true
            } else {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val probe = "mock_probe_test"
                try {
                    lm.addTestProvider(probe, false, false, false, false, false, false, false, 1, 1)
                    lm.removeTestProvider(probe)
                    true
                } catch (se: SecurityException) {
                    false
                } catch (e: Exception) {
                    true
                }
            }
        } catch (e: Exception) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val probe = "mock_probe_test"
            try {
                lm.addTestProvider(probe, false, false, false, false, false, false, false, 1, 1)
                lm.removeTestProvider(probe)
                true
            } catch (se: SecurityException) {
                false
            } catch (e: Exception) {
                true
            }
        }
    }

    fun checkPrimaryPermissions(context: Context): PermissionIssueType {
        if (!hasLocationPermission(context)) {
            return PermissionIssueType.LOCATION_PERMISSION_MISSING
        }
        if (!isMockLocationAppSet(context)) {
            return PermissionIssueType.MOCK_LOCATION_APP_NOT_SET
        }
        return PermissionIssueType.NONE
    }

    fun openDevelopmentSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}