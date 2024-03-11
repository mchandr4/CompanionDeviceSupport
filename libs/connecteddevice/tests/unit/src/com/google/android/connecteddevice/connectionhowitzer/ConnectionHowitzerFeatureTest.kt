/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.connecteddevice.connectionhowitzer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.api.FakeConnector
import com.google.android.connecteddevice.connectionhowitzer.proto.HowitzerMessage
import com.google.android.connecteddevice.connectionhowitzer.proto.HowitzerMessage.MessageType
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.test.assertFailsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

/** Unit tests for the [ConnectionHowitzerFeature]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class ConnectionHowitzerFeatureTest {
  private val fakeConnector: Connector = spy(FakeConnector())

  private lateinit var howitzerFeature: ConnectionHowitzerFeature

  @Before
  fun setup() {
    howitzerFeature = ConnectionHowitzerFeature(fakeConnector)
  }

  @Test
  fun onSecureChannelEstablished_resetAfterReconnected() {
    connect()
    val config = createConfig(sendPayloadFromIhu = false, testId = TEST_ID)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())
    disconnect()

    connect()

    // First message is config ack.
    val expectedMessageCount = 1
    verifyReadyForNextTest(count = expectedMessageCount)
  }

  @Test
  fun handleConfig_populateConfigWhenReceivedFromPhone() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    assertThat(howitzerFeature.config.testId).isEqualTo(config.testId)
    assertThat(howitzerFeature.config.payloadCount).isEqualTo(config.payloadCount)
    assertThat(howitzerFeature.config.payloadSize).isEqualTo(config.payloadSize)
    assertThat(howitzerFeature.config.sendPayloadFromIhu).isEqualTo(config.sendPayloadFromIhu)
  }

  @Test
  fun handleConfig_doNotAckIfMessageTypeIsNotConfig() {
    connect()
    howitzerFeature.onMessageReceived(device, createAckMessage().toByteArray())

    verify(fakeConnector, never()).sendMessageSecurely(eq(device), any())
  }

  @Test
  fun handleConfig_sendAckAfterReceivedConfig() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    val captor = argumentCaptor<ByteArray>()
    // First message message is configAck; second is payload.
    verify(fakeConnector, times(2)).sendMessageSecurely(eq(device), captor.capture())
    val message = HowitzerMessage.parseFrom(captor.firstValue)
    assertThat(message.messageType).isEqualTo(MessageType.ACK)
  }

  @Test
  fun handleConfig_sendPayloadWhenIhuIsSender() {
    connect()
    val payloadCount = 10
    val payloadSize = 100
    val config =
      createConfig(
        sendPayloadFromIhu = true,
        payloadSize = payloadSize,
        payloadCount = payloadCount,
        testId = TEST_ID,
      )

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    val captor = argumentCaptor<ByteArray>()
    // First message message is configAck; the rest are payload.
    val expectedMessageCount = 1 + payloadCount
    verify(fakeConnector, times(expectedMessageCount))
      .sendMessageSecurely(eq(device), captor.capture())
    assertThat(captor.lastValue.size).isEqualTo(payloadSize)
  }

  @Test
  fun handleConfig_doNotSendPayloadWhenPhoneIsSender() {
    connect()
    val config = createConfig(sendPayloadFromIhu = false, testId = TEST_ID)

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    // First message message is config ack, no other messages should be sent.
    verify(fakeConnector).sendMessageSecurely(eq(device), any())
  }

  @Test
  fun handlePayload_sendResultAfterReceivedAllPayloads() {
    connect()
    val payloadCount = 5
    val payloadSize = 10
    val config =
      createConfig(
        sendPayloadFromIhu = false,
        payloadSize = payloadSize,
        payloadCount = payloadCount,
        testId = TEST_ID,
      )
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    for (i in 1..payloadCount) {
      howitzerFeature.onMessageReceived(device, createPayload(payloadSize))
    }

    // First message message is config ack, second is result.
    val expectedMessageCount = 2
    val captor = argumentCaptor<ByteArray>()
    verify(fakeConnector, times(expectedMessageCount))
      .sendMessageSecurely(eq(device), captor.capture())
    val message = HowitzerMessage.parseFrom(captor.lastValue)
    assertThat(message.messageType).isEqualTo(MessageType.RESULT)
  }

  @Test
  fun handlePayload_doNotSendResultWhenHaveNotReceivedAllPayloads() {
    connect()
    val payloadCount = 5
    val receivedPayloadCount = 4
    val payloadSize = 10
    val config =
      createConfig(
        sendPayloadFromIhu = false,
        payloadSize = payloadSize,
        payloadCount = payloadCount,
        testId = TEST_ID,
      )
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    for (i in 1..receivedPayloadCount) {
      howitzerFeature.onMessageReceived(device, createPayload(payloadSize))
    }

    // First message message is config ack, no other messages should be sent.
    verify(fakeConnector).sendMessageSecurely(eq(device), any())
  }

  @Test
  fun handlePayload_throwExceptionIfPayloadSizeMismatched() {
    connect()
    val configPayloadSize = 10
    val receivedPayloadSize = 8
    val config =
      createConfig(sendPayloadFromIhu = false, payloadSize = configPayloadSize, testId = TEST_ID)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    assertFailsWith<IllegalStateException> {
      howitzerFeature.onMessageReceived(device, createPayload(receivedPayloadSize))
    }
  }

  @Test
  fun handleResult_sendAckAfterReceivingResults() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    howitzerFeature.onMessageReceived(device, createResult(config).toByteArray())

    // First message message is config ack, second is payload, third is result ack.
    val expectedMessageCount = 3
    val captor = argumentCaptor<ByteArray>()
    verify(fakeConnector, times(expectedMessageCount))
      .sendMessageSecurely(eq(device), captor.capture())
    val message = HowitzerMessage.parseFrom(captor.lastValue)
    assertThat(message.messageType).isEqualTo(MessageType.ACK)
  }

  @Test
  fun handleResult_resetAfterSendingAck() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    howitzerFeature.onMessageReceived(device, createResult(config).toByteArray())

    // First message is config ack, second is payload, third is result ack.
    val expectedMessageCount = 3
    verifyReadyForNextTest(count = expectedMessageCount)
  }

  @Test
  fun handleResult_doNotAckIfCannotParseMessage() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    howitzerFeature.onMessageReceived(device, createPayload(config.payloadSize))

    val expectedMessageCount = 2
    verifyReadyForNextTest(count = expectedMessageCount)
  }

  @Test
  fun handleResult_doNotAckIfMessageTypeMismatch() {
    connect()
    val config = createConfig(sendPayloadFromIhu = true, testId = TEST_ID)

    // In this set up correct received message type should be:
    // 1: config
    // 2: result (received config message instead)
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())
    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())

    // First message is config ack, second is payload.
    val expectedMessageCount = 2
    verifyReadyForNextTest(count = expectedMessageCount)
  }

  @Test
  fun handleResultAck_reset() {
    connect()
    val config = createConfig(sendPayloadFromIhu = false, testId = TEST_ID)

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())
    howitzerFeature.onMessageReceived(device, createPayload(config.payloadSize))
    howitzerFeature.onMessageReceived(device, createAckMessage().toByteArray())

    // First message message is config ack, second is result.
    val expectedMessageCount = 2
    verifyReadyForNextTest(count = expectedMessageCount)
  }

  @Test
  fun handleResultAck_receivedAckBeforeReceivingAllPayloads() {
    connect()
    val config = createConfig(sendPayloadFromIhu = false, payloadCount = 2, testId = TEST_ID)

    howitzerFeature.onMessageReceived(device, createConfigMessage(config).toByteArray())
    howitzerFeature.onMessageReceived(device, createPayload(config.payloadSize))

    assertFailsWith<IllegalStateException> {
      howitzerFeature.onMessageReceived(device, createAckMessage().toByteArray())
    }
  }

  private fun connect(remoteDevice: ConnectedDevice = device) {
    fakeConnector.callback?.onSecureChannelEstablished(remoteDevice)
  }

  private fun disconnect(remoteDevice: ConnectedDevice = device) {
    fakeConnector.callback?.onDeviceDisconnected(remoteDevice)
  }

  private fun verifyReadyForNextTest(connectedDevice: ConnectedDevice = device, count: Int = 1) {
    val newConfig = createConfig(sendPayloadFromIhu = false, testId = TEST_ID)
    howitzerFeature.onMessageReceived(connectedDevice, createConfigMessage(newConfig).toByteArray())

    val captor = argumentCaptor<ByteArray>()
    // Additional message is ack message to the new config.
    verify(fakeConnector, times(count + 1))
      .sendMessageSecurely(eq(connectedDevice), captor.capture())
    val message = HowitzerMessage.parseFrom(captor.lastValue)
    assertThat(message.messageType).isEqualTo(MessageType.ACK)
    assertThat(howitzerFeature.payloadTimeInstants).isEmpty()
  }

  companion object {
    private val TEST_ID = UUID.randomUUID()
    private val DEVICE_ID = UUID.randomUUID().toString()
    private val device = createMockDevice()

    /** Creates a mock [ConnectedDevice] with a random [UUID]. */
    private fun createMockDevice(mockDeviceId: String = DEVICE_ID): ConnectedDevice = mock {
      on { deviceId } doReturn mockDeviceId
    }
  }
}
