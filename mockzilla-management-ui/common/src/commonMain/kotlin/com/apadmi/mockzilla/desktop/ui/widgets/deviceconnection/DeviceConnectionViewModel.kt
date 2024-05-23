package com.apadmi.mockzilla.desktop.ui.widgets.deviceconnection

import androidx.compose.runtime.Immutable
import com.apadmi.mockzilla.desktop.engine.adb.AdbConnection
import com.apadmi.mockzilla.desktop.engine.adb.AdbConnectorUseCase
import com.apadmi.mockzilla.desktop.engine.device.ActiveDeviceSelector
import com.apadmi.mockzilla.desktop.engine.device.Device
import com.apadmi.mockzilla.desktop.engine.device.MetaDataUseCase
import com.apadmi.mockzilla.desktop.viewmodel.ViewModel
import com.apadmi.mockzilla.lib.config.ZeroConfConfig
import com.apadmi.mockzilla.lib.models.MetaData
import com.apadmi.mockzilla.lib.models.MetaData.Companion.parseMetaData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceTypeListener


class DeviceConnectionViewModel(
    private val metaDataUseCase: MetaDataUseCase,
    private val activeDeviceSelector: ActiveDeviceSelector,
    private val adbConnectorUseCase: AdbConnectorUseCase
) : ViewModel() {
    val state = MutableStateFlow(State())
    private var connectionJob: Job? = null


    init {
        // TODO: This is proof of concept, tidy all this and bullet proof it once Bonjour is brought in
        viewModelScope.launch {
            adbConnectorUseCase.listConnectedDevices().onSuccess {
                state.value = state.value.copy(adbDevices = it)
            }
        }

        val jmdns: JmDNS = JmDNS.create(InetAddress.getLocalHost())

        jmdns.addServiceTypeListener(object : ServiceTypeListener {
            override fun serviceTypeAdded(event: ServiceEvent?) {
                println("abc serviceTypeAdded $event")

                if (event?.type?.startsWith(ZeroConfConfig.serviceType) == true) {
                    jmdns.addServiceListener("${ZeroConfConfig.serviceType}.local.", object : ServiceListener {
                        override fun serviceAdded(event: ServiceEvent) {
                            println("abc adding ${format(event.info)}")
                        }

                        override fun serviceRemoved(event: ServiceEvent) {
                            println("abc removed ${format(event.info)}")
                        }

                        override fun serviceResolved(event: ServiceEvent) {
                            println("abc resolved ${format(event.info)}")
                        }
                    })

                    viewModelScope.launch {
                        while (true) {
                            delay(1500)
                            println(
                                "abc mockzilla subtype ${
                                    jmdns.list("_mockzilla._tcp.local.").joinToString {
                                        format(it)
                                    }
                                }"
                            )
                        }
                    }

                }
            }

            override fun subTypeForServiceTypeAdded(event: ServiceEvent?) {
                println("abc subTypeForServiceTypeAdded $event")
            }

        })


    }

    private fun format(info: ServiceInfo): String {
        val map = info.propertyNames.toList().associateWith { info.getPropertyString(it) }

        return "${info.name}: ${info.port} $map ${map.parseMetaData()}"
    }


    // TODO: Replace this with better strategies of device connections
    // This is just a rough and ready way to get the UI to be testable
    fun onIpAndPortChanged(newValue: String) {
        connectionJob?.cancel()
        val device = createDeviceOrNull(newValue)
        device ?: run {
            state.value = state.value.copy(
                ipAndPort = newValue,
                connectionState = State.ConnectionState.Disconnected
            )
            return
        }

        state.value = state.value.copy(
            ipAndPort = newValue,
            connectionState = State.ConnectionState.Connecting
        )
        connectionJob = awaitConnectionAndSetState(device)
    }

    // TODO: Hardcoding ports now and ignoring real devices just as a proof of concept
    fun connect8080To0(adbConnection: AdbConnection) = viewModelScope.launch {
        if (!adbConnection.deviceSerial.startsWith("emulator")) {
            throw Exception("Non emulators not supported yet")
        }
        adbConnectorUseCase.setupPortForwardingIfNeeded(adbConnection, 0, 8080).onSuccess {
            onIpAndPortChanged("127.0.0.1:${it.localPort}")
        }.onFailure { throw it }
    }

    private fun createDeviceOrNull(ipAndPort: String): Device? {
        val split = ipAndPort.split(":").takeIf { it.size == 2 } ?: return null
        return Device(split[0], split[1])
    }

    private fun awaitConnectionAndSetState(device: Device): Job = viewModelScope.launch {
        do {
            yield()
            state.value = state.value.copy(
                connectionState = metaDataUseCase.getMetaData(device)
                    .onSuccess { onSuccessfulConnection(device, it) }
                    .fold(onSuccess = { State.ConnectionState.Connected },
                        onFailure = { State.ConnectionState.Connecting })
            )
            delay(300)
        } while (state.value.connectionState != State.ConnectionState.Connected)
    }

    private fun onSuccessfulConnection(tmpDevice: Device, metaData: MetaData) {
        activeDeviceSelector.setActiveDeviceWithMetaData(tmpDevice, metaData)
    }

    @Immutable
    data class State(
        val ipAndPort: String = "",
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val adbDevices: List<AdbConnection> = emptyList()
    ) {
        enum class ConnectionState {
            Connected,
            Connecting,
            Disconnected,
            ;
        }
    }
}
