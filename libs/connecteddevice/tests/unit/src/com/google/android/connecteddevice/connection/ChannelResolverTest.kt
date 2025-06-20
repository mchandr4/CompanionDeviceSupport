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
package com.google.android.connecteddevice.connection

import android.content.Context
import android.os.ParcelUuid
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannelType
import com.google.android.companionprotos.VersionExchangeProto
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataReceivedListener
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.encryptionrunner.FakeEncryptionRunner
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.validateMockitoUsage
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val TEST_PROTOCOL_ID_1 = "testDevice1"
private const val TEST_PROTOCOL_ID_2 = "testDevice2"
private val TEST_DEVICE_ID = UUID.randomUUID()
private val TEST_CHALLENGE = "test Challenge".toByteArray()
private val TEST_CHALLENGE_RESPONSE = "test Challenge response".toByteArray()
private val BT_RFCOMM_CHANNEL = OobChannelType.BT_RFCOMM
private val SUPPORTED_OOB_CAPABILITIES = listOf(BT_RFCOMM_CHANNEL.name)

@RunWith(AndroidJUnit4::class)
class ChannelResolverTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testProtocol1: TestProtocol = spy(TestProtocol(needDeviceVerification = false))
  private val testProtocol2: TestProtocol = spy(TestProtocol(needDeviceVerification = true))
  private val mockCallback = mock<ChannelResolver.Callback>()
  private val mockStreamFactory = mock<ProtocolStreamFactory>()
  private val mockStream = mock<ProtocolStream>()
  private val mockOobRunner = mock<OobRunner>()
  private var mockEncryptionRunner = mock<FakeEncryptionRunner>()
  private val testDevice1 = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
  private val testDevice2 = ProtocolDevice(testProtocol2, TEST_PROTOCOL_ID_2)
  private lateinit var mockStorage: ConnectedDeviceStorage
  private lateinit var channelResolver: ChannelResolver

  @Before
  fun setUp() {
    mockStorage = mock()
    whenever(mockStreamFactory.createProtocolStream(any())).thenReturn(mockStream)
    mockStorage.stub {
      onBlocking { hashWithChallengeSecret(any(), any()) } doReturn TEST_CHALLENGE_RESPONSE
    }
    channelResolver =
      ChannelResolver(
        testDevice1,
        mockStorage,
        mockCallback,
        mockStreamFactory,
        mockEncryptionRunner,
      )
  }

  @After
  fun cleanUp() {
    // Validate after each test to get accurate indication of Mockito misuse.
    validateMockitoUsage()
  }

  @Test
  fun resolveAssociation_dataReceivedListenerRegistered() {
    channelResolver.resolveAssociation(mockOobRunner)
    assertThat(testProtocol1.dataReceivedListenerList).hasSize(1)
  }

  @Test
  fun resolveReconnect_dataReceivedListenerRegistered() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    assertThat(testProtocol1.dataReceivedListenerList).hasSize(1)
  }

  @Test
  fun addDeviceProtocol_dataReceivedListenerRegistered() {
    channelResolver.resolveAssociation(mockOobRunner)
    channelResolver.addProtocolDevice(testDevice2)
    assertThat(testProtocol2.dataReceivedListenerList).hasSize(1)
  }

  @Test
  fun receivedUnsupportedVersion_invokeOnError() {
    val unsupportedVersion =
      VersionExchangeProto.VersionExchange.newBuilder()
        .setMaxSupportedMessagingVersion(ChannelResolver.MAX_MESSAGING_VERSION + 1)
        .setMinSupportedMessagingVersion(ChannelResolver.MAX_MESSAGING_VERSION + 1)
        .setMaxSupportedSecurityVersion(ChannelResolver.MAX_SECURITY_VERSION + 1)
        .setMinSupportedSecurityVersion(ChannelResolver.MAX_SECURITY_VERSION + 1)
        .build()
        .toByteArray()
    channelResolver.resolveAssociation(mockOobRunner)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, unsupportedVersion)
    }
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun receivedSupportedVersion_sendVersionMessage() {
    channelResolver.resolveAssociation(mockOobRunner)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }
    val expectedVersion = createVersionMessage()
    argumentCaptor<ByteArray>().apply {
      verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), capture(), anyOrNull())
      assertThat(firstValue).isEqualTo(expectedVersion)
    }
  }

  @Test
  fun receivedInvalidChallenge_invokeOnError() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val invalidChallengeMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        "Invalid test Challenge".toByteArray(),
      )
    mockStream.messageReceivedListener?.onMessageReceived(invalidChallengeMessage)
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun receivedValidChallenge_sendChallengeResponse() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        TEST_CHALLENGE,
      )
    mockStream.messageReceivedListener?.onMessageReceived(validChallengeMessage)
    argumentCaptor<DeviceMessage> {
      verify(mockStream).sendMessage(capture())
      assertThat(firstValue.operationType).isEqualTo(OperationType.ENCRYPTION_HANDSHAKE)
      assertThat(firstValue.recipient).isNull()
      assertThat(firstValue.message).isEqualTo(TEST_CHALLENGE_RESPONSE)
    }
  }

  @Test
  fun receivedValidChallenge_invokeOnChannelResolved() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        TEST_CHALLENGE,
      )
    mockStream.messageReceivedListener?.onMessageReceived(validChallengeMessage)
    verify(mockCallback).onChannelResolved(any())
  }

  @Test
  fun challengeNotRequired_invokeOnChannelResolved() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    argumentCaptor<IDataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }
    verify(mockCallback).onChannelResolved(any())
  }

  private fun createVersionMessage(
    maxSecurityVersion: Int = ChannelResolver.MAX_SECURITY_VERSION
  ): ByteArray {
    return VersionExchangeProto.VersionExchange.newBuilder()
      .setMaxSupportedMessagingVersion(ChannelResolver.MAX_MESSAGING_VERSION)
      .setMinSupportedMessagingVersion(ChannelResolver.MIN_MESSAGING_VERSION)
      .setMaxSupportedSecurityVersion(maxSecurityVersion)
      .setMinSupportedSecurityVersion(ChannelResolver.MIN_SECURITY_VERSION)
      .build()
      .toByteArray()
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }

  open class TestProtocol(private val needDeviceVerification: Boolean) : ConnectionProtocol() {

    val dataReceivedListenerList = dataReceivedListeners

    override fun isDeviceVerificationRequired() = needDeviceVerification

    override fun startAssociationDiscovery(
      name: String,
      identifier: ParcelUuid,
      callback: IDiscoveryCallback,
    ) {}

    override fun startConnectionDiscovery(
      id: ParcelUuid,
      challenge: ConnectChallenge,
      callback: IDiscoveryCallback,
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: ParcelUuid) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }

    override fun reset() {}
  }
}
