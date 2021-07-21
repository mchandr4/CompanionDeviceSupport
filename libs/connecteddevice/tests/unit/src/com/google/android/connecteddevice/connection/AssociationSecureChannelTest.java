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

package com.google.android.connecteddevice.connection;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.encryptionrunner.EncryptionRunner;
import com.google.android.encryptionrunner.EncryptionRunnerFactory;
import com.google.android.encryptionrunner.FakeEncryptionRunner;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class AssociationSecureChannelTest {
  private static final UUID CLIENT_DEVICE_ID =
      UUID.fromString("a5645523-3280-410a-90c1-582a6c6f4969");
  private static final UUID SERVER_DEVICE_ID =
      UUID.fromString("a29f0c74-2014-4b14-ac02-be6ed15b545a");
  private static final byte[] CLIENT_SECRET = ByteUtils.randomBytes(32);

  @Mock private ConnectedDeviceStorage storageMock;

  @Mock
  private AssociationSecureChannel.ShowVerificationCodeListener showVerificationCodeListenerMock;

  @Mock private SecureChannel.Callback channelCallback;

  @Mock private DeviceMessageStream streamMock;
  private AssociationSecureChannel associationSecureChannel;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(storageMock.getUniqueId()).thenReturn(SERVER_DEVICE_ID);
  }

  @Test
  public void testEncryptionHandshake_association() {
    setupAssociationSecureChannel(channelCallback, EncryptionRunnerFactory.newFakeRunner());
    ArgumentCaptor<String> deviceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);

    initHandshakeMessage();
    verify(streamMock).writeMessage(messageCaptor.capture());
    byte[] response = messageCaptor.getValue().getMessage();
    assertThat(response).isEqualTo(FakeEncryptionRunner.INIT_RESPONSE);

    respondToContinueMessage();
    verify(showVerificationCodeListenerMock).showVerificationCode(anyString());

    associationSecureChannel.notifyOutOfBandAccepted();
    sendDeviceId();

    verify(channelCallback).onDeviceIdReceived(deviceIdCaptor.capture());
    verify(streamMock, times(2)).writeMessage(messageCaptor.capture());
    DeviceMessage deviceIdMessage = messageCaptor.getValue();
    associationSecureChannel.processMessage(deviceIdMessage);
    assertThat(deviceIdMessage.getMessage()).isEqualTo(ByteUtils.uuidToBytes(SERVER_DEVICE_ID));
    assertThat(deviceIdCaptor.getValue()).isEqualTo(CLIENT_DEVICE_ID.toString());
    verify(storageMock).saveEncryptionKey(eq(CLIENT_DEVICE_ID.toString()), any());
    verify(storageMock).saveChallengeSecret(CLIENT_DEVICE_ID.toString(), CLIENT_SECRET);
    verify(channelCallback).onSecureChannelEstablished();
  }

  @Test
  public void testEncryptionHandshake_association_wrongInitHandshakeMessage() {
    setupAssociationSecureChannel(channelCallback, EncryptionRunnerFactory.newFakeRunner());

    // Wrong init handshake message
    respondToContinueMessage();

    verify(channelCallback)
        .onEstablishSecureChannelFailure(eq(SecureChannel.CHANNEL_ERROR_INVALID_HANDSHAKE));
  }

  @Test
  public void testEncryptionHandshake_association_wrongRespondToContinueMessage()
      throws InterruptedException {
    setupAssociationSecureChannel(channelCallback, EncryptionRunnerFactory.newFakeRunner());

    initHandshakeMessage();

    // Wrong respond to continue message
    initHandshakeMessage();

    verify(channelCallback)
        .onEstablishSecureChannelFailure(eq(SecureChannel.CHANNEL_ERROR_INVALID_HANDSHAKE));
  }

  private void setupAssociationSecureChannel(
      SecureChannel.Callback callback, EncryptionRunner encryptionRunner) {
    associationSecureChannel =
        new AssociationSecureChannel(streamMock, storageMock, encryptionRunner);
    associationSecureChannel.registerCallback(callback);
    associationSecureChannel.setShowVerificationCodeListener(showVerificationCodeListenerMock);
  }

  private void sendDeviceId() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ true,
            DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE,
            FakeEncryptionRunner.encryptDataWithFakeKey(
                ByteUtils.concatByteArrays(
                    ByteUtils.uuidToBytes(CLIENT_DEVICE_ID), CLIENT_SECRET)));
    associationSecureChannel.onMessageReceived(message);
  }

  private void initHandshakeMessage() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ false,
            DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE,
            FakeEncryptionRunner.INIT_MESSAGE);
    associationSecureChannel.onMessageReceived(message);
  }

  private void respondToContinueMessage() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ false,
            DeviceMessage.OperationType.ENCRYPTION_HANDSHAKE,
            FakeEncryptionRunner.CLIENT_RESPONSE);
    associationSecureChannel.onMessageReceived(message);
  }
}
