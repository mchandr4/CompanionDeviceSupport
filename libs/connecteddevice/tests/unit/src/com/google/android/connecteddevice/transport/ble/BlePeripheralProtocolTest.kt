package com.google.android.connecteddevice.transport.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.transport.ConnectionProtocol.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol.DataReceivedListener
import com.google.android.connecteddevice.transport.ConnectionProtocol.DataSendCallback
import com.google.android.connecteddevice.transport.ConnectionProtocol.DeviceDisconnectedListener
import com.google.android.connecteddevice.transport.ConnectionProtocol.DeviceMaxDataSizeChangedListener
import com.google.android.connecteddevice.transport.ConnectionProtocol.DiscoveryCallback
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import java.time.Duration
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_REMOTE_DEVICE_ADDRESS = "00:11:22:33:AA:BB"
private const val TEST_DEFAULT_MTU_SIZE = 23
private const val TEST_DEVICE_NAME = "TestName"

@RunWith(AndroidJUnit4::class)
class BlePeripheralProtocolTest {
  private val testIdentifier = UUID.randomUUID()
  private val testReconnectServiceUuid = UUID.randomUUID()
  private val testReconnectDataUuid = UUID.randomUUID()
  private val testAdvertiseDataCharacteristicUuid = UUID.randomUUID()
  private val testWriteCharacteristicUuid = UUID.randomUUID()
  private val testReadCharacteristicUuid = UUID.randomUUID()
  private val testMaxReconnectAdvertisementDuration = Duration.ofMinutes(6)
  private val testMessage = "TestMessage".toByteArray()
  private val testChallenge =
    ConnectChallenge("TestChallenge".toByteArray(), "TestSalt".toByteArray())

  private val mockBlePeripheralManager: BlePeripheralManager = mock()
  private val mockDiscoveryCallback: DiscoveryCallback = mock()
  private val mockDataSendCallback: DataSendCallback = mock()
  private val mockSecondDataSendCallback: DataSendCallback = mock()
  private val mockDisconnectedListener: DeviceDisconnectedListener = mock()
  private val mockDataReceivedListener: DataReceivedListener = mock()
  private val mockMaxDataSizeChangedListener: DeviceMaxDataSizeChangedListener = mock()

  private lateinit var blePeripheralProtocol: BlePeripheralProtocol
  private lateinit var testBluetoothDevice: BluetoothDevice

  @Before
  fun setUp() {
    val bluetoothManager =
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(BluetoothManager::class.java)
    testBluetoothDevice = bluetoothManager.adapter.getRemoteDevice(TEST_REMOTE_DEVICE_ADDRESS)
    blePeripheralProtocol =
      BlePeripheralProtocol(
        mockBlePeripheralManager,
        testReconnectServiceUuid,
        testReconnectDataUuid,
        testAdvertiseDataCharacteristicUuid,
        testWriteCharacteristicUuid,
        testReadCharacteristicUuid,
        testMaxReconnectAdvertisementDuration,
        TEST_DEFAULT_MTU_SIZE
      )
  }

