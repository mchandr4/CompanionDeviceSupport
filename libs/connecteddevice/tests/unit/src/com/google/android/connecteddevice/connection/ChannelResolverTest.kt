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
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannelType
import com.google.android.companionprotos.VersionExchangeProto
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_PROTOCOL_ID_1 = "testDevice1"
private const val TEST_PROTOCOL_ID_2 = "testDevice2"
private val TEST_DEVICE_ID = UUID.randomUUID()
private val TEST_CHALLENGE = "test Challenge".toByteArray()
private val TEST_CHALLENGE_RESPONSE = "test Challenge response".toByteArray()
private val SUPPORTED_OOB_CAPABILITIES = listOf(OobChannelType.BT_RFCOMM)

@RunWith(AndroidJUnit4::class)
class ChannelResolverTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testProtocol1: TestProtocol = spy(TestProtocol(needDeviceVerification = false))
  private val testProtocol2: TestProtocol = spy(TestProtocol(needDeviceVerification = true))
  private val mockCallback: ChannelResolver.Callback = mock()
  private val mockStreamFactory: ProtocolStreamFactory = mock()
  private val mockStream: ProtocolStream = mock()
  private val testDevice1 = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
  private val testDevice2 = ProtocolDevice(testProtocol2, TEST_PROTOCOL_ID_2)
  private lateinit var spyStorage: ConnectedDeviceStorage
  private lateinit var channelResolver: ChannelResolver

  @Before
  fun setUp() {
    val connectedDeviceDatabase =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
    val database = connectedDeviceDatabase.associatedDeviceDao()
    spyStorage = spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database))
    whenever(mockStreamFactory.createProtocolStream(any(), any())).thenReturn(mockStream)
    whenever(spyStorage.hashWithChallengeSecret(any(), any())).thenReturn(TEST_CHALLENGE_RESPONSE)
    channelResolver = ChannelResolver(testDevice1, spyStorage, mockCallback, mockStreamFactory)
  }

  @Test
  fun resolveAssociation_deviceCallbackRegistered() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    assertThat(testProtocol1.callbackList).hasSize(1)
  }

  @Test
  fun resolveReconnect_deviceCallbackRegistered() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    assertThat(testProtocol1.callbackList).hasSize(1)
  }

  @Test
  fun addDeviceProtocol_deviceCallbackRegistered() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    channelResolver.addProtocolDevice(testDevice2)
    assertThat(testProtocol2.callbackList).hasSize(1)
  }

  @Test
  fun onProtocolDisconnected_deviceCallbackUnregistered() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDeviceDisconnected(TEST_PROTOCOL_ID_1)
    }
    verify(testProtocol1).unregisterCallback(eq(TEST_PROTOCOL_ID_1), any())
  }

  @Test
  fun onAllProtocolDisconnected_invokeOnError() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDeviceDisconnected(TEST_PROTOCOL_ID_1)
    }
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol2).registerCallback(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDeviceDisconnected(TEST_PROTOCOL_ID_2)
    }
    verify(testProtocol1).unregisterCallback(eq(TEST_PROTOCOL_ID_1), any())
    verify(testProtocol2).unregisterCallback(eq(TEST_PROTOCOL_ID_2), any())
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun onMainProtocolDisconnected_invokeOnError() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }

    val callbacks =
      testProtocol1.callbackList[TEST_PROTOCOL_ID_1] ?: fail("Failed to find callbacks.")
    callbacks.invoke { it.onDeviceDisconnected(TEST_PROTOCOL_ID_1) }

    verify(mockCallback).onChannelResolutionError()
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
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, unsupportedVersion)
    }
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun receivedSupportedVersion_sendVersionMessage() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }
    val expectedVersion = createVersionMessage()
    argumentCaptor<ByteArray>().apply {
      verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), capture(), anyOrNull())
      assertThat(firstValue).isEqualTo(expectedVersion)
    }
  }

  @Test
  fun receivedCapabilityExchangeNotSupportedVersion_invokeOnChannelResolved() {
    val version =
      VersionExchangeProto.VersionExchange.newBuilder()
        .setMaxSupportedMessagingVersion(ChannelResolver.MAX_MESSAGING_VERSION)
        .setMinSupportedMessagingVersion(ChannelResolver.MIN_MESSAGING_VERSION)
        .setMaxSupportedSecurityVersion(
          ChannelResolver.MIN_SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE - 1
        )
        .setMinSupportedSecurityVersion(ChannelResolver.MIN_SECURITY_VERSION)
        .build()
        .toByteArray()
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)

    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, version)
    }
    verify(mockCallback).onChannelResolved(any())
  }

  @Test
  fun receivedCapabilityExchangeSupportedVersion_sendCapabilityMessage() {
    channelResolver.resolveAssociation(SUPPORTED_OOB_CAPABILITIES)
    val deviceCallback =
      argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
        verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
      }
    verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), any(), anyOrNull())

    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(SUPPORTED_OOB_CAPABILITIES)
        .build()
        .toByteArray()
    deviceCallback.firstValue.onDataReceived(TEST_PROTOCOL_ID_1, capabilities)
    argumentCaptor<ByteArray> {
      verify(testProtocol1, times(2)).sendData(eq(TEST_PROTOCOL_ID_1), capture(), anyOrNull())
      val capabilityResponse =
        try {
          CapabilitiesExchange.parseFrom(lastValue, ExtensionRegistryLite.getEmptyRegistry())
            .supportedOobChannelsList
        } catch (e: InvalidProtocolBufferException) {
          fail("Failed to parse capabilities exchange message.")
        }
      assertThat(capabilityResponse).isEqualTo(SUPPORTED_OOB_CAPABILITIES)
    }
  }

  @Test
  fun receivedInvalidChallenge_invokeOnError() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol2).registerCallback(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val invalidChallengeMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE,
        "Invalid test Challenge".toByteArray()
      )
    mockStream.messageReceivedListener?.onMessageReceived(invalidChallengeMessage)
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun receivedValidChallenge_sendChallengeResponse() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol2).registerCallback(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE,
        TEST_CHALLENGE
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
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol2).registerCallback(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        TEST_CHALLENGE
      )
    mockStream.messageReceivedListener?.onMessageReceived(validChallengeMessage)
    verify(mockCallback).onChannelResolved(any())
  }

  @Test
  fun challengeNotRequired_invokeOnChannelResolved() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    argumentCaptor<ConnectionProtocol.DeviceCallback>().apply {
      verify(testProtocol1).registerCallback(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }
    verify(mockCallback).onChannelResolved(any())
  }

  private fun createVersionMessage(): ByteArray {
    return VersionExchangeProto.VersionExchange.newBuilder()
      .setMaxSupportedMessagingVersion(ChannelResolver.MAX_MESSAGING_VERSION)
      .setMinSupportedMessagingVersion(ChannelResolver.MIN_MESSAGING_VERSION)
      .setMaxSupportedSecurityVersion(ChannelResolver.MAX_SECURITY_VERSION)
      .setMinSupportedSecurityVersion(ChannelResolver.MIN_SECURITY_VERSION)
      .build()
      .toByteArray()
  }

  private class Base64CryptoHelper : CryptoHelper {
    override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

    override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
  }

  open class TestProtocol(private val needDeviceVerification: Boolean) : ConnectionProtocol() {
    val callbackList: ConcurrentHashMap<String, ThreadSafeCallbacks<DeviceCallback>> =
      deviceCallbacks

    override val isDeviceVerificationRequired: Boolean = needDeviceVerification

    override fun startAssociationDiscovery(name: String, callback: DiscoveryCallback) {}

    override fun startConnectionDiscovery(
      id: UUID,
      challenge: ConnectChallenge,
      callback: DiscoveryCallback
    ) {}

    override fun stopAssociationDiscovery() {}

    override fun stopConnectionDiscovery(id: UUID) {}

    override fun sendData(protocolId: String, data: ByteArray, callback: DataSendCallback?) {}

    override fun disconnectDevice(protocolId: String) {}

    override fun getMaxWriteSize(protocolId: String): Int {
      return 0
    }

    override fun reset() {}
  }
}
