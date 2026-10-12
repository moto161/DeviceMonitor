package com.devicemonitor.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devicemonitor.app.data.Alert
import com.devicemonitor.app.data.Device
import com.devicemonitor.app.data.SystemStatus
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceListUiState(
    val devices: List<Device> = emptyList(),
    val systemStatus: SystemStatus? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val alerts: List<Alert> = emptyList(),
    val isLoadingAlerts: Boolean = false,
    val filterType: String = "all",
    val sortBy: SortBy = SortBy.STATUS,
    val isFromCache: Boolean = false
)

enum class SortBy {
    STATUS, NAME, CPU, MEMORY
}

class DeviceListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceRepository(application)
    private val _uiState = MutableStateFlow(DeviceListUiState())
    private val _newAlerts = MutableSharedFlow<List<Alert>>(extraBufferCapacity = 10)
    val newAlerts = _newAlerts.asSharedFlow()
    private val knownAlertIds = mutableSetOf<Int>()
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        loadData(isInitialLoad = true)
        startAutoRefresh()
    }

    fun loadData(isInitialLoad: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return

        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            }

            withContext(Dispatchers.IO) {
                repository.getDevices(
                    onCache = { cachedDevices ->
                        if (cachedDevices.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                devices = cachedDevices,
                                isFromCache = true
                            )
                        }
                    },
                    onNetwork = { devices ->
                        _uiState.value = _uiState.value.copy(
                            devices = devices,
                            isLoading = false,
                            isRefreshing = false,
                            isFromCache = false,
                            errorMessage = null
                        )
                    },
                    onError = { e ->
                        val currentDevices = _uiState.value.devices
                        if (currentDevices.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isFromCache = true,
                                errorMessage = null
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isFromCache = false,
                                errorMessage = e.javaClass.simpleName + ": " + (e.message ?: "无详细信息")
                            )
                        }
                    }
                )

                repository.getSystemStatus(
                    onCache = { cachedStatus ->
                        cachedStatus?.let {
                            _uiState.value = _uiState.value.copy(systemStatus = it)
                        }
                    },
                    onNetwork = { status ->
                        _uiState.value = _uiState.value.copy(systemStatus = status)
                    },
                    onError = {
                        // 系统状态获取失败不影响设备列表，静默处理但保留日志
                        android.util.Log.w("DeviceListVM", "getSystemStatus failed: ${it.message}")
                    }
                )
            }
        }
    }

    fun loadAlerts() {
        if (_uiState.value.isLoadingAlerts) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAlerts = true)

            withContext(Dispatchers.IO) {
                repository.getAlerts(
                    onCache = { cachedAlerts ->
                        if (cachedAlerts.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(alerts = cachedAlerts)
                        }
                    },
                    onNetwork = { alerts ->
                        val newAlerts = alerts.filter { it.id !in knownAlertIds }
                        if (newAlerts.isNotEmpty() && knownAlertIds.isNotEmpty()) {
                            _newAlerts.tryEmit(newAlerts)
                        }
                        knownAlertIds.addAll(alerts.map { it.id })

                        _uiState.value = _uiState.value.copy(
                            alerts = alerts,
                            isLoadingAlerts = false
                        )
                    },
                    onError = {
                        _uiState.value = _uiState.value.copy(isLoadingAlerts = false)
                    }
                )
            }
        }
    }

    fun deleteAlert(alertId: Int) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { repository.deleteAlert(alertId) }
            if (success) {
                val updatedAlerts = _uiState.value.alerts.filter { it.id != alertId }
                val updatedStatus = _uiState.value.systemStatus?.let { status ->
                    status.copy(unacknowledgedAlerts = maxOf(0, status.unacknowledgedAlerts - 1))
                }
                _uiState.value = _uiState.value.copy(
                    alerts = updatedAlerts,
                    systemStatus = updatedStatus
                )
                loadAlerts()
            }
        }
    }

    fun deleteAllAlerts() {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { repository.deleteAllAlerts() }
            if (success) {
                val updatedStatus = _uiState.value.systemStatus?.let { status ->
                    status.copy(unacknowledgedAlerts = 0)
                }
                _uiState.value = _uiState.value.copy(
                    alerts = emptyList(),
                    systemStatus = updatedStatus
                )
                loadAlerts()
            }
        }
    }

    fun deleteDevice(deviceId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { repository.deleteDevice(deviceId) }
            if (success) {
                val updatedDevices = _uiState.value.devices.filter { it.id != deviceId }
                _uiState.value = _uiState.value.copy(devices = updatedDevices)
                onSuccess()
            } else {
                onError("删除失败")
            }
        }
    }

    fun refresh() {
        loadData(isInitialLoad = false)
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(Constants.LIST_REFRESH_INTERVAL_MS)
                // 无论是否有错误都继续刷新，网络可能恢复
                // loadData内部会跳过正在加载的情况
                loadData(isInitialLoad = false)
            }
        }
    }

    fun setFilterType(type: String) {
        _uiState.value = _uiState.value.copy(filterType = type)
    }

    fun setSortBy(sort: SortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
    }

    fun getFilteredDevices(): List<Device> {
        val state = _uiState.value
        var filtered = when (state.filterType) {
            "online" -> state.devices.filter { it.status == "online" }
            "offline" -> state.devices.filter { it.status == "offline" }
            "server" -> state.devices.filter { it.type == "server" }
            "router" -> state.devices.filter { it.type == "router" }
            "phone" -> state.devices.filter { it.type == "phone" || it.type == "tablet" }
            "pc" -> state.devices.filter { it.type == "pc" }
            else -> state.devices
        }
        filtered = when (state.sortBy) {
            SortBy.STATUS -> filtered.sortedByDescending { it.status == "online" }
            SortBy.NAME -> filtered.sortedBy { it.name }
            SortBy.CPU -> filtered.sortedByDescending { it.latestData?.cpuUsage ?: 0.0 }
            SortBy.MEMORY -> filtered.sortedByDescending { it.latestData?.memoryUsage ?: 0.0 }
        }
        return filtered
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
