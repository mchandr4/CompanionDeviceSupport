/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.connecteddevice.transport.eap

import android.os.IBinder
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.panasonic.iapx.IDeviceConnection
import com.panasonic.iapx.IDeviceConnectionDelegate
import com.panasonic.iapx.IServiceConnector
import java.util.UUID
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EapProtocolTest {
  private val mockServiceConnector = mock<IServiceConnector>()
  private val mockDeviceConnection = mock<IDeviceConnection>()
  private val mockDataReceivedListener = mockToBeAlive<IDataReceivedListener>()
  private val mockDiscoveryCallback = mockToBeAlive<IDiscoveryCallback>()
  private val mockDisconnectedListener = mockToBeAlive<IDeviceDisconnectedListener>()
  private val eapProtocol =
    EapProtocol(TEST_EAP_CLIENT_NAME, TEST_EAP_SERVICE_NAME, TEST_MAX_WRITE_SIZE)

  @Test
  fun OnEAPSessionStartWithValidProtocolName_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      TEST_EAP_PROTOCOL_NAME
    )

    verify(mockDiscoveryCallback).onDeviceConnected(testProtocolId.toString())
  }

  @Test
  fun OnEAPSessionStartWithInValidProtocolName_doesNotInvokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      "invalidProtocolName"
    )

    verify(mockDiscoveryCallback, never()).onDeviceConnected(testProtocolId.toString())
  }

  @Test
  fun OnEAPSessionStop_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )
    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      TEST_EAP_PROTOCOL_NAME
    )
    eapProtocol.registerDeviceDisconnectedListener(
      testProtocolId.toString(),
      mockDisconnectedListener
    )

    deviceConnectionDelegate.OnEAPSessionStop(mockDeviceConnection, testProtocolId)

    verify(mockDisconnectedListener).onDeviceDisconnected(testProtocolId.toString())
  }

  @Test
  fun OnEAPData_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.registerDataReceivedListener(testProtocolId.toString(), mockDataReceivedListener)

    deviceConnectionDelegate.OnEAPData(mockDeviceConnection, testProtocolId, testMessage)

    verify(mockDataReceivedListener).onDataReceived(testProtocolId.toString(), testMessage)
  }

  @Test
  fun sendData_sendDataViaConnection() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )
    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      TEST_EAP_PROTOCOL_NAME
    )

    eapProtocol.sendData(testProtocolId.toString(), testMessage, callback = null)

    verify(mockDeviceConnection).SendEAPData(testProtocolId, testMessage)
  }

  @Test
  fun stopAssociationDiscovery_doesNotInvokeCallbackOnDeviceConnected() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    eapProtocol.stopAssociationDiscovery()

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      TEST_EAP_PROTOCOL_NAME
    )
    verify(mockDiscoveryCallback, never()).onDeviceConnected(testProtocolId.toString())
  }

  @Test
  fun reset_clearDiscoveries() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      testIdentifier,
      mockDiscoveryCallback
    )

    eapProtocol.reset()

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      testProtocolId,
      TEST_EAP_PROTOCOL_NAME
    )
    verify(mockDiscoveryCallback, never()).onDeviceConnected(testProtocolId.toString())
  }

  @Test
  fun getMaxWriteSize_returnCorrectValue() {
    assertThat(eapProtocol.getMaxWriteSize(testProtocolId.toString()))
      .isEqualTo(TEST_MAX_WRITE_SIZE)
  }

  private fun captureDeviceCallback(): IDeviceConnectionDelegate {
    eapProtocol.connectClient(mockServiceConnector)
    val deviceDelegateBinder =
      argumentCaptor<IBinder>()
        .apply {
          verify(mockServiceConnector).ConnectClient(eq(TEST_EAP_CLIENT_NAME), any(), capture())
        }
        .firstValue
    return IDeviceConnectionDelegate.Stub.asInterface(deviceDelegateBinder)
  }
  companion object {
    private const val TEST_EAP_CLIENT_NAME = "eapClientName"
    private const val TEST_EAP_SERVICE_NAME = "eapServiceName"
    private const val TEST_EAP_PROTOCOL_NAME = "eapProtocolName"
    private const val TEST_MAX_WRITE_SIZE = 100
    private val testIdentifier = ParcelUuid(UUID.randomUUID())
    private val testProtocolId = Random.nextLong()
    private val testMessage = "TestMessage".toByteArray()
  }
}
