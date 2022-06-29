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

package com.google.android.companiondevicesupport.eap

import android.os.IBinder
import android.os.IInterface
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.nhaarman.mockitokotlin2.whenever
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
    EapProtocol(TEST_EAP_CLIENT_NAME, TEST_EAP_SERVICE_NAME, TEST_MAX_WRITE_SIZE_BYTES)

  @Test
  fun onEapSessionStartWithValidProtocolName_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      TEST_EAP_PROTOCOL_NAME
    )

    verify(mockDiscoveryCallback).onDeviceConnected(TEST_PROTOCOL_ID.toString())
  }

  @Test
  fun onEapSessionStartWithInValidProtocolName_doesNotInvokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      "invalidProtocolName"
    )

    verify(mockDiscoveryCallback, never()).onDeviceConnected(TEST_PROTOCOL_ID.toString())
  }

  @Test
  fun onEapSessionStop_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )
    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      TEST_EAP_PROTOCOL_NAME
    )
    eapProtocol.registerDeviceDisconnectedListener(
      TEST_PROTOCOL_ID.toString(),
      mockDisconnectedListener
    )

    deviceConnectionDelegate.OnEAPSessionStop(mockDeviceConnection, TEST_PROTOCOL_ID)

    verify(mockDisconnectedListener).onDeviceDisconnected(TEST_PROTOCOL_ID.toString())
  }

  @Test
  fun onEapData_invokeCallback() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.registerDataReceivedListener(TEST_PROTOCOL_ID.toString(), mockDataReceivedListener)

    deviceConnectionDelegate.OnEAPData(mockDeviceConnection, TEST_PROTOCOL_ID, TEST_MESSAGE)

    verify(mockDataReceivedListener).onDataReceived(TEST_PROTOCOL_ID.toString(), TEST_MESSAGE)
  }

  @Test
  fun sendData_sendDataViaConnection() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )
    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      TEST_EAP_PROTOCOL_NAME
    )

    eapProtocol.sendData(TEST_PROTOCOL_ID.toString(), TEST_MESSAGE, callback = null)

    verify(mockDeviceConnection).SendEAPData(TEST_PROTOCOL_ID, TEST_MESSAGE)
  }

  @Test
  fun stopAssociationDiscovery_doesNotInvokeCallbackOnDeviceConnected() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )

    eapProtocol.stopAssociationDiscovery()

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      TEST_EAP_PROTOCOL_NAME
    )
    verify(mockDiscoveryCallback, never()).onDeviceConnected(TEST_PROTOCOL_ID.toString())
  }

  @Test
  fun reset_clearDiscoveries() {
    val deviceConnectionDelegate = captureDeviceCallback()
    eapProtocol.startAssociationDiscovery(
      TEST_EAP_PROTOCOL_NAME,
      TEST_IDENTIFIER,
      mockDiscoveryCallback
    )

    eapProtocol.reset()

    deviceConnectionDelegate.OnEAPSessionStart(
      mockDeviceConnection,
      TEST_PROTOCOL_ID,
      TEST_EAP_PROTOCOL_NAME
    )
    verify(mockDiscoveryCallback, never()).onDeviceConnected(TEST_PROTOCOL_ID.toString())
  }

  @Test
  fun getMaxWriteSize_returnCorrectValue() {
    assertThat(eapProtocol.getMaxWriteSize(TEST_PROTOCOL_ID.toString()))
      .isEqualTo(TEST_MAX_WRITE_SIZE_BYTES)
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

  /** Returns a mock of the [IInterface] `T` with a binder that is alive. */
  private inline fun <reified T> mockToBeAlive(): T where T : IInterface {
    val mockInterface: T = mock()
    val mockBinder: IBinder = mock()
    whenever(mockInterface.asBinder()).thenReturn(mockBinder)
    whenever(mockBinder.isBinderAlive).thenReturn(true)
    return mockInterface
  }

  companion object {
    private const val TEST_EAP_CLIENT_NAME = "eapClientName"
    private const val TEST_EAP_SERVICE_NAME = "eapServiceName"
    private const val TEST_EAP_PROTOCOL_NAME = "eapProtocolName"
    private const val TEST_MAX_WRITE_SIZE_BYTES = 100
    private val TEST_IDENTIFIER = ParcelUuid(UUID.randomUUID())
    private val TEST_PROTOCOL_ID = Random.nextLong()
    private val TEST_MESSAGE = "TestMessage".toByteArray()
  }
}
