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
import com.google.android.companionprotos.VerificationCode
import com.google.android.companionprotos.VerificationCodeState
import com.google.android.connecteddevice.connection.MultiProtocolSecureChannel.ShowVerificationCodeListener
import com.google.android.connecteddevice.model.DeviceMessage
import com.google.android.connecteddevice.model.DeviceMessage.OperationType
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceDatabase
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.storage.CryptoHelper
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.android.encryptionrunner.EncryptionRunnerFactory
import com.google.android.encryptionrunner.FakeEncryptionRunner
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.protobuf.ByteString
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PROTOCOL_ID = "testProtocol"

@RunWith(AndroidJUnit4::class)
class MultiProtocolSecureChannelV4Test {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val stream1 =
    spy(ProtocolStream(ProtocolDevice(TestProtocol(), PROTOCOL_ID), directExecutor()))
  private lateinit var storage: ConnectedDeviceStorage
  private val mockOobRunner: OobRunner = mock()
  private val mockShowVerificationCodeListener: ShowVerificationCodeListener = mock()
  private val mockCallback: MultiProtocolSecureChannel.Callback = mock()

  @Before
  fun setUp() {
    val database =
      Room.inMemoryDatabaseBuilder(context, ConnectedDeviceDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directExecutor())
        .build()
        .associatedDeviceDao()
    storage = ConnectedDeviceStorage(context, Base64CryptoHelper(), database, directExecutor())
  }
  @Test
  fun processVerificationCodeMessage_oobVerification_verifyOobCode() {
    val secureChannel = setupSecureChannel(false)
    initHandshakeMessage(secureChannel)
    respondToContinueMessage(secureChannel)
    val testPayload = "testPayload".toByteArray()
    val testVerificationCodeMessage =
      VerificationCode.newBuilder().run {
        setState(VerificationCodeState.OOB_VERIFICATION)
        setPayload(ByteString.copyFrom(testPayload))
        build()
      }
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        testVerificationCodeMessage.toByteArray()
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)

    verify(mockOobRunner).decryptData(testPayload)
  }

  @Test
  fun createOobResponse_oobCodeMatch_sendCorrectMessage() {
    val testPayload = "testPayload".toByteArray()
    whenever(mockOobRunner.decryptData(testPayload))
      .thenReturn(FakeEncryptionRunner.VERIFICATION_CODE)
    whenever(mockOobRunner.encryptData(FakeEncryptionRunner.VERIFICATION_CODE))
      .thenReturn(testPayload)
    val secureChannel = setupSecureChannel(false)
    initHandshakeMessage(secureChannel)
    respondToContinueMessage(secureChannel)
    val testVerificationCodeMessage =
      VerificationCode.newBuilder().run {
        setState(VerificationCodeState.OOB_VERIFICATION)
        setPayload(ByteString.copyFrom(testPayload))
        build()
      }
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        testVerificationCodeMessage.toByteArray()
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)

    val confirmationMessage =
      argumentCaptor<DeviceMessage>().run {
        verify(stream1, times(2)).sendMessage(capture())
        secondValue.message
      }
    val verificationCodeMessage = VerificationCode.parseFrom(confirmationMessage)
    assertThat(verificationCodeMessage.state).isEqualTo(VerificationCodeState.OOB_VERIFICATION)
  }

  @Test
  fun processVerificationCodeMessage_visualVerification_invokeListener() {
    val secureChannel = setupSecureChannel(false)
    initHandshakeMessage(secureChannel)
    respondToContinueMessage(secureChannel)
    val testVerificationCodeMessage =
      VerificationCode.newBuilder().setState(VerificationCodeState.VISUAL_VERIFICATION).build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        testVerificationCodeMessage.toByteArray()
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)

    verify(mockShowVerificationCodeListener).showVerificationCode(any())
  }

  @Test
  fun onVisualVerificationCodeConfirmed_sendConfirmationMessage() {
    val secureChannel = setupSecureChannel(false)
    initHandshakeMessage(secureChannel)
    respondToContinueMessage(secureChannel)
    val testVerificationCodeMessage =
      VerificationCode.newBuilder().setState(VerificationCodeState.VISUAL_VERIFICATION).build()
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        testVerificationCodeMessage.toByteArray()
      )
    secureChannel.onDeviceMessageReceived(deviceMessage)
    secureChannel.notifyVerificationCodeAccepted()
    val confirmationMessage =
      argumentCaptor<DeviceMessage>().run {
        verify(stream1, times(2)).sendMessage(capture())
        secondValue.message
      }
    val verificationCodeMessage = VerificationCode.parseFrom(confirmationMessage)
    assertThat(verificationCodeMessage.state).isEqualTo(VerificationCodeState.VISUAL_CONFIRMATION)
  }

  private fun setupSecureChannel(
    isReconnect: Boolean,
    deviceId: String? = null
  ): MultiProtocolSecureChannelV4 {
    val encryptionRunner = EncryptionRunnerFactory.newFakeRunner()
    encryptionRunner.setIsReconnect(isReconnect)
    return MultiProtocolSecureChannelV4(
        stream1,
        storage,
        encryptionRunner,
        deviceId = deviceId,
        oobRunner = mockOobRunner
      )
      .apply {
        callback = mockCallback
        showVerificationCodeListener = mockShowVerificationCodeListener
      }
  }

  private fun initHandshakeMessage(
    channel: MultiProtocolSecureChannelV4,
    message: ByteArray = FakeEncryptionRunner.INIT_MESSAGE
  ) {
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    channel.onDeviceMessageReceived(deviceMessage)
  }

  private fun respondToContinueMessage(
    channel: MultiProtocolSecureChannelV4,
    message: ByteArray = FakeEncryptionRunner.CLIENT_RESPONSE
  ) {
    val deviceMessage =
      DeviceMessage.createOutgoingMessage(
        /* recipient= */ null,
        /* isMessageEncrypted= */ false,
        OperationType.ENCRYPTION_HANDSHAKE,
        message
      )
    channel.onDeviceMessageReceived(deviceMessage)
  }
}

private class TestProtocol : ConnectionProtocol() {
  override val isDeviceVerificationRequired = false

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

  override fun reset() {}

  override fun getMaxWriteSize(protocolId: String): Int {
    return 0
  }
}

private class Base64CryptoHelper : CryptoHelper {
  override fun encrypt(value: ByteArray?): String? = Base64.encodeToString(value, Base64.DEFAULT)

  override fun decrypt(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
}
