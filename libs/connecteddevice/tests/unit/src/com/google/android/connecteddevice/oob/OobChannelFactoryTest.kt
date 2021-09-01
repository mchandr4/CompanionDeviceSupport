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
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobChannelFactoryTest {
  private val mockSppBinder: ConnectedDeviceSppDelegateBinder = mock()
  private val factory = OobChannelFactory(mockSppBinder)

  @Test
  fun createOobChannel_supportedType_returnCorrectChannelType() {
    val supportedType = OobChannelType.BT_RFCOMM
    val oobChannel = factory.createOobChannel(supportedType)

    assertThat(oobChannel).isInstanceOf(BluetoothRfcommChannel::class.java)
  }

  @Test
  fun createOobChannel_unsupportedChannelType_throwException() {
    try {
      factory.createOobChannel(OobChannelType.OOB_CHANNEL_UNKNOWN)
      fail("Expected exception did not throw.")
    } catch (e: IllegalArgumentException) {}
  }
}
