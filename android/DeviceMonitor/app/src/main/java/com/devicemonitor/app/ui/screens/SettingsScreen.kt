package com.devicemonitor.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.*
import com.devicemonitor.app.data.ApiClient
import com.devicemonitor.app.ui.theme.*
import com.devicemonitor.app.util.AppUpdateHelper
import com.devicemonitor.app.util.Constants
import com.devicemonitor.app.util.EncryptedPrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var tempUrl by remember { mutableStateOf(serverUrl) }
    var isSaved by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateHelper.UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    // API Key 配置
    val encryptedPrefs = remember { EncryptedPrefsHelper(context) }
    var tempApiKey by remember { mutableStateOf("") }
    // getApiKey() 无值时返回内置默认 Key，据此判断是否已被用户覆盖
    var isUsingCustomKey by remember { mutableStateOf(encryptedPrefs.getApiKey() != Constants.API_KEY) }
    var apiKeySaved by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    GlowText(
                        text = "SETTINGS",
                        color = NeonCyan,
                        fontSize = 18,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDark,
                    titleContentColor = TextWhite
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBlack)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 服务器设置
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlowText(
                        text = "SERVER CONFIG",
                        color = NeonCyan,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("服务器地址", color = TextSecondary) },
                        placeholder = { Text("http://YOUR_SERVER_IP:8443", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            cursorColor = NeonCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tempUrl.isNotEmpty()) {
                                    ApiClient.init(tempUrl)
                                    serverUrl = tempUrl
                                    isSaved = true
                                    testResult = null
                                    testSuccess = false
                                    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putString(Constants.PREF_SERVER_URL, tempUrl).apply()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = CyberBlack
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (tempUrl.isNotEmpty()) {
                                    ApiClient.init(tempUrl)
                                    isTesting = true
                                    testResult = null
                                    testSuccess = false
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val response = ApiClient.getApi().getSystemStatus().execute()
                                            if (response.isSuccessful) {
                                                val status = response.body()
                                                testSuccess = true
                                                testResult = "连接成功！在线设备: ${status?.onlineDevices ?: 0}, 总设备: ${status?.totalDevices ?: 0}"
                                            } else {
                                                testSuccess = false
                                                testResult = "服务器返回错误: ${response.code()}"
                                            }
                                        } catch (e: IOException) {
                                            testSuccess = false
                                            val errType = e::class.java.simpleName
                                            val errMsg = e.message ?: "(无详细信息)"
                                            testResult = "连接失败 [$errType]: $errMsg"
                                        }
                                        isTesting = false
                                    }
                                }
                            },
                            enabled = !isTesting,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NeonCyan
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("测试中")
                            } else {
                                Icon(Icons.Default.CloudDone, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("测试连接")
                            }
                        }
                    }
                    if (isSaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("配置已保存", color = NeonGreen, fontSize = 12.sp)
                        }
                    }
                    testResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (testSuccess) NeonGreen.copy(alpha = 0.1f) else NeonRed.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (testSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (testSuccess) NeonGreen else NeonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result,
                                color = if (testSuccess) NeonGreen else NeonRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // API Key 配置
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonGreen
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlowText(
                        text = "API KEY",
                        color = NeonGreen,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it; apiKeySaved = false },
                        label = { Text("API Key", color = TextSecondary) },
                        placeholder = { Text("与服务端 API_KEY 保持一致", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = CyberBorder,
                            cursorColor = NeonGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUsingCustomKey) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isUsingCustomKey) NeonGreen else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUsingCustomKey) "当前使用自定义 Key" else "当前使用内置默认 Key",
                            color = if (isUsingCustomKey) NeonGreen else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val key = tempApiKey.trim()
                                if (key.isNotEmpty()) {
                                    encryptedPrefs.setApiKey(key)
                                    ApiClient.setApiKey(key)
                                    tempApiKey = ""
                                    isUsingCustomKey = true
                                    apiKeySaved = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = CyberBlack
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                encryptedPrefs.setApiKey(Constants.API_KEY)
                                ApiClient.setApiKey(Constants.API_KEY)
                                tempApiKey = ""
                                isUsingCustomKey = false
                                apiKeySaved = false
                            },
                            enabled = isUsingCustomKey,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NeonGreen,
                                disabledContentColor = TextMuted
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("恢复默认")
                        }
                    }
                    if (apiKeySaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("API Key 已保存，下次请求立即生效", color = NeonGreen, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 关于
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonPurple
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlowText(
                        text = "ABOUT",
                        color = NeonPurple,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isCheckingUpdate && !isDownloading) {
                                    isCheckingUpdate = true
                                    updateMessage = null
                                    scope.launch {
                                        val info = AppUpdateHelper.checkUpdate(context)
                                        isCheckingUpdate = false
                                        if (info != null) {
                                            updateInfo = info
                                            showUpdateDialog = true
                                        } else {
                                            updateMessage = "已是最新版本"
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("版本", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonPurple, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查中...", color = NeonPurple, fontSize = 13.sp)
                        } else {
                            Text(
                                AppUpdateHelper.getCurrentVersionName(context),
                                color = NeonPurple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.SystemUpdate, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                        }
                    }
                    updateMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(msg, color = NeonGreen, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    DataItem("应用名称", "Device Monitor")
                    Spacer(modifier = Modifier.height(4.dp))
                    DataItem("当前服务器", serverUrl)
                }
            }

            // 使用说明
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonGreen
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlowText(
                        text = "QUICK GUIDE",
                        color = NeonGreen,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GuideItem("1", "在阿里云服务器部署中心服务")
                    GuideItem("2", "在各设备安装 Agent 并配置服务器地址")
                    GuideItem("3", "在本 APP 配置服务器地址")
                    GuideItem("4", "即可查看所有设备实时状态")
                }
            }
        }
    }

    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = CyberDark,
            titleContentColor = NeonPurple,
            textContentColor = TextPrimary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("发现新版本 v${updateInfo!!.versionName}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "当前版本: ${AppUpdateHelper.getCurrentVersionName(context)} → 最新版本: v${updateInfo!!.versionName}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("更新内容:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        updateInfo!!.releaseNotes,
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonPurple, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在下载安装包...", color = NeonPurple, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isDownloading) {
                            isDownloading = true
                            scope.launch {
                                val success = AppUpdateHelper.downloadAndInstall(
                                    context,
                                    updateInfo!!.downloadUrl,
                                    updateInfo!!.versionName
                                )
                                isDownloading = false
                                if (!success) {
                                    updateMessage = "下载失败，请检查网络"
                                }
                                showUpdateDialog = false
                            }
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = CyberBlack)
                ) {
                    Text(if (isDownloading) "下载中..." else "立即更新", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("稍后", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun GuideItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(NeonGreen.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = NeonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 13.sp
        )
    }
}
