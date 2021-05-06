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

import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.transport.ConnectionProtocol.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol.DataSendCallback
import com.google.android.connecteddevice.transport.ConnectionProtocol.DeviceCallback
import com.google.android.connecteddevice.transport.ConnectionProtocol.DiscoveryCallback
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
  private val mockPendingConnection: PendingConnection = mock()
  private val mockDiscoveryCallback: DiscoveryCallback = mock()
  private val mockDataSendCallback: DataSendCallback = mock()
  private val mockDeviceCallback: DeviceCallback = mock()
  private val mockSppBinder: ConnectedDeviceSppDelegateBinder = mock {
    on { connectAsServer(any(), any()) } doReturn mockPendingConnection
  }
  private val invalidMockSppBinder: ConnectedDeviceSppDelegateBinder = mock()
  private val testMaxSize = 0
  private val testServiceUuid = UUID.randomUUID()
  private val testProtocolId = UUID.randomUUID()
  private val testName = "TestName"
  private val testMessage = "TestMessage".toByteArray()
  private val testChallenge =
    ConnectChallenge("TestChallenge".toByteArray(), "TestSalt".toByteArray())
  private val sppProtocol =
    SppProtocol(mockSppBinder, testServiceUuid, testMaxSize)
  private val invalidSppProtocol =
    SppProtocol(invalidMockSppBinder, testServiceUuid, testMaxSize)

  @Test
  fun startAssociationDiscovery_startedSuccessfully() {
    sppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)
    verify(mockSppBinder).connectAsServer(eq(testServiceUuid), any())
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun startAssociationDiscovery_startedFailed() {
    invalidSppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)

    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun startConnectionDiscovery_startedSuccessfully() {
    val testDeviceId = UUID.randomUUID()
    sppProtocol.startConnectionDiscovery(testDeviceId, testChallenge, mockDiscoveryCallback)
    verify(mockSppBinder).connectAsServer(eq(testDeviceId), any())
    verify(mockDiscoveryCallback).onDiscoveryStartedSuccessfully()
  }

  @Test
  fun stopAssociationDiscovery_stopSuccessfully() {
    sppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)
    sppProtocol.stopAssociationDiscovery()
    verify(mockSppBinder).cancelConnectionAttempt(mockPendingConnection)
  }

  @Test
  fun stopConnectionDiscovery_stopSuccessfully() {
    val testDeviceId = UUID.randomUUID()
    sppProtocol.startConnectionDiscovery(testDeviceId, testChallenge, mockDiscoveryCallback)
    sppProtocol.stopConnectionDiscovery(testDeviceId)
    verify(mockSppBinder).cancelConnectionAttempt(mockPendingConnection)
  }

  @Test
  fun sendData_sendSuccessfully() {
    val protocolId = establishConnection()

    sppProtocol.sendData(protocolId!!, testMessage, mockDataSendCallback)

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

    sppProtocol.disconnectDevice(protocolId!!)

    verify(mockSppBinder).disconnect(any())
  }

  @Test
  fun reset_cancelConnectionAttempt() {
    sppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)
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
    sppProtocol.registerCallback(protocolId!!, mockDeviceCallback, directExecutor())

    argumentCaptor<OnMessageReceivedListener>().apply {
      verify(mockSppBinder).setOnMessageReceivedListener(any(), capture())
      firstValue.onMessageReceived(testMessage)
    }

    verify(mockDeviceCallback).onDataReceived(protocolId, testMessage)
  }

  @Test
  fun onDeviceDisconnected_informCallback() {
    val protocolId = establishConnection()
    sppProtocol.registerCallback(protocolId!!, mockDeviceCallback, directExecutor())

    val connection = argumentCaptor<Connection>().apply {
      verify(mockSppBinder).setOnMessageReceivedListener(capture(), any())
    }.firstValue

    argumentCaptor<OnErrorListener>().apply {
      verify(mockSppBinder).registerConnectionCallback(any(), capture())
      firstValue.onError(connection)
    }

    verify(mockDeviceCallback).onDeviceDisconnected(protocolId)
  }

  @Test
  fun startDiscovery_throwException_startDiscoveryFailed() {
    doThrow(RemoteException()).`when`(mockSppBinder).connectAsServer(any(), any())
    sppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)

    verify(mockDiscoveryCallback).onDiscoveryFailedToStart()
  }

  @Test
  fun sendData_throwException_onDataFailedToSend() {
    doThrow(RemoteException()).`when`(mockSppBinder).sendMessage(any(), any())
    val protocolId = establishConnection()
    sppProtocol.sendData(protocolId!!, testMessage, mockDataSendCallback)

    verify(mockDataSendCallback).onDataFailedToSend()
  }

  private fun establishConnection(): String? {
    sppProtocol.startAssociationDiscovery(testName, mockDiscoveryCallback)
    argumentCaptor<OnConnectedListener>().apply {
      verify(mockPendingConnection).setOnConnectedListener(capture())
      firstValue.onConnected(testServiceUuid, any(), any(), testName)
    }
    return argumentCaptor<String>().apply {
      verify(mockDiscoveryCallback).onDeviceConnected(capture())
    }.firstValue
  }
}
