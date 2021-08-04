package com.google.android.connecteddevice.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionProtocolTest {
  private lateinit var testProtocol: TestConnectionProtocol
  private val mockDeviceCallback: ConnectionProtocol.DeviceCallback = mock()

  @Before
  fun setUp() {
    testProtocol = TestConnectionProtocol()
  }

  @Test
  fun registerCallback_invokeCallbackWithCall() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerCallback(testProtocolId, mockDeviceCallback, directExecutor())
    testProtocol.invokeDeviceCallback(testProtocolId)

    verify(mockDeviceCallback).onDeviceDisconnected(testProtocolId)
  }

  @Test
  fun unregisterCallback_doNotInvokeCallbackWithCall() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerCallback(testProtocolId, mockDeviceCallback, directExecutor())
    testProtocol.unregisterCallback(testProtocolId, mockDeviceCallback)
    testProtocol.invokeDeviceCallback(testProtocolId)

    verify(mockDeviceCallback, never()).onDeviceDisconnected(testProtocolId)
  }

  @Test
  fun reset_clearDeviceCallbacks() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerCallback(testProtocolId, mockDeviceCallback, directExecutor())
    testProtocol.reset()

    testProtocol.invokeDeviceCallback(testProtocolId)
    verify(mockDeviceCallback, never()).onDeviceDisconnected(testProtocolId)
  }

  class TestConnectionProtocol : ConnectionProtocol() {
    override val isDeviceVerificationRequired = false

    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {}

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: UUID) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }

    fun invokeDeviceCallback(protocolId: String) {
      deviceCallbacks[protocolId]?.invoke { it.onDeviceDisconnected(protocolId) }
    }
  }
}
