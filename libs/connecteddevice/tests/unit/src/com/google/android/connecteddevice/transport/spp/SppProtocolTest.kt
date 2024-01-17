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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SppProtocolTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testBluetoothDevice =
    context
      .getSystemService(BluetoothManager::class.java)
      .adapter
      .getRemoteDevice("00:11:22:33:AA:BB")
  private val mockDiscoveryCallback = mockToBeAlive<IDiscoveryCallback>()
  private val mockDataSendCallback = mockToBeAlive<IDataSendCallback>()
  private val mockDisconnectedListener = mockToBeAlive<IDeviceDisconnectedListener>()
  private val mockDataReceivedListener = mockToBeAlive<IDataReceivedListener>()
  private val mockSppManager = mock<SppManager>()
  private val testMaxSize = 0
  private val testIdentifier = UUID.randomUUID()
  private val testProtocolId = UUID.randomUUID()
  private val testMessage = "TestMessage".toByteArray()
  private val sppProtocol = SppProtocol(context, testMaxSize)

  @Test
  fun startConnection_startSuccessfully() {
    whenever(mockSppManager.startListening(any())).thenReturn(true)
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)

    verify(mockSppManager).startListening(testIdentifier)
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun startAssociationDiscovery_startedFailed() {
    whenever(mockSppManager.startListening(any())).thenReturn(false)
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)

    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun stopAssociationDiscovery_stopSuccessfully() {
    whenever(mockSppManager.startListening(any())).thenReturn(true)
    sppProtocol.associationIdentifier = testIdentifier
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)

    sppProtocol.stopAssociationDiscovery()

    verify(mockSppManager).cleanup()
  }

  @Test
  fun stopConnectionDiscovery_stopSuccessfully() {
    whenever(mockSppManager.startListening(any())).thenReturn(true)
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)
    sppProtocol.stopConnectionDiscovery(ParcelUuid(testIdentifier))

    verify(mockSppManager).cleanup()
  }

  @Test
  fun sendData_sendSuccessfully() {
    whenever(mockSppManager.write(any(), any())).thenReturn(true)
    val protocolId = establishConnection()

    sppProtocol.sendData(protocolId, testMessage, mockDataSendCallback)

    with(argumentCaptor<PendingSentMessage>()) {
      verify(mockSppManager).write(any(), capture())
      firstValue.notifyMessageSent()
    }
    verify(mockDataSendCallback).onDataSentSuccessfully()
  }

  @Test
  fun sendData_noConnection_sendFailed() {
    sppProtocol.sendData(testProtocolId.toString(), testMessage, mockDataSendCallback)

    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun sendData_withConnection_sendFailed() {
    whenever(mockSppManager.write(any(), any())).thenReturn(false)
    val protocolId = establishConnection()

    sppProtocol.sendData(protocolId, testMessage, mockDataSendCallback)

    verify(mockSppManager).write(any(), any())
    verify(mockDataSendCallback).onDataFailedToSend()
  }

  @Test
  fun disconnectDevice_disconnectSuccessfully() {
    val protocolId = establishConnection()

    sppProtocol.disconnectDevice(protocolId)

    verify(mockSppManager).cleanup()
  }

  @Test
  fun reset_cleanupPendingDiscovery() {
    whenever(mockSppManager.startListening(any())).thenReturn(true)
    sppProtocol.associationIdentifier = testIdentifier
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)
    sppProtocol.reset()

    verify(mockSppManager).cleanup()
    assertThat(sppProtocol.associationIdentifier).isNull()
  }

  @Test
  fun reset_cleanupConnections() {
    establishConnection()
    sppProtocol.reset()

    verify(mockSppManager).cleanup()
  }

  @Test
  fun getMaxWriteSize_startedSuccessfully() {
    assertThat(sppProtocol.getMaxWriteSize(testProtocolId.toString())).isEqualTo(testMaxSize)
  }

  @Test
  fun onMessageReceived_informCallback() {
    val protocolId = establishConnection()
    sppProtocol.registerDataReceivedListener(protocolId, mockDataReceivedListener)

    with(argumentCaptor<SppManager.OnMessageReceivedListener>()) {
      verify(mockSppManager).addOnMessageReceivedListener(capture(), any())
      firstValue.onMessageReceived(testBluetoothDevice, testMessage)
    }

    verify(mockDataReceivedListener).onDataReceived(protocolId, testMessage)
  }

  @Test
  fun onDeviceDisconnected_informCallback() {
    val protocolId = establishConnection()
    sppProtocol.registerDeviceDisconnectedListener(protocolId, mockDisconnectedListener)

    with(argumentCaptor<SppManager.ConnectionCallback>()) {
      verify(mockSppManager).registerCallback(capture(), any())
      firstValue.onRemoteDeviceDisconnected(testBluetoothDevice)
    }

    verify(mockDisconnectedListener).onDeviceDisconnected(protocolId)
  }

  private fun establishConnection(): String {
    sppProtocol.startConnection(testIdentifier, mockDiscoveryCallback, mockSppManager)
    with(argumentCaptor<SppManager.ConnectionCallback>()) {
      verify(mockSppManager).registerCallback(capture(), any())
      firstValue.onRemoteDeviceConnected(testBluetoothDevice)
    }
    return argumentCaptor<String>()
      .apply { verify(mockDiscoveryCallback).onDeviceConnected(capture()) }
      .firstValue
  }
}
