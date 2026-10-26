package com.mockrun.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mockrun.app.ui.navigation.AppNavigation
import com.mockrun.app.core.designsystem.MockRunTheme
import com.mockrun.app.feature.disclaimer.DisclaimerGateScreen
import com.mockrun.app.feature.disclaimer.DisclaimerPrefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled; Compose UI handles developer settings check
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MockRunTheme {
                // 免责声明门禁：未接受过当前版本声明的用户，先看全屏条款页面，
                // 必须滚动到底部并手动输入确认短语才能进入主界面。
                // 用 rememberSaveable 记住本次会话的接受状态，避免配置变更后重复显示。
                var accepted by rememberSaveable {
                    mutableStateOf(DisclaimerPrefs.hasAccepted(this@MainActivity))
                }

                if (accepted) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        checkAndRequestPermissions()
                    }
                    AppNavigation()
                } else {
                    DisclaimerGateScreen(
                        onAccepted = {
                            DisclaimerPrefs.markAccepted(this@MainActivity)
                            accepted = true
                            // 接受之后再申请权限：先让用户明白为什么要这些权限，
                            // 而不是一启动就弹系统授权框。
                            checkAndRequestPermissions()
                        },
                        onDecline = {
                            // 不同意则退出，不留后台进程
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Notification permission for Android 13+ (needed for foreground service notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 轨迹录制的计步传感器：API 29+ 注册 TYPE_STEP_COUNTER 需要 ACTIVITY_RECOGNITION
        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}
