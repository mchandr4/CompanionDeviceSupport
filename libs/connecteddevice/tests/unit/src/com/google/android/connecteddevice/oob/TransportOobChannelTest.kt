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

package com.google.android.connecteddevice.oob

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.IConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransportOobChannelTest {
  private val testConnectionProtocol = mockToBeAlive<IConnectionProtocol>()
  private lateinit var transportOobChannel: OobChannel
  private val protocolDelegate = ProtocolDelegate().apply { addOobProtocol(testConnectionProtocol) }

  @Before
  fun setUp() {
    transportOobChannel = TransportOobChannel(protocolDelegate, TEST_PROTOCOL_NAME)
  }

  @Test
  fun completeOobDataExchange_startProtocolDiscovery() {
    transportOobChannel.completeOobDataExchange(testMessage)

    verify(testConnectionProtocol).startAssociationDiscovery(eq(TEST_PROTOCOL_NAME), any(), any())
  }

  @Test
  fun onDeviceConnected_sendOobData() {
    transportOobChannel.completeOobDataExchange(testMessage)
    with(argumentCaptor<IDiscoveryCallback>()) {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_PROTOCOL_NAME), any(), capture())
      firstValue.onDeviceConnected(TEST_PROTOCOL_ID)
    }
    verify(testConnectionProtocol).sendData(eq(TEST_PROTOCOL_ID), eq(testMessage), any())
  }

  @Test
  fun onDataFailedToSend_disconnectDevice() {
    transportOobChannel.completeOobDataExchange(testMessage)
    with(argumentCaptor<IDiscoveryCallback>()) {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_PROTOCOL_NAME), any(), capture())
      firstValue.onDeviceConnected(TEST_PROTOCOL_ID)
    }
    with(argumentCaptor<IDataSendCallback>()) {
      verify(testConnectionProtocol).sendData(eq(TEST_PROTOCOL_ID), eq(testMessage), capture())
      firstValue.onDataFailedToSend()
    }
    verify(testConnectionProtocol).disconnectDevice(TEST_PROTOCOL_ID)
  }

  @Test
  fun interrupt_disconnectOngoingConnection() {
    transportOobChannel.completeOobDataExchange(testMessage)
    with(argumentCaptor<IDiscoveryCallback>()) {
      verify(testConnectionProtocol)
        .startAssociationDiscovery(eq(TEST_PROTOCOL_NAME), any(), capture())
      firstValue.onDeviceConnected(TEST_PROTOCOL_ID)
    }

    transportOobChannel.interrupt()

    verify(testConnectionProtocol).disconnectDevice(TEST_PROTOCOL_ID)
  }

  companion object {
    private const val TEST_PROTOCOL_ID = "testProtocolId"
    private const val TEST_PROTOCOL_NAME = "testProtocolName"
    private val testMessage = "testMessage".toByteArray()
  }
}
