package com.devicemonitor.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * APP内更新工具类
 * 检查更新、下载APK、自动安装
 */
object AppUpdateHelper {

    private const val TAG = "AppUpdate"
    private const val VERSION_CHECK_URL = "http://YOUR_SERVER_IP:8081/apk/device-monitor-version.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    /**
     * 检查更新
     * @return 有新版本返回UpdateInfo，无新版本返回null
     */
    suspend fun checkUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(VERSION_CHECK_URL).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext null

                val json = response.body?.string() ?: return@withContext null
                val obj = JSONObject(json)

                val latestCode = obj.getInt("versionCode")
                val currentCode = getCurrentVersionCode(context)

                if (latestCode > currentCode) {
                    UpdateInfo(
                        versionCode = latestCode,
                        versionName = obj.getString("versionName"),
                        downloadUrl = obj.getString("downloadUrl"),
                        releaseNotes = obj.optString("releaseNotes", "")
                    )
                } else {
                    null
                }
            } catch (e: IOException) {
                Log.w(TAG, "检查更新失败: ${e.message}")
                null
            }
        }
    }

    /**
     * 下载并安装APK
     */
    suspend fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext false

                val apkFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "DeviceMonitor-v$versionName.apk"
                )

                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!apkFile.exists() || apkFile.length() < 1000000) {
                    return@withContext false
                }

                // 安装APK
                installApk(context, apkFile)
                true
            } catch (e: IOException) {
                Log.e(TAG, "下载安装失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 调用系统安装器安装APK
     */
    private fun installApk(context: Context, apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }

    /**
     * 获取当前版本号
     */
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "获取版本号失败: ${e.message}")
            0
        }
    }

    /**
     * 获取当前版本名
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "获取版本名失败: ${e.message}")
            "未知"
        }
    }
}
