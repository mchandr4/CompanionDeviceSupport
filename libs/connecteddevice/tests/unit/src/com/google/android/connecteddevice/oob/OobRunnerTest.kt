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

package com.google.android.connecteddevice.oob

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannelType
import com.google.android.companionprotos.OutOfBandAssociationToken
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.model.OobEligibleDevice
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobRunnerTest {
  private val mockOobRunnerCallback: OobRunner.Callback = mock()
  private val mockOobChannelFactory: OobChannelFactory = mock()
  private val testSecurityVersion = OobRunner.MIN_SECURITY_VERSION_FOR_OOB_PROTO
  private val supportedTypesSingle = listOf(OobChannelType.BT_RFCOMM.name)
  private val supportedTypesTwo =
    listOf(OobChannelType.BT_RFCOMM.name, OobChannelType.OOB_CHANNEL_UNKNOWN.name)
  private val oobRunner = OobRunner(mockOobChannelFactory, supportedTypesSingle)
  private lateinit var testOobChannel: TestOobChannel
  private lateinit var testProtocolDevice: ProtocolDevice

  @Before
  fun setUp() {
    testOobChannel = spy(TestOobChannel())
    whenever(mockOobChannelFactory.createOobChannel(any())).thenReturn(testOobChannel)
    testProtocolDevice = ProtocolDevice(TestConnectionProtocol(), "testProtocolId")
  }

  @Test
  fun startOobDataExchange_startOobDataExchangeSuccessfully_returnTrue() {
    testOobChannel.oobDataExchangeResult = true

    assertThat(
        oobRunner.startOobDataExchange(
          testProtocolDevice,
          listOf(OobChannelType.BT_RFCOMM),
          testSecurityVersion,
          mockOobRunnerCallback
        )
      )
      .isTrue()
  }

  @Test
  fun startOobDataExchange_PreOobDataProto_sendRawBytes() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.generateOobData()

    assertThat(
        oobRunner.startOobDataExchange(
          testProtocolDevice,
          listOf(OobChannelType.BT_RFCOMM),
          testSecurityVersion - 1,
          mockOobRunnerCallback
        )
      )
      .isTrue()
    testOobChannel.oobChannelCallback!!.onOobExchangeSuccess()

    val data =
      argumentCaptor<ByteArray>().run {
        verify(testOobChannel).sendOobData(capture())
        firstValue
      }

    assertThrows(IOException::class.java) { OutOfBandAssociationToken.parseFrom(data) }
  }

  @Test
  fun startOobDataExchange_startOobDataExchangeFailed_returnFalse() {
    testOobChannel.oobDataExchangeResult = false

    assertThat(
        oobRunner.startOobDataExchange(
          testProtocolDevice,
          listOf(OobChannelType.BT_RFCOMM),
          testSecurityVersion,
          mockOobRunnerCallback
        )
      )
      .isFalse()
  }

  @Test
  fun startOobDataExchange_unsupportedChannelType_returnFalse() {
    testOobChannel.oobDataExchangeResult = true

    assertThat(
        oobRunner.startOobDataExchange(
          testProtocolDevice,
          listOf(OobChannelType.OOB_CHANNEL_UNKNOWN),
          testSecurityVersion,
          mockOobRunnerCallback
        )
      )
      .isFalse()
  }

  @Test
  fun startOobDataExchange_failedTheFirstChannel_tryTheAlternativeSupportedChannel() {
    val oobRunner = OobRunner(mockOobChannelFactory, supportedTypesTwo)
    testOobChannel.oobDataExchangeResult = false

    assertThat(
        oobRunner.startOobDataExchange(
          testProtocolDevice,
          listOf(OobChannelType.BT_RFCOMM, OobChannelType.OOB_CHANNEL_UNKNOWN),
          testSecurityVersion,
          mockOobRunnerCallback
        )
      )
      .isFalse()
    verify(mockOobChannelFactory, times(2)).createOobChannel(any())
  }

  @Test
  fun startOobDataExchange_onOobExchangeSuccess_invokeSuccessCallback() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.generateOobData()
    oobRunner.startOobDataExchange(
      testProtocolDevice,
      listOf(OobChannelType.BT_RFCOMM),
      testSecurityVersion,
      mockOobRunnerCallback
    )
    assertThat(testOobChannel.oobChannelCallback).isNotNull()
    testOobChannel.oobChannelCallback!!.onOobExchangeSuccess()

    verify(mockOobRunnerCallback).onOobDataExchangeSuccess()
  }

  @Test
  fun startOobDataExchange_onOobExchangeSuccessWithoutOobData_invokeFailureCallback() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.startOobDataExchange(
      testProtocolDevice,
      listOf(OobChannelType.BT_RFCOMM),
      testSecurityVersion,
      mockOobRunnerCallback
    )
    testOobChannel.oobChannelCallback!!.onOobExchangeSuccess()

    verify(mockOobRunnerCallback).onOobDataExchangeFailure()
  }

  @Test
  fun startOobDataExchange_onOobExchangeFailure_invokeFailureCallback() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.generateOobData()
    oobRunner.startOobDataExchange(
      testProtocolDevice,
      listOf(OobChannelType.BT_RFCOMM),
      testSecurityVersion,
      mockOobRunnerCallback
    )
    assertThat(testOobChannel.oobChannelCallback).isNotNull()
    testOobChannel.oobChannelCallback!!.onOobExchangeFailure()

    verify(mockOobRunnerCallback).onOobDataExchangeFailure()
  }

  @Test
  fun reset_resetAllStatus() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.generateOobData()
    oobRunner.startOobDataExchange(
      testProtocolDevice,
      listOf(OobChannelType.BT_RFCOMM),
      testSecurityVersion,
      mockOobRunnerCallback
    )
    oobRunner.reset()
    assertThat(testOobChannel.isInterrupted).isTrue()
    assertThat(oobRunner.encryptionKey).isNull()
  }

  @Test
  fun testInit_keyIsNull() {
    assertThat(oobRunner.encryptionKey).isNull()
  }

  @Test
  fun generateOobData_algorithmNotSupported_throwException() {
    val oobRunner = OobRunner(mockOobChannelFactory, supportedTypesSingle, keyAlgorithm = "UNKNOWN")
    assertThrows(IllegalStateException::class.java) { oobRunner.generateOobData() }
  }

  @Test
  fun generateOobData_keyAndNoncesAreNonNullAndOobDataIsSetCorrectly() {
    val oobData = oobRunner.generateOobData()
    assertThat(oobRunner.encryptionKey).isNotNull()
    assertThat(oobRunner.ihuIv).isNotNull()
    assertThat(oobRunner.mobileIv).isNotNull()
    assertThat(oobRunner.ihuIv).isNotEqualTo(oobRunner.mobileIv)
    assertThat(oobData)
      .isEqualTo(OobData(oobRunner.encryptionKey!!.encoded, oobRunner.ihuIv, oobRunner.mobileIv))
  }

  @Test
  @Throws(Exception::class)
  fun serverEncryptAndClientDecrypt() {
    val testMessage = "testMessage".toByteArray()
    oobRunner.generateOobData()
    val encryptedTestMessage = oobRunner.encryptData(testMessage)

    switchClientAndServerRole()

    val decryptedTestMessage = oobRunner.decryptData(encryptedTestMessage)
    assertThat(decryptedTestMessage).isEqualTo(testMessage)
  }

  @Test
  @Throws(Exception::class)
  fun encryptAndDecryptWithDifferentNonces_throwsException() {
    val testMessage = "testMessage".toByteArray()
    oobRunner.generateOobData()
    val encryptedMessage = oobRunner.encryptData(testMessage)
    assertThrows(IllegalStateException::class.java) { oobRunner.decryptData(encryptedMessage) }
  }

  @Test
  fun decryptWithShortMessage_throwsException() {
    oobRunner.generateOobData()
    assertThrows(IllegalStateException::class.java) { oobRunner.decryptData("short".toByteArray()) }
  }

  @Test
  fun encryptWithNullKey_throwsException() {
    val testMessage = "testMessage".toByteArray()
    assertThrows(IllegalStateException::class.java) { oobRunner.encryptData(testMessage) }
  }

  @Test
  fun decryptWithNullKey_throwsException() {
    val testMessage = "testMessage".toByteArray()
    assertThrows(IllegalStateException::class.java) { oobRunner.decryptData(testMessage) }
  }

  private fun switchClientAndServerRole() {
    val encryptionIv = oobRunner.ihuIv
    oobRunner.ihuIv = oobRunner.mobileIv
    oobRunner.mobileIv = encryptionIv
  }
  private open class TestOobChannel : OobChannel {
    var oobDataExchangeResult = false
    var oobChannelCallback: OobChannel.Callback? = null
    var isInterrupted = false

    override fun completeOobDataExchange(
      protocolDevice: ProtocolDevice,
      callback: OobChannel.Callback
    ): Boolean {
      oobChannelCallback = callback
      return oobDataExchangeResult
    }

    override fun completeOobDataExchange(
      device: OobEligibleDevice,
      callback: OobChannel.Callback
    ) {}

    override fun sendOobData(oobData: ByteArray) {}

    override fun interrupt() {
      isInterrupted = true
    }
  }

  private class TestConnectionProtocol : ConnectionProtocol() {
    override val isDeviceVerificationRequired: Boolean
      get() = false

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
  }
}
