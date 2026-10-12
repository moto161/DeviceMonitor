package com.devicemonitor.app.data.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.devicemonitor.app.data.Alert
import com.devicemonitor.app.data.Device
import com.devicemonitor.app.data.SystemStatus

class CacheRepository(context: Context) {
    private val cacheDao = AppDatabase.getDatabase(context).cacheDao()
    private val gson = Gson()

    companion object {
        private const val TAG = "CacheRepository"
        const val KEY_DEVICES = "devices"
        const val KEY_ALERTS = "alerts"
        const val KEY_SYSTEM_STATUS = "system_status"
        const val CACHE_EXPIRY_MS = 5 * 60 * 1000L
    }

    // 通用：保存列表
    private suspend inline fun <reified T> saveList(key: String, data: List<T>) {
        cacheDao.insert(CacheEntity(key, gson.toJson(data)))
    }

    // 通用：读取列表
    private suspend inline fun <reified T> getList(key: String): List<T>? {
        val cache = cacheDao.getByKey(key) ?: return null
        return try {
            gson.fromJson(cache.value, object : TypeToken<List<T>>() {}.type)
        } catch (e: JsonParseException) {
            Log.w(TAG, "解析缓存列表失败 key=$key", e)
            null
        }
    }

    // 通用：保存对象
    private suspend inline fun <reified T> saveObject(key: String, data: T) {
        cacheDao.insert(CacheEntity(key, gson.toJson(data)))
    }

    // 通用：读取对象
    private suspend inline fun <reified T> getObject(key: String): T? {
        val cache = cacheDao.getByKey(key) ?: return null
        return try {
            gson.fromJson(cache.value, T::class.java)
        } catch (e: JsonParseException) {
            Log.w(TAG, "解析缓存对象失败 key=$key", e)
            null
        }
    }

    // 对外接口（薄封装）
    suspend fun saveDevices(devices: List<Device>) = saveList(KEY_DEVICES, devices)
    suspend fun getDevices(): List<Device>? = getList(KEY_DEVICES)
    suspend fun saveAlerts(alerts: List<Alert>) = saveList(KEY_ALERTS, alerts)
    suspend fun getAlerts(): List<Alert>? = getList(KEY_ALERTS)
    suspend fun saveSystemStatus(status: SystemStatus) = saveObject(KEY_SYSTEM_STATUS, status)
    suspend fun getSystemStatus(): SystemStatus? = getObject(KEY_SYSTEM_STATUS)

    suspend fun isCacheExpired(key: String): Boolean {
        val cache = cacheDao.getByKey(key) ?: return true
        return System.currentTimeMillis() - cache.timestamp > CACHE_EXPIRY_MS
    }

    suspend fun clearAll() = cacheDao.clearAll()
}
