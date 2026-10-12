package com.devicemonitor.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密存储帮助类
 * 使用AndroidX Security库，密钥由Android Keystore管理
 */
class EncryptedPrefsHelper(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "device_monitor_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DELETE_CODE = "delete_code"
        // 默认值做base64混淆，避免反编译直接看到明文
        private val DEFAULT_API_KEY by lazy {
            String(android.util.Base64.decode("WU9VUl9BUElfS0VZ", android.util.Base64.DEFAULT))
        }
        private val DEFAULT_DELETE_CODE by lazy {
            String(android.util.Base64.decode("eXhjbG92ZWx6", android.util.Base64.DEFAULT))
        }
    }

    /**
     * 获取API Key
     * 如果不存在则写入默认值
     */
    fun getApiKey(): String {
        val key = prefs.getString(KEY_API_KEY, null)
        if (key == null) {
            prefs.edit().putString(KEY_API_KEY, DEFAULT_API_KEY).apply()
            return DEFAULT_API_KEY
        }
        return key
    }

    /**
     * 获取删除验证码
     */
    fun getDeleteCode(): String {
        val code = prefs.getString(KEY_DELETE_CODE, null)
        if (code == null) {
            prefs.edit().putString(KEY_DELETE_CODE, DEFAULT_DELETE_CODE).apply()
            return DEFAULT_DELETE_CODE
        }
        return code
    }

    /**
     * 更新API Key
     */
    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    /**
     * 更新删除验证码
     */
    fun setDeleteCode(code: String) {
        prefs.edit().putString(KEY_DELETE_CODE, code).apply()
    }
}
