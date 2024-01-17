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

package com.google.android.connecteddevice.ping

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.PeriodicPingProto.PeriodicPingMessage
import com.google.android.companionprotos.PeriodicPingProto.PeriodicPingMessage.MessageType
import com.google.android.connecteddevice.api.FakeConnector
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class PeriodicPingFeatureTest {
  private lateinit var feature: PeriodicPingFeature
  private val fakeConnector = spy(FakeConnector())
  private val device =
    ConnectedDevice(
      UUID.randomUUID().toString(),
      /* deviceName= */ null,
      /* belongsToDriver= */ true,
      /* hasSecureChannel= */ true
    )

  @Before
  fun setup() {
    feature = PeriodicPingFeature(fakeConnector)
  }

  @Test
  fun start_connectsConnector() {
    feature.start()

    verify(fakeConnector).connect()
  }

  @Test
  fun stop_disconnectsConnector() {
    feature.stop()

    verify(fakeConnector).disconnect()
  }

  @Test
  fun sendPing_onSecureChannelEstablished_sendImmediately() {
    feature.start()
    fakeConnector.callback?.onSecureChannelEstablished(device)

    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // First ping will be sent immediately after a secure channel with device is established.
    verifySendingPing(1)
  }

  @Test
  fun sendPing_onSecureChannelEstablished_sendPeriodically() {
    feature.start()
    val periods = 3
    fakeConnector.callback?.onSecureChannelEstablished(device)

    Shadows.shadowOf(Looper.getMainLooper())
      .idleFor(periods * PeriodicPingFeature.PING_DELAY, TimeUnit.MILLISECONDS)

    // First ping is sent immediately and following pings are sent periodically.
    verifySendingPing(1 + periods)
  }

  @Test
  fun sendPing_stopSending_onDeviceDisconnected() {
    feature.start()
    fakeConnector.callback?.onSecureChannelEstablished(device)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    fakeConnector.callback?.onDeviceDisconnected(device)
    Shadows.shadowOf(Looper.getMainLooper())
      .idleFor(PeriodicPingFeature.PING_DELAY, TimeUnit.MILLISECONDS)

    // After the device is disconnected, scheduled ping tasks should be canceled.
    verifySendingPing(1)
  }

  private fun verifySendingPing(count: Int) {
    val captor = argumentCaptor<ByteArray>()
    verify(fakeConnector, times(count)).sendMessageSecurely(eq(device), captor.capture())
    for (value in captor.allValues) {
      val message = PeriodicPingMessage.parseFrom(value)
      assertThat(message.messageType).isEqualTo(MessageType.PING)
    }
  }
}
