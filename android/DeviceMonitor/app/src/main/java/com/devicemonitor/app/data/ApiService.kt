package com.devicemonitor.app.data

import com.devicemonitor.app.util.Constants
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.devicemonitor.app.BuildConfig

interface DeviceMonitorApi {

    @GET("api/devices")
    fun getDevices(): Call<DevicesResponse>

    @GET("api/devices/{id}")
    fun getDeviceDetail(@Path("id") id: String): Call<DeviceDetailResponse>

    @DELETE("api/devices/{id}")
    fun deleteDevice(@Path("id") id: String): Call<DeleteResponse>

    @GET("api/devices/{id}/history")
    fun getDeviceHistory(
        @Path("id") id: String,
        @Query("hours") hours: Int = 24,
        @Query("limit") limit: Int = 288
    ): Call<HistoryResponse>

    @GET("api/alerts")
    fun getAlerts(@Query("limit") limit: Int = 500): Call<AlertsResponse>

    @DELETE("api/alerts/{id}")
    fun deleteAlert(@Path("id") id: Int): Call<DeleteResponse>

    @DELETE("api/alerts")
    fun deleteAllAlerts(): Call<DeleteResponse>

    @GET("api/status")
    fun getSystemStatus(): Call<SystemStatus>
}

object ApiClient {
    private var baseUrl = Constants.DEFAULT_SERVER_URL
    private var api: DeviceMonitorApi? = null
    private var apiKey: String = Constants.API_KEY

    fun init(serverUrl: String) {
        baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        api = null
    }

    fun setApiKey(key: String) {
        apiKey = key
        api = null
    }

    fun getApi(): DeviceMonitorApi {
        if (api == null) {
            val apiKeyInterceptor = okhttp3.Interceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("X-API-Key", apiKey)
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            val clientBuilder = okhttp3.OkHttpClient.Builder()
                .addInterceptor(apiKeyInterceptor)
            // release版本禁用日志，避免泄露API Key和设备数据
            if (BuildConfig.DEBUG) {
                val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                }
                clientBuilder.addInterceptor(logging)
            }
            val client = clientBuilder
                .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(Constants.READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            api = retrofit2.Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(DeviceMonitorApi::class.java)
        }
        return api ?: error("ApiClient未初始化，请先调用ApiClient.init()")
    }

    fun getBaseUrl(): String = baseUrl
}
