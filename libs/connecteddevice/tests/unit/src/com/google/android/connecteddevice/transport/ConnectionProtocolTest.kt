package com.google.android.connecteddevice.transport

import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ConnectionProtocolTest {
  private lateinit var testProtocol: TestConnectionProtocol
  private val mockDataReceivedListener = mockToBeAlive<IDataReceivedListener>()
  private val mockDisconnectedListener = mockToBeAlive<IDeviceDisconnectedListener>()
  private val mockMaxDataSizeChangedListener = mockToBeAlive<IDeviceMaxDataSizeChangedListener>()

  @Before
  fun setUp() {
    testProtocol = TestConnectionProtocol()
  }

  @Test
  fun registerDataReceivedListener_invokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)

    testProtocol.invokeDataReceivedListeners(testProtocolId, testData)

    verify(mockDataReceivedListener).onDataReceived(testProtocolId, testData)
  }

  @Test
  fun registerDataReceivedListener_invokeListenerWithMissedData() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    testProtocol.notifyDataReceived(testProtocolId, testData)

    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)

    verify(mockDataReceivedListener).onDataReceived(testProtocolId, testData)
  }

  @Test
  fun registerDataReceivedListener_deliveredMissedDataCleared() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    testProtocol.notifyDataReceived(testProtocolId, testData)

    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)
    val mockSecondDataReceivedListener: IDataReceivedListener = mock()
    testProtocol.registerDataReceivedListener(testProtocolId, mockSecondDataReceivedListener)

    verify(mockDataReceivedListener).onDataReceived(testProtocolId, testData)
    verify(mockSecondDataReceivedListener, never()).onDataReceived(testProtocolId, testData)
  }

  @Test
  fun unregisterDataReceivedListener_doNotInvokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)
    testProtocol.unregisterDataReceivedListener(testProtocolId, mockDataReceivedListener)

    testProtocol.invokeDataReceivedListeners(testProtocolId, testData)

    verify(mockDataReceivedListener, never()).onDataReceived(testProtocolId, testData)
  }

  @Test
  fun unregisterDataReceivedListener_invalidListener_doNotThrowException() {
    testProtocol.unregisterDataReceivedListener("testProtocolId", mockDataReceivedListener)
  }

  @Test
  fun unregisterDataReceivedListener_onLastListenerUnregistered_keyCleared() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)

    testProtocol.unregisterDataReceivedListener(testProtocolId, mockDataReceivedListener)

    assertThat(testProtocol.dataReceivedListenerList).doesNotContainKey(testProtocolId)
  }

  @Test
  fun registerDeviceDisconnectListener_invokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerDeviceDisconnectedListener(testProtocolId, mockDisconnectedListener)

    testProtocol.invokeDeviceDisconnectedListeners(testProtocolId)

    verify(mockDisconnectedListener).onDeviceDisconnected(testProtocolId)
  }

  @Test
  fun unregisterDeviceDisconnectListener_doNotInvokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerDeviceDisconnectedListener(testProtocolId, mockDisconnectedListener)
    testProtocol.unregisterDeviceDisconnectListener(testProtocolId, mockDisconnectedListener)

    testProtocol.invokeDeviceDisconnectedListeners(testProtocolId)

    verify(mockDisconnectedListener, never()).onDeviceDisconnected(testProtocolId)
  }

  @Test
  fun unregisterDeviceDisconnectListener_invalidListener_doNotThrowException() {
    testProtocol.unregisterDeviceDisconnectListener("testProtocolId", mockDisconnectedListener)
  }

  @Test
  fun unregisterDeviceDisconnectListener_onLastListenerUnregistered_keyCleared() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerDeviceDisconnectedListener(testProtocolId, mockDisconnectedListener)

    testProtocol.unregisterDeviceDisconnectListener(testProtocolId, mockDisconnectedListener)

    assertThat(testProtocol.deviceDisconnectedListenerList).doesNotContainKey(testProtocolId)
  }

  @Test
  fun registerDeviceMaxDataSizeChangedListener_invokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    val testSize = 20
    testProtocol.registerDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )

    testProtocol.invokeDeviceMaxDataSizeChangedListener(testProtocolId, testSize)

    verify(mockMaxDataSizeChangedListener).onDeviceMaxDataSizeChanged(testProtocolId, testSize)
  }

  @Test
  fun unregisterDeviceMaxDataSizeChangedListener_doNotInvokeListenerWithCall() {
    val testProtocolId = "testProtocolId"
    val testSize = 20
    testProtocol.registerDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )
    testProtocol.unregisterDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )

    testProtocol.invokeDeviceMaxDataSizeChangedListener(testProtocolId, testSize)

    verify(mockMaxDataSizeChangedListener, never())
      .onDeviceMaxDataSizeChanged(testProtocolId, testSize)
  }

  @Test
  fun unregisterDeviceMaxDataSizeChangedListener_invalidListener_doNotThrowException() {
    testProtocol.unregisterDeviceMaxDataSizeChangedListener(
      "testProtocolId",
      mockMaxDataSizeChangedListener
    )
  }

  @Test
  fun unregisterDeviceMaxDataSizeChangedListener_onLastListenerUnregistered_keyCleared() {
    val testProtocolId = "testProtocolId"
    testProtocol.registerDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )

    testProtocol.unregisterDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )

    assertThat(testProtocol.maxDataSizeChangedListenerList).doesNotContainKey(testProtocolId)
  }

  @Test
  fun reset_clearListeners() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    val testSize = 20
    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)
    testProtocol.registerDeviceDisconnectedListener(testProtocolId, mockDisconnectedListener)
    testProtocol.registerDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener
    )
    testProtocol.reset()

    testProtocol.invokeDataReceivedListeners(testProtocolId, testData)
    testProtocol.invokeDeviceDisconnectedListeners(testProtocolId)
    testProtocol.invokeDeviceMaxDataSizeChangedListener(testProtocolId, testSize)

    verify(mockDisconnectedListener, never()).onDeviceDisconnected(testProtocolId)
    verify(mockDataReceivedListener, never()).onDataReceived(testProtocolId, testData)
    verify(mockMaxDataSizeChangedListener, never())
      .onDeviceMaxDataSizeChanged(testProtocolId, testSize)
  }

  @Test
  fun reset_clearMissedData() {
    val testProtocolId = "testProtocolId"
    val testData = "testData".toByteArray()
    testProtocol.notifyDataReceived(testProtocolId, testData)
    testProtocol.reset()

    testProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)

    verify(mockDataReceivedListener, never()).onDataReceived(testProtocolId, testData)
  }

  class TestConnectionProtocol : ConnectionProtocol() {
    override fun isDeviceVerificationRequired() = false

    val deviceDisconnectedListenerList = deviceDisconnectedListeners

    val dataReceivedListenerList = dataReceivedListeners

    val maxDataSizeChangedListenerList = maxDataSizeChangedListeners

    override fun startAssociationDiscovery(
      name: String,
      identifier: ParcelUuid,
      callback: IDiscoveryCallback,
    ) {}

    override fun startConnectionDiscovery(
      id: ParcelUuid,
      challenge: ConnectChallenge,
      callback: IDiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: ParcelUuid) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }

    fun invokeDataReceivedListeners(protocolId: String, data: ByteArray) {
      dataReceivedListeners[protocolId]?.invoke { it.onDataReceived(protocolId, data) }
    }

    fun invokeDeviceDisconnectedListeners(protocolId: String) {
      deviceDisconnectedListeners[protocolId]?.invoke { it.onDeviceDisconnected(protocolId) }
    }

    fun invokeDeviceMaxDataSizeChangedListener(protocolId: String, maxBytes: Int) {
      maxDataSizeChangedListeners[protocolId]?.invoke {
        it.onDeviceMaxDataSizeChanged(protocolId, maxBytes)
      }
    }
  }
}
