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
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDevice
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassThroughChannelTest {
  private val channel = PassThroughChannel()
  private val mockCallback: OobChannel.Callback = mock()
  private val testProtocolDevice = ProtocolDevice(TestConnectionProtocol(), "testProtocolId")

  @Test
  fun startOobExchange_directlyPassThrough_invokeCallback() {
    assertThat(channel.completeOobDataExchange(testProtocolDevice, mockCallback)).isTrue()
    verify(mockCallback).onOobExchangeSuccess()
  }
}

private class TestConnectionProtocol : ConnectionProtocol() {
  override fun isDeviceVerificationRequired() = false

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback
  ) {}
  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback
  ) {}

  override fun stopAssociationDiscovery() {}
  override fun stopConnectionDiscovery(id: ParcelUuid) {}
  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {}
  override fun disconnectDevice(protocolId: String) {}
  override fun getMaxWriteSize(protocolId: String): Int {
    return 0
  }
}
