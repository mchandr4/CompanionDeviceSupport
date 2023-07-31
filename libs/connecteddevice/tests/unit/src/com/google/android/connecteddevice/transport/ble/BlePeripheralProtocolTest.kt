package com.google.android.connecteddevice.transport.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.content.Context
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.time.Duration
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val TEST_REMOTE_DEVICE_ADDRESS = "00:11:22:33:AA:BB"
private const val TEST_ALTERNATIVE_REMOTE_DEVICE_ADDRESS = "00:11:22:33:AA:CC"
private const val TEST_DEFAULT_MTU_SIZE = 23
private const val TEST_DEVICE_NAME = "TestName"

@RunWith(AndroidJUnit4::class)
class BlePeripheralProtocolTest {
  private val testIdentifier = ParcelUuid(UUID.randomUUID())
  private val testReconnectServiceUuid = UUID.randomUUID()
  private val testReconnectDataUuid = UUID.randomUUID()
  private val testAdvertiseDataCharacteristicUuid = UUID.randomUUID()
  private val testWriteCharacteristicUuid = UUID.randomUUID()
  private val testReadCharacteristicUuid = UUID.randomUUID()
  private val testServiceChangedCharacteristicUuid = UUID.randomUUID()
  private val testMaxReconnectAdvertisementDuration = Duration.ofMinutes(6)
  private val testMessage = "TestMessage".toByteArray()
  private val testChallenge =
    ConnectChallenge("TestChallenge".toByteArray(), "TestSalt".toByteArray())

  private val mockBlePeripheralManager = mock<BlePeripheralManager>()
  private val mockDiscoveryCallback = mockToBeAlive<IDiscoveryCallback>()
  private val mockDataSendCallback = mockToBeAlive<IDataSendCallback>()
  private val mockSecondDataSendCallback = mockToBeAlive<IDataSendCallback>()
  private val mockDisconnectedListener = mockToBeAlive<IDeviceDisconnectedListener>()
  private val mockDataReceivedListener = mockToBeAlive<IDataReceivedListener>()
  private val mockMaxDataSizeChangedListener = mockToBeAlive<IDeviceMaxDataSizeChangedListener>()

  private lateinit var blePeripheralProtocol: BlePeripheralProtocol
  private lateinit var testBluetoothDevice: BluetoothDevice
  private lateinit var unknownBluetoothDevice: BluetoothDevice

  @Before
  fun setUp() {
    val bluetoothManager =
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(BluetoothManager::class.java)
    testBluetoothDevice = bluetoothManager.adapter.getRemoteDevice(TEST_REMOTE_DEVICE_ADDRESS)
    unknownBluetoothDevice =
      bluetoothManager.adapter.getRemoteDevice(TEST_ALTERNATIVE_REMOTE_DEVICE_ADDRESS)
    blePeripheralProtocol =
      BlePeripheralProtocol(
        mockBlePeripheralManager,
        testReconnectServiceUuid,
        testReconnectDataUuid,
        testAdvertiseDataCharacteristicUuid,
        testWriteCharacteristicUuid,
        testReadCharacteristicUuid,
        testServiceChangedCharacteristicUuid,
        testMaxReconnectAdvertisementDuration,
        TEST_DEFAULT_MTU_SIZE,
        directExecutor()
      )
  }

