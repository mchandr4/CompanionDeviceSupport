/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.connecteddevice.notificationmsg;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.notificationmsg.common.CompositeKey;
import com.google.android.connecteddevice.notificationmsg.common.ConversationKey;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.PhoneToCarMessage;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class NotificationMsgFeatureTest {
  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();

  private static final String DEVICE_ID = UUID.randomUUID().toString();
  private static final PhoneToCarMessage PHONE_TO_CAR_MESSAGE =
      PhoneToCarMessage.getDefaultInstance();

  @Mock
  private NotificationMsgDelegate mNotificationMsgDelegate;
  @Mock
  private ConnectedDevice mConnectedDevice;

  private NotificationMsgFeature mNotificationMsgFeature;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    when(mConnectedDevice.getDeviceId()).thenReturn(DEVICE_ID);

    mNotificationMsgFeature = new NotificationMsgFeature(context, mNotificationMsgDelegate);
  }

  @Test
  public void startShouldClearInternalMemory() {
    mNotificationMsgFeature.start();

    ArgumentCaptor<Predicate<CompositeKey>> predicateArgumentCaptor = ArgumentCaptor.forClass(
        Predicate.class);
    verify(mNotificationMsgDelegate).cleanupMessagesAndNotifications(
        predicateArgumentCaptor.capture());

    // There's no way to test if two predicates have the same logic, so test the logic itself.
    // The expected predicate should return true for any CompositeKey. Since CompositeKey is
    // an abstract class, test with a class that extends it.
    ConversationKey deviceKey = new ConversationKey(DEVICE_ID, "subKey");
    ConversationKey deviceKey1 = new ConversationKey("NOT_DEVICE_ID", "subKey");
    assertThat(predicateArgumentCaptor.getValue().test(deviceKey)).isTrue();
    assertThat(predicateArgumentCaptor.getValue().test(deviceKey1)).isTrue();
    assertThat(predicateArgumentCaptor.getValue().test(null)).isTrue();
  }

  @Test
  public void stopShouldDestroyDelegate() {
    mNotificationMsgFeature.start();
    mNotificationMsgFeature.stop();
    verify(mNotificationMsgDelegate).onDestroy();
  }

  @Test
  public void onMessageReceivedShouldPassMessageToDelegate() {
    startWithSecureDevice();

    mNotificationMsgFeature.onMessageReceived(mConnectedDevice,
        PHONE_TO_CAR_MESSAGE.toByteArray());
    verify(mNotificationMsgDelegate).onMessageReceived(mConnectedDevice, PHONE_TO_CAR_MESSAGE);
  }

  @Test
  public void onMessageReceivedShouldCheckDeviceConnection() {
    when(mConnectedDevice.hasSecureChannel()).thenReturn(false);
    when(mConnectedDevice.isAssociatedWithDriver()).thenReturn(true);
    mNotificationMsgFeature.start();

    mNotificationMsgFeature.onMessageReceived(mConnectedDevice,
        PHONE_TO_CAR_MESSAGE.toByteArray());
    verify(mNotificationMsgDelegate, never()).onMessageReceived(mConnectedDevice,
        PHONE_TO_CAR_MESSAGE);
  }

  @Test
  public void unknownDeviceDisconnectedShouldDoNothing() {
    when(mConnectedDevice.hasSecureChannel()).thenReturn(true);
    when(mConnectedDevice.isAssociatedWithDriver()).thenReturn(true);
    mNotificationMsgFeature.start();

    mNotificationMsgFeature.onDeviceDisconnected(mConnectedDevice);
    verify(mNotificationMsgDelegate, never()).onDeviceDisconnected(DEVICE_ID);
  }

  @Test
  public void secureDeviceDisconnectedShouldAlertDelegate() {
    startWithSecureDevice();

    mNotificationMsgFeature.onDeviceDisconnected(mConnectedDevice);
    verify(mNotificationMsgDelegate).onDeviceDisconnected(DEVICE_ID);
  }

  private void startWithSecureDevice() {
    when(mConnectedDevice.hasSecureChannel()).thenReturn(true);
    when(mConnectedDevice.isAssociatedWithDriver()).thenReturn(true);
    mNotificationMsgFeature.start();
    mNotificationMsgFeature.onSecureChannelEstablished(mConnectedDevice);
  }
}
