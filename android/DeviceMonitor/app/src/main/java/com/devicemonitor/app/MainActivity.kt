package com.devicemonitor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devicemonitor.app.data.ApiClient
import com.devicemonitor.app.util.Constants
import com.devicemonitor.app.util.EncryptedPrefsHelper
import com.devicemonitor.app.ui.screens.DeviceDetailScreen
import com.devicemonitor.app.ui.screens.DeviceListScreen
import com.devicemonitor.app.ui.screens.SettingsScreen
import com.devicemonitor.app.ui.theme.DeviceMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 API（默认地址，用户可在设置中修改）
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val savedUrl = prefs.getString(Constants.PREF_SERVER_URL, Constants.DEFAULT_SERVER_URL) ?: Constants.DEFAULT_SERVER_URL
        ApiClient.init(savedUrl)

        // 从加密存储读取API Key
        val encryptedPrefs = EncryptedPrefsHelper(this)
        ApiClient.setApiKey(encryptedPrefs.getApiKey())

        createNotificationChannel()

        setContent {
            DeviceMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceMonitorApp()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "device_alerts",
                "设备告警",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "设备离线、温度过高等告警通知"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

// 页面转场：淡入淡出 + 轻微横向位移（屏宽 1/12），方向与列表左滑手势一致
private const val ENTER_MS = 260
private const val EXIT_MS = 200
private const val SLIDE_DIVISOR = 12

// 进入栈：新页自右侧滑入
private fun slideFadeIn() = slideInHorizontally(
    initialOffsetX = { it / SLIDE_DIVISOR },
    animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing)
) + fadeIn(tween(ENTER_MS, easing = FastOutSlowInEasing))

// 进入栈：旧页向左滑出
private fun slideFadeOut() = slideOutHorizontally(
    targetOffsetX = { -it / SLIDE_DIVISOR },
    animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing)
) + fadeOut(tween(EXIT_MS, easing = FastOutSlowInEasing))

// 出栈（返回）：上一页自左侧滑回
private fun slideFadePopIn() = slideInHorizontally(
    initialOffsetX = { -it / SLIDE_DIVISOR },
    animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing)
) + fadeIn(tween(ENTER_MS, easing = FastOutSlowInEasing))

// 出栈（返回）：当前页向右滑走
private fun slideFadePopOut() = slideOutHorizontally(
    targetOffsetX = { it / SLIDE_DIVISOR },
    animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing)
) + fadeOut(tween(EXIT_MS, easing = FastOutSlowInEasing))

@Composable
fun DeviceMonitorApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "devices"
    ) {
        composable(
            route = "devices",
            enterTransition = { slideFadeIn() },
            exitTransition = { slideFadeOut() },
            popEnterTransition = { slideFadePopIn() },
            popExitTransition = { slideFadePopOut() }
        ) {
            DeviceListScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate("device/$deviceId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable(
            route = "device/{deviceId}",
            enterTransition = { slideFadeIn() },
            exitTransition = { slideFadeOut() },
            popEnterTransition = { slideFadePopIn() },
            popExitTransition = { slideFadePopOut() }
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "settings",
            enterTransition = { slideFadeIn() },
            exitTransition = { slideFadeOut() },
            popEnterTransition = { slideFadePopIn() },
            popExitTransition = { slideFadePopOut() }
        ) {
            SettingsScreen(
                context = context,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