  @Test
  fun startAssociationDiscovery_startsWithIdentifier() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )

    argumentCaptor<BluetoothGattService>().apply {
      verify(mockBlePeripheralManager).startAdvertising(capture(), any(), any(), any())
      assertThat(firstValue.uuid).isEqualTo(testIdentifier)
    }
  }

  @Test
  fun startAssociationDiscovery_startedSuccessfully() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture())
      firstValue.onStartSuccess(/* settingsInEffect= */ null)
    }
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun startAssociationDiscovery_failedToStart() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture())
      firstValue.onStartFailure(/* errorCode= */ 0)
    }
    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun startAssociationDiscovery_alreadyInConnection() {
    establishConnection(testBluetoothDevice)
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    // Should only be invoked once for establishing connection.
    verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), any())
  }

  @Test
  fun startAssociationDiscovery_multipleTimesSucceed() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager, atLeastOnce())
        .startAdvertising(any(), any(), any(), capture())
      firstValue.onStartSuccess(/* settingsInEffect= */ null)
    }
    blePeripheralProtocol.stopAssociationDiscovery()
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager, atLeastOnce())
        .startAdvertising(any(), any(), any(), capture())
      firstValue.onStartSuccess(/* settingsInEffect= */ null)
    }

    verify(mockBlePeripheralManager, times(2)).startAdvertising(any(), any(), any(), any())
    verify(mockBlePeripheralManager).stopAdvertising(any())
    verify(mockDiscoveryCallback, times(2)).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun stopAssociationDiscovery_stopSuccessfully() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    blePeripheralProtocol.stopAssociationDiscovery()
    val advertiseCallback =
      argumentCaptor<AdvertiseCallback>()
        .apply { verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture()) }
        .firstValue
    verify(mockBlePeripheralManager).stopAdvertising(eq(advertiseCallback))
  }

  @Test
  fun startConnectionDiscovery_startedSuccessfully() {
    val testDeviceId = UUID.randomUUID()
    blePeripheralProtocol.startConnectionDiscovery(
      testDeviceId,
      testChallenge,
      mockDiscoveryCallback
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture())
      firstValue.onStartSuccess(/* settingsInEffect= */ null)
    }
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun startConnectionDiscovery_failedToStart() {
    val testDeviceId = UUID.randomUUID()
    blePeripheralProtocol.startConnectionDiscovery(
      testDeviceId,
      testChallenge,
      mockDiscoveryCallback
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture())
      firstValue.onStartFailure(/* errorCode= */ 0)
    }
    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun startConnectionDiscovery_alreadyInConnection() {
    establishConnection(testBluetoothDevice)
    blePeripheralProtocol.startConnectionDiscovery(
      UUID.randomUUID(),
      testChallenge,
      mockDiscoveryCallback
    )
    // Should only be invoked once for establishing connection.
    verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), any())
  }

  @Test
  fun stopConnectionDiscovery_stopSuccessfully() {
    val testDeviceId = UUID.randomUUID()
    blePeripheralProtocol.startConnectionDiscovery(
      testDeviceId,
      testChallenge,
      mockDiscoveryCallback
    )
    blePeripheralProtocol.stopConnectionDiscovery(testDeviceId)
    val advertiseCallback =
      argumentCaptor<AdvertiseCallback>()
        .apply { verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture()) }
        .firstValue
    verify(mockBlePeripheralManager).stopAdvertising(eq(advertiseCallback))
  }

  @Test
  fun sendData_oneMessage_sendSuccessfully() {
    val testProtocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.sendData(testProtocolId, testMessage, mockDataSendCallback)
    val writeCharacteristic =
      argumentCaptor<BluetoothGattCharacteristic>()
        .apply {
          verify(mockBlePeripheralManager)
            .notifyCharacteristicChanged(eq(testBluetoothDevice), capture(), any())
        }
        .firstValue
    argumentCaptor<BlePeripheralManager.OnCharacteristicReadListener>().apply {
      verify(mockBlePeripheralManager).addOnCharacteristicReadListener(capture())
      firstValue.onCharacteristicRead(testBluetoothDevice)
    }
    assertThat(writeCharacteristic.uuid).isEqualTo(testWriteCharacteristicUuid)
    assertThat(writeCharacteristic.value).isEqualTo(testMessage)
    verify(mockDataSendCallback).onDataSentSuccessfully()
  }

  @Test
  fun sendData_twoMessages_invokeCallbackSuccessfully() {
    val testProtocolId = establishConnection(testBluetoothDevice)
    val onCharacteristicReadListener =
      argumentCaptor<BlePeripheralManager.OnCharacteristicReadListener>()
        .apply { verify(mockBlePeripheralManager).addOnCharacteristicReadListener(capture()) }
        .firstValue
    blePeripheralProtocol.sendData(testProtocolId, testMessage, mockDataSendCallback)
    onCharacteristicReadListener.onCharacteristicRead(testBluetoothDevice)
    blePeripheralProtocol.sendData(testProtocolId, testMessage, mockSecondDataSendCallback)
    onCharacteristicReadListener.onCharacteristicRead(testBluetoothDevice)

    verify(mockDataSendCallback).onDataSentSuccessfully()
    verify(mockSecondDataSendCallback).onDataSentSuccessfully()
  }

  @Test
  fun sendData_failedToSend_noConnectedDevice() {
    val testProtocolId = UUID.randomUUID().toString()
    blePeripheralProtocol.sendData(testProtocolId, testMessage, mockDataSendCallback)
    verify(mockBlePeripheralManager, never()).notifyCharacteristicChanged(any(), any(), any())
    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun sendData_failedToSend_dataTooLarge() {
    val largeTestMessage = ByteUtils.randomBytes(TEST_DEFAULT_MTU_SIZE + 1)
    val protocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.sendData(protocolId, largeTestMessage, mockDataSendCallback)
    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun disconnectDevice_cleanupBlePeripheralManager() {
    val protocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.disconnectDevice(protocolId)
    // First cleanup for establishing the new connection
    // Second cleanup for disconnection
    verify(mockBlePeripheralManager, times(2)).cleanup()
  }

  @Test
  fun reset_stopStartedAdvertising() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    blePeripheralProtocol.reset()
    val advertiseCallback =
      argumentCaptor<AdvertiseCallback>()
        .apply { verify(mockBlePeripheralManager).startAdvertising(any(), any(), any(), capture()) }
        .firstValue
    verify(mockBlePeripheralManager).stopAdvertising(eq(advertiseCallback))
  }

  @Test
  fun reset_cleanupBlePeripheralManager() {
    blePeripheralProtocol.reset()
    verify(mockBlePeripheralManager).cleanup()
  }

  @Test
  fun getMaxWriteSize() {
    val testProtocolId = UUID.randomUUID().toString()
    val expectedMaxWriteSize = TEST_DEFAULT_MTU_SIZE - BlePeripheralProtocol.ATT_PROTOCOL_BYTES
    assertThat(blePeripheralProtocol.getMaxWriteSize(testProtocolId))
      .isEqualTo(expectedMaxWriteSize)
  }

  @Test
  fun onMessageReceived_callbackInvoked() {
    val testReadCharacteristic =
      BluetoothGattCharacteristic(
        testReadCharacteristicUuid,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
          BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
      )
    val testProtocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDataReceivedListener(
      testProtocolId,
      mockDataReceivedListener,
      directExecutor()
    )
    argumentCaptor<BlePeripheralManager.OnCharacteristicWriteListener>().apply {
      verify(mockBlePeripheralManager).addOnCharacteristicWriteListener(capture())
      firstValue.onCharacteristicWrite(testBluetoothDevice, testReadCharacteristic, testMessage)
    }
    verify(mockDataReceivedListener).onDataReceived(testProtocolId, testMessage)
  }

  @Test
  fun onDeviceDisconnected_callbackInvoked() {
    val testProtocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDeviceDisconnectedListener(
      testProtocolId,
      mockDisconnectedListener,
      directExecutor()
    )
    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onRemoteDeviceDisconnected(testBluetoothDevice)
    }
    verify(mockDisconnectedListener).onDeviceDisconnected(eq(testProtocolId))
  }

  @Test
  fun onDeviceMaxDataSizeChanged_callbackInvoked() {
    val testProtocolId = establishConnection(testBluetoothDevice)
    val testMtuSize = 20
    blePeripheralProtocol.registerDeviceMaxDataSizeChangedListener(
      testProtocolId,
      mockMaxDataSizeChangedListener,
      directExecutor()
    )
    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onMtuSizeChanged(testMtuSize)
    }
    verify(mockMaxDataSizeChangedListener)
      .onDeviceMaxDataSizeChanged(
        eq(testProtocolId),
        eq(testMtuSize - BlePeripheralProtocol.ATT_PROTOCOL_BYTES)
      )
  }

  @Test
  fun onDeviceConnected_stopsAdvertising() {
    establishConnection(testBluetoothDevice)

    verify(mockBlePeripheralManager).stopAdvertising(any())
  }

  @Test
  fun getBluetoothDevice_returnNonNullDevice() {
    val protocolId = establishConnection(testBluetoothDevice)

    assertThat(blePeripheralProtocol.getBluetoothDeviceById(protocolId)).isNotNull()
  }

  @Test
  fun getBluetoothDevice_returnNullWhenDisconnected() {
    val protocolId = establishConnection(testBluetoothDevice)
    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onRemoteDeviceDisconnected(testBluetoothDevice)
    }

    assertThat(blePeripheralProtocol.getBluetoothDeviceById(protocolId)).isNull()
  }

  @Test
  fun getBluetoothDevice_returnNullWhenProtocolIdMismatch() {
    establishConnection(testBluetoothDevice)

    assertThat(blePeripheralProtocol.getBluetoothDeviceById("RandomProtocolId")).isNull()
  }

  private fun establishConnection(bluetoothDevice: BluetoothDevice): String {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      mockDiscoveryCallback,
      testIdentifier
    )
    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onRemoteDeviceConnected(bluetoothDevice)
    }
    return argumentCaptor<String>()
      .apply { verify(mockDiscoveryCallback).onDeviceConnected(capture()) }
      .firstValue
  }
}
