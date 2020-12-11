/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Central;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage.Event;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class CentralConnectionStatusTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private CentralConnectionStatus.Callback mockCallback;

  private UUID centralId = null;
  private Central central = null;
  private CentralConnectionStatus status = null;

  @Before
  public void setUp() {
    centralId = UUID.randomUUID();
    central = Central.newBuilder().setIdentifier(centralId.toString()).build();
    status = new CentralConnectionStatus(central, Executors.newSingleThreadScheduledExecutor());
    status.setCallback(mockCallback);
  }

  @Test
  public void isConnected_initialValue() {
    assertThat(status.isConnected()).isFalse();
  }

  @Test
  public void handleSubscriptionEvent_cannotHandleDifferentCentral() {
    Central anotherCentral =
        Central.newBuilder().setIdentifier(UUID.randomUUID().toString()).build();
    NotifyCentralSubscriptionMessage message =
        NotifyCentralSubscriptionMessage.newBuilder().setCentral(anotherCentral).build();

    assertThat(status.handleSubscriptionEvent(message)).isFalse();
  }

  @Test
  public void handleSubscriptionEvent_subscription() {
    ArgumentCaptor<Central> centralCaptor = ArgumentCaptor.forClass(Central.class);

    assertThat(status.handleSubscriptionEvent(createSubscriptionMessage(true))).isTrue();

    verify(mockCallback).onCentralConnected(centralCaptor.capture());
    assertThat(centralCaptor.getValue().getIdentifier()).isEqualTo(centralId.toString());
    assertThat(status.isConnected()).isTrue();
  }

  @Test
  public void handleSubscriptionEvent_temporaryUnsubscription() {
    // First ensure the central is connected.
    assertThat(status.handleSubscriptionEvent(createSubscriptionMessage(true))).isTrue();

    // Unsubscription is followed by an immediate subscription, which should cancel the disconnect.
    assertThat(status.handleSubscriptionEvent(createSubscriptionMessage(false))).isTrue();
    assertThat(status.handleSubscriptionEvent(createSubscriptionMessage(true))).isTrue();

    verify(mockCallback, never()).onCentralDisconnected(any());
  }

  private NotifyCentralSubscriptionMessage createSubscriptionMessage(boolean isSubscribing) {
    Event event;
    if (isSubscribing) {
      event = Event.SUBSCRIBED;
    } else {
      event = Event.UNSUBSCRIBED;
    }
    return NotifyCentralSubscriptionMessage.newBuilder()
        .setCentral(central)
        .setEvent(event)
        .build();
  }
}