  @Test
  fun startAssociationDiscovery_startsWithIdentifier() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    argumentCaptor<BluetoothGattService>().apply {
      verify(mockBlePeripheralManager).startAdvertising(capture(), any(), any(), any())
      assertThat(firstValue.uuid).isEqualTo(testIdentifier.uuid)
    }
  }

  @Test
  fun startAssociationDiscovery_doNotReset() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    verify(mockBlePeripheralManager, never()).cleanup()
  }

  @Test
  fun startAssociationDiscovery_startedSuccessfully() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
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
      testIdentifier,
      mockDiscoveryCallback
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
      testIdentifier,
      mockDiscoveryCallback
    )
    // Discovery can be started even when there is a connection. This could be cause by a late
    // disconnect callback.
    verify(mockBlePeripheralManager, times(2)).startAdvertising(any(), any(), any(), any())
  }

  @Test
  fun startAssociationDiscovery_multipleTimesSucceed() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )
    argumentCaptor<AdvertiseCallback>().apply {
      verify(mockBlePeripheralManager, atLeastOnce())
        .startAdvertising(any(), any(), any(), capture())
      firstValue.onStartSuccess(/* settingsInEffect= */ null)
    }
    blePeripheralProtocol.stopAssociationDiscovery()
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
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
      testIdentifier,
      mockDiscoveryCallback
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
    blePeripheralProtocol.startConnectionDiscovery(
      testIdentifier,
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
    blePeripheralProtocol.startConnectionDiscovery(
      testIdentifier,
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
      testIdentifier,
      testChallenge,
      mockDiscoveryCallback
    )
    // Discovery can be started even when there is a connection. This could be cause by a late
    // disconnect callback.
    verify(mockBlePeripheralManager, times(2)).startAdvertising(any(), any(), any(), any())
  }

  @Test
  fun stopConnectionDiscovery_stopSuccessfully() {
    blePeripheralProtocol.startConnectionDiscovery(
      testIdentifier,
      testChallenge,
      mockDiscoveryCallback
    )
    blePeripheralProtocol.stopConnectionDiscovery(testIdentifier)
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
  fun disconnectDevice_disconnect() {
    val protocolId = establishConnection(testBluetoothDevice)

    blePeripheralProtocol.disconnectDevice(protocolId)

    verify(mockBlePeripheralManager).disconnect()
  }

  @Test
  fun disconnectDevice_unknownProtocolId_ignore() {
    establishConnection(testBluetoothDevice)

    blePeripheralProtocol.disconnectDevice("unknownId")

    verify(mockBlePeripheralManager, never()).disconnect()
  }

  @Test
  fun disconnectDevice_onDeviceDisconnected_invokeListener() {
    val protocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDeviceDisconnectedListener(protocolId, mockDisconnectedListener)

    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onRemoteDeviceDisconnected(testBluetoothDevice)
    }

    verify(mockDisconnectedListener).onDeviceDisconnected(eq(protocolId))
  }

  @Test
  fun onDeviceDisconnected_unknownDevice_ignore() {
    val protocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDeviceDisconnectedListener(protocolId, mockDisconnectedListener)

    argumentCaptor<BlePeripheralManager.Callback>().apply {
      verify(mockBlePeripheralManager).registerCallback(capture())
      firstValue.onRemoteDeviceDisconnected(unknownBluetoothDevice)
    }

    verify(mockDisconnectedListener, never()).onDeviceDisconnected(any())
  }

  @Test
  fun onDeviceDisconnected_removeListeners() {
    val protocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDeviceDisconnectedListener(protocolId, mockDisconnectedListener)

    val managerCallback =
      argumentCaptor<BlePeripheralManager.Callback>()
        .apply { verify(mockBlePeripheralManager).registerCallback(capture()) }
        .firstValue
    val testMtuSize = 20
    blePeripheralProtocol.registerDeviceMaxDataSizeChangedListener(
      protocolId,
      mockMaxDataSizeChangedListener
    )

    managerCallback.onRemoteDeviceDisconnected(testBluetoothDevice)
    managerCallback.onMtuSizeChanged(testMtuSize)

    verify(mockMaxDataSizeChangedListener, never())
      .onDeviceMaxDataSizeChanged(
        eq(protocolId),
        eq(testMtuSize - BlePeripheralProtocol.ATT_PROTOCOL_BYTES)
      )
  }

  @Test
  fun reset_stopStartedAdvertising() {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
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
    blePeripheralProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)
    argumentCaptor<BlePeripheralManager.OnCharacteristicWriteListener>().apply {
      verify(mockBlePeripheralManager).addOnCharacteristicWriteListener(capture())
      firstValue.onCharacteristicWrite(testBluetoothDevice, testReadCharacteristic, testMessage)
    }
    verify(mockDataReceivedListener).onDataReceived(testProtocolId, testMessage)
  }

  @Test
  fun onMessageReceived_unknownDevice_disconnect() {
    val testReadCharacteristic =
      BluetoothGattCharacteristic(
        testReadCharacteristicUuid,
        BluetoothGattCharacteristic.PROPERTY_WRITE or
          BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
      )
    val testProtocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDataReceivedListener(testProtocolId, mockDataReceivedListener)
    argumentCaptor<BlePeripheralManager.OnCharacteristicWriteListener>().apply {
      verify(mockBlePeripheralManager).addOnCharacteristicWriteListener(capture())
      firstValue.onCharacteristicWrite(unknownBluetoothDevice, testReadCharacteristic, testMessage)
    }
    verify(mockBlePeripheralManager).disconnect()
  }

  @Test
  fun onDeviceDisconnected_callbackInvoked() {
    val testProtocolId = establishConnection(testBluetoothDevice)
    blePeripheralProtocol.registerDeviceDisconnectedListener(
      testProtocolId,
      mockDisconnectedListener
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
      mockMaxDataSizeChangedListener
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

  private fun establishConnection(bluetoothDevice: BluetoothDevice): String {
    blePeripheralProtocol.startAssociationDiscovery(
      TEST_DEVICE_NAME,
      testIdentifier,
      mockDiscoveryCallback
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
