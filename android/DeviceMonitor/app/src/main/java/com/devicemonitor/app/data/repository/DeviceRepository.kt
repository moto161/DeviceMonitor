package com.devicemonitor.app.data.repository

import android.content.Context
import android.util.Log
import com.devicemonitor.app.data.Alert
import com.devicemonitor.app.data.ApiClient
import com.devicemonitor.app.data.Device
import com.devicemonitor.app.data.DeviceDetail
import com.devicemonitor.app.data.HistoryPoint
import com.devicemonitor.app.data.SystemStatus
import com.devicemonitor.app.data.cache.CacheRepository
import java.io.IOException

/**
 * 设备数据仓库
 * 统一协调网络请求和本地缓存
 */
class DeviceRepository(context: Context) {

    companion object {
        private const val TAG = "DeviceRepository"
    }

    private val cacheRepository = CacheRepository(context)

    /**
     * 获取设备列表（先缓存后网络）
     */
    suspend fun getDevices(
        onCache: (List<Device>) -> Unit,
        onNetwork: (List<Device>) -> Unit,
        onError: (IOException) -> Unit
    ) {
        // 先读缓存
        val cachedDevices = cacheRepository.getDevices()
        if (!cachedDevices.isNullOrEmpty()) {
            onCache(cachedDevices)
        }

        // 再网络请求
        try {
            val response = ApiClient.getApi().getDevices().execute()
            val devices = response.body()?.devices ?: emptyList()
            // 写入缓存
            cacheRepository.saveDevices(devices)
            onNetwork(devices)
        } catch (e: IOException) {
            onError(e)
        }
    }

    /**
     * 获取系统状态
     */
    suspend fun getSystemStatus(
        onCache: (SystemStatus?) -> Unit,
        onNetwork: (SystemStatus?) -> Unit,
        onError: (IOException) -> Unit
    ) {
        val cachedStatus = cacheRepository.getSystemStatus()
        if (cachedStatus != null) {
            onCache(cachedStatus)
        }

        try {
            val response = ApiClient.getApi().getSystemStatus().execute()
            val status = response.body()
            status?.let { cacheRepository.saveSystemStatus(it) }
            onNetwork(status)
        } catch (e: IOException) {
            onError(e)
        }
    }

    /**
     * 获取告警列表
     */
    suspend fun getAlerts(
        onCache: (List<Alert>) -> Unit,
        onNetwork: (List<Alert>) -> Unit,
        onError: (IOException) -> Unit
    ) {
        val cachedAlerts = cacheRepository.getAlerts()
        if (!cachedAlerts.isNullOrEmpty()) {
            onCache(cachedAlerts)
        }

        try {
            val response = ApiClient.getApi().getAlerts().execute()
            val alerts = response.body()?.alerts ?: emptyList()
            cacheRepository.saveAlerts(alerts)
            onNetwork(alerts)
        } catch (e: IOException) {
            onError(e)
        }
    }

    /**
     * 删除单条告警
     */
    suspend fun deleteAlert(alertId: Int): Boolean {
        return try {
            val response = ApiClient.getApi().deleteAlert(alertId).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "删除告警失败 id=$alertId", e)
            false
        }
    }

    /**
     * 删除全部告警
     */
    suspend fun deleteAllAlerts(): Boolean {
        return try {
            val response = ApiClient.getApi().deleteAllAlerts().execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "删除全部告警失败", e)
            false
        }
    }

    /**
     * 删除设备
     */
    suspend fun deleteDevice(deviceId: String): Boolean {
        return try {
            val response = ApiClient.getApi().deleteDevice(deviceId).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.w(TAG, "删除设备失败 id=$deviceId", e)
            false
        }
    }

    /**
     * 获取设备详情
     */
    suspend fun getDeviceDetail(deviceId: String): DeviceDetail? {
        return try {
            val response = ApiClient.getApi().getDeviceDetail(deviceId).execute()
            response.body()?.device
        } catch (e: IOException) {
            Log.w(TAG, "获取设备详情失败 id=$deviceId", e)
            null
        }
    }

    /**
     * 获取设备历史数据
     */
    suspend fun getDeviceHistory(deviceId: String): List<HistoryPoint> {
        return try {
            val response = ApiClient.getApi().getDeviceHistory(deviceId).execute()
            response.body()?.history ?: emptyList()
        } catch (e: IOException) {
            Log.w(TAG, "获取设备历史失败 id=$deviceId", e)
            emptyList()
        }
    }
}
