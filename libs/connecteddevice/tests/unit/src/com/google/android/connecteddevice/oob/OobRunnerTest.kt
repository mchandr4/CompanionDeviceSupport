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

import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobRunnerTest {
  private val mockOobChannelFactory = mock<OobChannelFactory>()
  private val oobRunner = OobRunner(mockOobChannelFactory, SUPPORTED_TYPES_SINGLE)
  private lateinit var testOobChannel: TestOobChannel
  private lateinit var testProtocolDevice: ProtocolDevice

  @Before
  fun setUp() {
    testOobChannel = spy(TestOobChannel())
    whenever(mockOobChannelFactory.createOobChannel(any())).thenReturn(testOobChannel)
    testProtocolDevice = ProtocolDevice(TestConnectionProtocol(), "testProtocolId")
  }

  @Test
  fun sendOobData_startOobDataExchangeSuccessfully_addToEatablishedOobChannels() {
    oobRunner.generateOobData()
    testOobChannel.oobDataExchangeResult = true
    oobRunner.sendOobData(testProtocolDevice)

    assertThat(oobRunner.establishedOobChannels).hasSize(1)
  }

  @Test
  fun sendOobData_startOobDataExchangeFailed() {
    testOobChannel.oobDataExchangeResult = false
    oobRunner.sendOobData(testProtocolDevice)

    assertThat(oobRunner.establishedOobChannels).isEmpty()
  }

  @Test
  fun sendOobData_tryAllSupportedChannel() {
    val oobRunner = OobRunner(mockOobChannelFactory, SUPPORTED_TYPES_TWO)
    oobRunner.generateOobData()
    oobRunner.sendOobData(testProtocolDevice)

    verify(mockOobChannelFactory, times(2)).createOobChannel(any())
  }

  @Test
  fun reset_resetAllStatus() {
    testOobChannel.oobDataExchangeResult = true
    oobRunner.generateOobData()
    oobRunner.sendOobData(testProtocolDevice)
    oobRunner.reset()
    assertThat(testOobChannel.isInterrupted).isTrue()
    assertThat(oobRunner.encryptionKey).isNull()
    assertThat(oobRunner.establishedOobChannels).isEmpty()
  }

  @Test
  fun testInit_keyIsNull() {
    assertThat(oobRunner.encryptionKey).isNull()
  }

  @Test
  fun generateOobData_algorithmNotSupported_throwException() {
    val oobRunner =
      OobRunner(mockOobChannelFactory, SUPPORTED_TYPES_SINGLE, keyAlgorithm = "UNKNOWN")
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
    var isInterrupted = false

    override fun completeOobDataExchange(
      protocolDevice: ProtocolDevice,
      oobData: ByteArray,
    ): Boolean {
      return oobDataExchangeResult
    }

    override fun interrupt() {
      isInterrupted = true
    }
  }

  private class TestConnectionProtocol : ConnectionProtocol() {
    override fun isDeviceVerificationRequired() = false

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
  }

  companion object {
    private val SUPPORTED_TYPES_SINGLE = listOf(OobChannelFactory.BT_RFCOMM)
    private val SUPPORTED_TYPES_TWO =
      listOf(OobChannelFactory.BT_RFCOMM, OobChannelFactory.PRE_ASSOCIATION)
  }
}
