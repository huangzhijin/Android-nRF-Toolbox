package no.nordicsemi.android.rscs.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.rscs.repository.RSCSManager
import no.nordicsemi.android.rscs.repository.RSCSService
import no.nordicsemi.android.service.BleManagerResult
import no.nordicsemi.android.service.ConnectingResult
import no.nordicsemi.android.service.ServiceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RSCSRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val serviceManager: ServiceManager,
) {
    private var manager: RSCSManager? = null

    private val _data = MutableStateFlow<BleManagerResult<RSCSData>>(ConnectingResult())
    internal val data = _data.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun launch(device: BluetoothDevice) {
        serviceManager.startService(RSCSService::class.java, device)
    }

    fun start(device: BluetoothDevice, scope: CoroutineScope) {
        val manager = RSCSManager(context, scope)
        this.manager = manager

        manager.dataHolder.status.onEach {
            _data.value = it
        }.launchIn(scope)

        scope.launch {
            manager.start(device)
        }
    }

    private suspend fun RSCSManager.start(device: BluetoothDevice) {
        try {
            connect(device)
                .useAutoConnect(false)
                .retry(3, 100)
                .suspend()
            _isRunning.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        serviceManager.stopService(RSCSService::class.java)
        manager?.disconnect()?.enqueue()
        manager = null
        _isRunning.value = false
    }
}
