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
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.model.OobData
import com.google.android.connecteddevice.transport.IConnectionProtocol
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobRunnerTest {
  private val testProtocolName = "testProtocolName"
  private val testConnectionProtocol = mockToBeAlive<IConnectionProtocol>()
  private val testProtocolDelegate = ProtocolDelegate()
  private lateinit var oobRunner: OobRunner

  @Before
  fun setUp() {
    oobRunner = OobRunner(testProtocolDelegate, testProtocolName)
  }

  @Test
  fun sendOobData_startOobDataExchangeSuccessfully_addToEatablishedOobChannels() {
    testProtocolDelegate.addOobProtocol(testConnectionProtocol)

    oobRunner.sendOobData()

    assertThat(oobRunner.establishedOobChannels).hasSize(1)
  }

  @Test
  fun sendOobData_startOobDataExchangeFailed() {
    oobRunner.sendOobData()

    assertThat(oobRunner.establishedOobChannels).isEmpty()
  }

  @Test
  fun reset_resetAllStatus() {
    testProtocolDelegate.addOobProtocol(testConnectionProtocol)
    oobRunner.sendOobData()
    oobRunner.reset()
    assertThat(oobRunner.encryptionKey).isNull()
    assertThat(oobRunner.establishedOobChannels).isEmpty()
  }

  @Test
  fun testInit_keyIsNull() {
    assertThat(oobRunner.encryptionKey).isNull()
  }

  @Test
  fun generateOobData_algorithmNotSupported_throwException() {
    val oobRunner = OobRunner(testProtocolDelegate, testProtocolName, keyAlgorithm = "UNKNOWN")
    assertThrows(IllegalStateException::class.java) { oobRunner.sendOobData() }
  }

  @Test
  fun generateOobData_keyAndNoncesAreNonNullAndOobDataIsSetCorrectly() {
    val oobData = oobRunner.sendOobData()
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
    oobRunner.sendOobData()
    val encryptedTestMessage = oobRunner.encryptData(testMessage)

    switchClientAndServerRole()

    val decryptedTestMessage = oobRunner.decryptData(encryptedTestMessage)
    assertThat(decryptedTestMessage).isEqualTo(testMessage)
  }

  @Test
  @Throws(Exception::class)
  fun encryptAndDecryptWithDifferentNonces_throwsException() {
    val testMessage = "testMessage".toByteArray()
    oobRunner.sendOobData()
    val encryptedMessage = oobRunner.encryptData(testMessage)
    assertThrows(IllegalStateException::class.java) { oobRunner.decryptData(encryptedMessage) }
  }

  @Test
  fun decryptWithShortMessage_throwsException() {
    oobRunner.sendOobData()
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

    override fun completeOobDataExchange(oobData: ByteArray): Boolean {
      return oobDataExchangeResult
    }

    override fun interrupt() {
      isInterrupted = true
    }
  }
}
