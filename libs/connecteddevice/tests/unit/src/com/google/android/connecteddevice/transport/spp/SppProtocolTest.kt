/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.transport.spp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.ParcelUuid
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnErrorListener
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder.OnMessageReceivedListener
import com.google.android.connecteddevice.transport.spp.PendingConnection.OnConnectedListener
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SppProtocolTest {
  private val mockPendingConnection = mock<PendingConnection>()
  private val mockDiscoveryCallback = mockToBeAlive<IDiscoveryCallback>()
  private val mockDataSendCallback = mockToBeAlive<IDataSendCallback>()
  private val mockDisconnectedListener = mockToBeAlive<IDeviceDisconnectedListener>()
  private val mockDataReceivedListener = mockToBeAlive<IDataReceivedListener>()
  private val mockSppBinder =
    mock<ConnectedDeviceSppDelegateBinder> {
      on { connectAsServer(any(), any()) } doReturn mockPendingConnection
    }
  private val invalidMockSppBinder = mock<ConnectedDeviceSppDelegateBinder>()
  private val testMaxSize = 0
  private val testIdentifier = ParcelUuid(UUID.randomUUID())
  private val testProtocolId = UUID.randomUUID()
  private val testName = "TestName"
  private val testMessage = "TestMessage".toByteArray()
  private val testChallenge =
    ConnectChallenge("TestChallenge".toByteArray(), "TestSalt".toByteArray())
  private val sppProtocol = SppProtocol(mockSppBinder, testMaxSize, directExecutor())
  private val invalidSppProtocol = SppProtocol(invalidMockSppBinder, testMaxSize)

  @Test
  fun startAssociationDiscovery_startedSuccessfully() {
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)

    verify(mockSppBinder).connectAsServer(eq(testIdentifier.uuid), any())
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun startAssociationDiscovery_startsWithIdentifier() {
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)

    verify(mockSppBinder).connectAsServer(eq(testIdentifier.uuid), any())
  }

  @Test
  fun startAssociationDiscovery_startedFailed() {
    invalidSppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)

    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun startConnectionDiscovery_startedSuccessfully() {
    sppProtocol.startConnectionDiscovery(testIdentifier, testChallenge, mockDiscoveryCallback)

    verify(mockSppBinder).connectAsServer(eq(testIdentifier.uuid), any())
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun stopAssociationDiscovery_stopSuccessfully() {
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)

    sppProtocol.stopAssociationDiscovery()

    verify(mockSppBinder).cancelConnectionAttempt(mockPendingConnection)
  }

  @Test
  fun stopConnectionDiscovery_stopSuccessfully() {
    sppProtocol.startConnectionDiscovery(testIdentifier, testChallenge, mockDiscoveryCallback)
    sppProtocol.stopConnectionDiscovery(testIdentifier)

    verify(mockSppBinder).cancelConnectionAttempt(mockPendingConnection)
  }

  @Test
  fun sendData_sendSuccessfully() {
    val protocolId = establishConnection()

    sppProtocol.sendData(protocolId, testMessage, mockDataSendCallback)

    verify(mockSppBinder).sendMessage(any(), eq(testMessage))
    verify(mockDataSendCallback).onDataSentSuccessfully()
  }

  @Test
  fun sendData_sendFailed() {
    sppProtocol.sendData(testProtocolId.toString(), testMessage, mockDataSendCallback)

    verify(mockSppBinder, never()).sendMessage(any(), eq(testMessage))
    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun disconnectDevice_disconnectSuccessfully() {
    val protocolId = establishConnection()

    sppProtocol.disconnectDevice(protocolId)

    verify(mockSppBinder).disconnect(any())
  }

  @Test
  fun reset_cancelConnectionAttempt() {
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)
    sppProtocol.reset()

    verify(mockSppBinder).cancelConnectionAttempt(mockPendingConnection)
  }

  @Test
  fun reset_disconnectDevice() {
    establishConnection()
    sppProtocol.reset()

    verify(mockSppBinder).disconnect(any())
  }

  @Test
  fun getMaxWriteSize_startedSuccessfully() {
    assertThat(sppProtocol.getMaxWriteSize(testProtocolId.toString())).isEqualTo(testMaxSize)
  }

  @Test
  fun onMessageReceived_informCallback() {
    val protocolId = establishConnection()
    sppProtocol.registerDataReceivedListener(protocolId, mockDataReceivedListener)

    argumentCaptor<OnMessageReceivedListener>().apply {
      verify(mockSppBinder).setOnMessageReceivedListener(any(), capture())
      firstValue.onMessageReceived(testMessage)
    }

    verify(mockDataReceivedListener).onDataReceived(protocolId, testMessage)
  }

  @Test
  fun onDeviceConnected_registersConnectionCallbackForServiceUuid() {
    establishConnection()

    argumentCaptor<UUID>().apply {
      verify(mockSppBinder).registerConnectionCallback(capture(), any())
      assertThat(firstValue).isEqualTo(testIdentifier.uuid)
    }
  }

  @Test
  fun onDeviceDisconnected_informCallback() {
    val protocolId = establishConnection()
    sppProtocol.registerDeviceDisconnectedListener(protocolId, mockDisconnectedListener)

    val connection =
      argumentCaptor<Connection>()
        .apply { verify(mockSppBinder).setOnMessageReceivedListener(capture(), any()) }
        .firstValue

    argumentCaptor<OnErrorListener>().apply {
      verify(mockSppBinder).registerConnectionCallback(any(), capture())
      firstValue.onError(connection)
    }

    verify(mockDisconnectedListener).onDeviceDisconnected(protocolId)
  }

  @Test
  fun startDiscovery_throwException_startDiscoveryFailed() {
    doThrow(RemoteException()).`when`(mockSppBinder).connectAsServer(any(), any())
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)

    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun sendData_throwException_onDataFailedToSend() {
    doThrow(RemoteException()).`when`(mockSppBinder).sendMessage(any(), any())
    val protocolId = establishConnection()
    sppProtocol.sendData(protocolId, testMessage, mockDataSendCallback)

    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun getBluetoothDevice_returnNonNullDeviceWithCorrectProtocolId() {
    val protocolId = establishConnection()
    assertThat(sppProtocol.getBluetoothDeviceById(protocolId)).isNotNull()
  }

  @Test
  fun getBluetoothDevice_returnNullWhenDeviceDisconnected() {
    val protocolId = establishConnection()
    val connection =
      argumentCaptor<Connection>()
        .apply { verify(mockSppBinder).setOnMessageReceivedListener(capture(), any()) }
        .firstValue

    argumentCaptor<OnErrorListener>().apply {
      verify(mockSppBinder).registerConnectionCallback(any(), capture())
      firstValue.onError(connection)
    }

    assertThat(sppProtocol.getBluetoothDeviceById(protocolId)).isNull()
  }

  @Test
  fun getBluetoothDevice_returnNullWithIncorrectFormatProtocolId() {
    establishConnection()
    assertThat(sppProtocol.getBluetoothDeviceById("RandomProtocolId")).isNull()
  }

  @Test
  fun getBluetoothDevice_returnNullWhenProtocolIdMismatch() {
    establishConnection()
    assertThat(sppProtocol.getBluetoothDeviceById(UUID.randomUUID().toString())).isNull()
  }

  private fun establishConnection(): String {
    val testMacAddress = "00:11:22:33:AA:BB"
    val bluetoothManager =
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(BluetoothManager::class.java)
    val testBluetoothDevice = bluetoothManager.adapter.getRemoteDevice(testMacAddress)
    sppProtocol.startAssociationDiscovery(testName, testIdentifier, mockDiscoveryCallback)
    argumentCaptor<OnConnectedListener>().apply {
      verify(mockPendingConnection).setOnConnectedListener(capture())
      firstValue.onConnected(testIdentifier.uuid, testBluetoothDevice, false, testName)
    }
    return argumentCaptor<String>()
      .apply { verify(mockDiscoveryCallback).onDeviceConnected(capture()) }
      .firstValue
  }
}
