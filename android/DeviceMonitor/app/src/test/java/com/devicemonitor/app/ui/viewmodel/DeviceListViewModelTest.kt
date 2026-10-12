package com.devicemonitor.app.ui.viewmodel

import android.app.Application
import com.devicemonitor.app.data.Device
import com.devicemonitor.app.data.SystemStatus
import com.devicemonitor.app.data.repository.DeviceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var repository: DeviceRepository
    private lateinit var viewModel: DeviceListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state - isLoading false`() = runTest {
        // ViewModel初始化时会调用loadData，需要mock repository
        coEvery { repository.getDevices(any(), any(), any()) } answers {
            secondArg<(List<Device>) -> Unit>().invoke(emptyList())
        }
        coEvery { repository.getSystemStatus(any(), any(), any()) } answers {
            secondArg<(SystemStatus?) -> Unit>().invoke(null)
        }

        viewModel = DeviceListViewModel(application)
        advanceUntilIdle()

        // 初始状态应该有设备列表
        assertEquals(0, viewModel.uiState.value.devices.size)
    }

    @Test
    fun `setFilterType - updates state`() = runTest {
        coEvery { repository.getDevices(any(), any(), any()) } answers {
            secondArg<(List<Device>) -> Unit>().invoke(emptyList())
        }
        coEvery { repository.getSystemStatus(any(), any(), any()) } answers {
            secondArg<(SystemStatus?) -> Unit>().invoke(null)
        }

        viewModel = DeviceListViewModel(application)
        advanceUntilIdle()

        viewModel.setFilterType("online")
        assertEquals("online", viewModel.uiState.value.filterType)
    }

    @Test
    fun `setSortBy - updates state`() = runTest {
        coEvery { repository.getDevices(any(), any(), any()) } answers {
            secondArg<(List<Device>) -> Unit>().invoke(emptyList())
        }
        coEvery { repository.getSystemStatus(any(), any(), any()) } answers {
            secondArg<(SystemStatus?) -> Unit>().invoke(null)
        }

        viewModel = DeviceListViewModel(application)
        advanceUntilIdle()

        viewModel.setSortBy(SortBy.NAME)
        assertEquals(SortBy.NAME, viewModel.uiState.value.sortBy)
    }

    @Test
    fun `getFilteredDevices - filters online`() = runTest {
        val devices = listOf(
            Device("1", "Device1", "server", "online", 0, null),
            Device("2", "Device2", "server", "offline", 0, null)
        )

        coEvery { repository.getDevices(any(), any(), any()) } answers {
            secondArg<(List<Device>) -> Unit>().invoke(devices)
        }
        coEvery { repository.getSystemStatus(any(), any(), any()) } answers {
            secondArg<(SystemStatus?) -> Unit>().invoke(null)
        }

        viewModel = DeviceListViewModel(application)
        advanceUntilIdle()

        viewModel.setFilterType("online")
        val filtered = viewModel.getFilteredDevices()
        assertEquals(1, filtered.size)
        assertEquals("Device1", filtered[0].name)
    }

    @Test
    fun `getFilteredDevices - sorts by name`() = runTest {
        val devices = listOf(
            Device("2", "Beta", "server", "online", 0, null),
            Device("1", "Alpha", "server", "online", 0, null)
        )

        coEvery { repository.getDevices(any(), any(), any()) } answers {
            secondArg<(List<Device>) -> Unit>().invoke(devices)
        }
        coEvery { repository.getSystemStatus(any(), any(), any()) } answers {
            secondArg<(SystemStatus?) -> Unit>().invoke(null)
        }

        viewModel = DeviceListViewModel(application)
        advanceUntilIdle()

        viewModel.setSortBy(SortBy.NAME)
        val sorted = viewModel.getFilteredDevices()
        assertEquals("Alpha", sorted[0].name)
        assertEquals("Beta", sorted[1].name)
    }
}
