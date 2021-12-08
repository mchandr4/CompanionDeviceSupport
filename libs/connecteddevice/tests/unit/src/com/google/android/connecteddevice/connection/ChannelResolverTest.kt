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
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ConnectionProtocol.DataReceivedListener
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import com.google.android.encryptionrunner.FakeEncryptionRunner
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.validateMockitoUsage
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_PROTOCOL_ID_1 = "testDevice1"
private const val TEST_PROTOCOL_ID_2 = "testDevice2"
private const val OOB_CALLBACK_VALID_VERSION = 3
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
  private val mockCallback: ChannelResolver.Callback = mock()
  private val mockStreamFactory: ProtocolStreamFactory = mock()
  private val mockStream: ProtocolStream = mock()
  private val mockOobRunner: OobRunner = mock {
    on { supportedTypes } doReturn SUPPORTED_OOB_CAPABILITIES
  }
  private var mockEncryptionRunner: FakeEncryptionRunner = mock()
  private val testDevice1 = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
  private val testDevice2 = ProtocolDevice(testProtocol2, TEST_PROTOCOL_ID_2)
  private lateinit var spyStorage: ConnectedDeviceStorage
  private lateinit var channelResolver: ChannelResolver
  private lateinit var connectedDeviceDatabase: ConnectedDeviceDatabase

  @Before
  fun setUp() {
    connectedDeviceDatabase =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
    val database = connectedDeviceDatabase.associatedDeviceDao()
    spyStorage =
      spy(ConnectedDeviceStorage(context, Base64CryptoHelper(), database, directExecutor()))
    whenever(mockStreamFactory.createProtocolStream(any(), any())).thenReturn(mockStream)
    whenever(spyStorage.hashWithChallengeSecret(any(), any())).thenReturn(TEST_CHALLENGE_RESPONSE)
    channelResolver =
      ChannelResolver(
        testDevice1,
        spyStorage,
        mockCallback,
        mockStreamFactory,
        mockEncryptionRunner
      )
  }

  @After
  fun cleanUp() {
    connectedDeviceDatabase.close()
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
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, unsupportedVersion)
    }
    verify(mockCallback).onChannelResolutionError()
  }

  @Test
  fun receivedSupportedVersion_sendVersionMessage() {
    channelResolver.resolveAssociation(mockOobRunner)
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
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
          ChannelResolver.SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE - 1
        )
        .setMinSupportedSecurityVersion(ChannelResolver.MIN_SECURITY_VERSION)
        .build()
        .toByteArray()
    channelResolver.resolveAssociation(mockOobRunner)

    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, version)
    }
    verify(mockCallback).onChannelResolved(any())
  }

  @Test
  fun receivedCapabilityExchangeSupportedVersion_sendCapabilityMessage() {
    val supportedOobChannelTypes = listOf(BT_RFCOMM_CHANNEL)
    channelResolver.resolveAssociation(mockOobRunner)
    val deviceCallback =
      argumentCaptor<DataReceivedListener>().apply {
        verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
      }
    verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), any(), anyOrNull())

    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(supportedOobChannelTypes)
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
      assertThat(capabilityResponse).isEqualTo(supportedOobChannelTypes)
    }
  }

  @Test
  fun receivedInvalidChallenge_invokeOnError() {
    channelResolver.resolveReconnect(TEST_DEVICE_ID, TEST_CHALLENGE)
    channelResolver.addProtocolDevice(testDevice2)
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val invalidChallengeMessage =
      DeviceMessage.createOutgoingMessage(
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
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage.createOutgoingMessage(
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
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol2).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_2), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_2, createVersionMessage())
    }
    val validChallengeMessage =
      DeviceMessage.createOutgoingMessage(
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
    argumentCaptor<DataReceivedListener>().apply {
      verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
      firstValue.onDataReceived(TEST_PROTOCOL_ID_1, createVersionMessage())
    }
    verify(mockCallback).onChannelResolved(any())
  }

  @Test
  fun onOobExchangeSuccess_V3_updateEncryptionRunnerAndResolveStream() {
    val testProtocolDevice = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
    val testOobChannels = listOf(BT_RFCOMM_CHANNEL)
    whenever(mockOobRunner.startOobDataExchange(eq(testProtocolDevice), any(), any(), any()))
      .thenReturn(true)
    channelResolver.resolveAssociation(mockOobRunner)
    val deviceCallback =
      argumentCaptor<DataReceivedListener>().apply {
        verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(
          TEST_PROTOCOL_ID_1,
          createVersionMessage(OOB_CALLBACK_VALID_VERSION)
        )
      }
    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(testOobChannels)
        .build()
        .toByteArray()
    deviceCallback.firstValue.onDataReceived(TEST_PROTOCOL_ID_1, capabilities)
    argumentCaptor<OobRunner.Callback>().apply {
      verify(mockOobRunner).startOobDataExchange(eq(testProtocolDevice), any(), any(), capture())
      firstValue.onOobDataExchangeSuccess()
    }

    verifyZeroInteractions(mockEncryptionRunner)
    verify(mockStreamFactory).createProtocolStream(eq(testProtocolDevice), any())
  }

  @Test
  fun onOobExchangeFailure_directlyResolveStream() {
    val testProtocolDevice = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
    val testOobChannels = listOf(BT_RFCOMM_CHANNEL)
    whenever(mockOobRunner.startOobDataExchange(eq(testProtocolDevice), any(), any(), any()))
      .thenReturn(true)
    channelResolver.resolveAssociation(mockOobRunner)
    val deviceCallback =
      argumentCaptor<DataReceivedListener>().apply {
        verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(
          TEST_PROTOCOL_ID_1,
          createVersionMessage(OOB_CALLBACK_VALID_VERSION)
        )
      }
    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(testOobChannels)
        .build()
        .toByteArray()
    deviceCallback.firstValue.onDataReceived(TEST_PROTOCOL_ID_1, capabilities)
    argumentCaptor<OobRunner.Callback>().apply {
      verify(mockOobRunner).startOobDataExchange(eq(testProtocolDevice), any(), any(), capture())
      firstValue.onOobDataExchangeFailure()
    }

    verify(mockEncryptionRunner).setIsReconnect(false)
    verify(mockStreamFactory).createProtocolStream(eq(testProtocolDevice), any())
  }

  @Test
  fun receivedCapabilityExchangeSupportedVersion_createOobChannelSuccessfully_waitForOobCallback() {
    val supportedOobChannelTypes = listOf(BT_RFCOMM_CHANNEL)
    val testProtocolDevice = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
    whenever(mockOobRunner.startOobDataExchange(eq(testProtocolDevice), any(), any(), any()))
      .thenReturn(true)
    channelResolver.resolveAssociation(mockOobRunner)
    val deviceCallback =
      argumentCaptor<DataReceivedListener>().apply {
        verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(
          TEST_PROTOCOL_ID_1,
          createVersionMessage(OOB_CALLBACK_VALID_VERSION)
        )
      }
    verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), any(), anyOrNull())

    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(supportedOobChannelTypes)
        .build()
        .toByteArray()
    deviceCallback.firstValue.onDataReceived(TEST_PROTOCOL_ID_1, capabilities)

    verify(mockStreamFactory, never()).createProtocolStream(any(), any())
  }

  @Test
  fun receivedCapabilityExchangeSupportedVersion_createOobChannelFailed_directlyResolveStream() {
    val supportedOobChannelTypes = listOf(BT_RFCOMM_CHANNEL)
    val testProtocolDevice = ProtocolDevice(testProtocol1, TEST_PROTOCOL_ID_1)
    channelResolver.resolveAssociation(mockOobRunner)
    val deviceCallback =
      argumentCaptor<DataReceivedListener>().apply {
        verify(testProtocol1).registerDataReceivedListener(eq(TEST_PROTOCOL_ID_1), capture(), any())
        firstValue.onDataReceived(
          TEST_PROTOCOL_ID_1,
          createVersionMessage(OOB_CALLBACK_VALID_VERSION)
        )
      }
    verify(testProtocol1).sendData(eq(TEST_PROTOCOL_ID_1), any(), anyOrNull())

    val capabilities =
      CapabilitiesExchange.newBuilder()
        .addAllSupportedOobChannels(supportedOobChannelTypes)
        .build()
        .toByteArray()
    deviceCallback.firstValue.onDataReceived(TEST_PROTOCOL_ID_1, capabilities)

    verify(mockStreamFactory).createProtocolStream(eq(testProtocolDevice), any())
    verify(mockEncryptionRunner).setIsReconnect(false)
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
    val dataReceivedListenerList: MutableMap<String, ThreadSafeCallbacks<DataReceivedListener>> =
      dataReceivedListeners

    override val isDeviceVerificationRequired: Boolean = needDeviceVerification

    override fun startAssociationDiscovery(
      name: String,
      callback: DiscoveryCallback,
      identifier: UUID
    ) {}

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
