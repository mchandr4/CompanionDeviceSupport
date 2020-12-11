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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.OperationProto.OperationType;
import com.google.android.connecteddevice.oob.OobConnectionManager;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.util.ByteUtils;
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
public class OobAssociationSecureChannelTest {
  private static final UUID CLIENT_DEVICE_ID =
      UUID.fromString("a5645523-3280-410a-90c1-582a6c6f4969");

  private static final UUID SERVER_DEVICE_ID =
      UUID.fromString("a29f0c74-2014-4b14-ac02-be6ed15b545a");

  private static final byte[] CLIENT_SECRET = ByteUtils.randomBytes(32);

  @Mock private DeviceMessageStream streamMock;

  @Mock private ConnectedDeviceStorage storageMock;

  @Mock private OobConnectionManager oobConnectionManagerMock;

  @Mock private SecureChannel.Callback channelCallback;

  private OobAssociationSecureChannel channel;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(storageMock.getUniqueId()).thenReturn(SERVER_DEVICE_ID);
  }

  @Test
  public void testEncryptionHandshake_oobAssociation() throws Exception {
    setupOobAssociationSecureChannel(channelCallback);
    ArgumentCaptor<String> deviceIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);

    initHandshakeMessage();
    verify(streamMock).writeMessage(messageCaptor.capture(), any());
    byte[] response = messageCaptor.getValue().getMessage();
    assertThat(response).isEqualTo(FakeEncryptionRunner.INIT_RESPONSE);
    reset(streamMock);
    respondToContinueMessage();
    verify(streamMock).writeMessage(messageCaptor.capture(), any());
    byte[] oobCodeResponse = messageCaptor.getValue().getMessage();
    assertThat(oobCodeResponse).isEqualTo(FakeEncryptionRunner.VERIFICATION_CODE.getBytes(UTF_8));
    respondToOobCode();
    sendDeviceId();
    verify(channelCallback).onDeviceIdReceived(deviceIdCaptor.capture());
    verify(streamMock, times(2)).writeMessage(messageCaptor.capture(), any());
    byte[] deviceIdMessage = messageCaptor.getValue().getMessage();
    assertThat(deviceIdMessage).isEqualTo(ByteUtils.uuidToBytes(SERVER_DEVICE_ID));
    assertThat(deviceIdCaptor.getValue()).isEqualTo(CLIENT_DEVICE_ID.toString());
    verify(storageMock).saveEncryptionKey(eq(CLIENT_DEVICE_ID.toString()), any());
    verify(storageMock).saveChallengeSecret(CLIENT_DEVICE_ID.toString(), CLIENT_SECRET);

    verify(channelCallback).onSecureChannelEstablished();
  }

  private void setupOobAssociationSecureChannel(SecureChannel.Callback callback) throws Exception {
    channel =
        new OobAssociationSecureChannel(
            streamMock,
            storageMock,
            oobConnectionManagerMock,
            EncryptionRunnerFactory.newOobFakeRunner());
    channel.registerCallback(callback);

    when(oobConnectionManagerMock.encryptVerificationCode(any()))
        .thenReturn(FakeEncryptionRunner.VERIFICATION_CODE.getBytes(UTF_8));
    when(oobConnectionManagerMock.decryptVerificationCode(any()))
        .thenReturn(FakeEncryptionRunner.VERIFICATION_CODE.getBytes(UTF_8));
  }

  private void sendDeviceId() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ true,
            ByteUtils.concatByteArrays(ByteUtils.uuidToBytes(CLIENT_DEVICE_ID), CLIENT_SECRET));
    channel.onMessageReceived(message, OperationType.ENCRYPTION_HANDSHAKE);
  }

  private void initHandshakeMessage() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ false,
            FakeEncryptionRunner.INIT_MESSAGE);
    channel.onMessageReceived(message, OperationType.ENCRYPTION_HANDSHAKE);
  }

  private void respondToContinueMessage() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ false,
            FakeEncryptionRunner.CLIENT_RESPONSE);
    channel.onMessageReceived(message, OperationType.ENCRYPTION_HANDSHAKE);
  }

  private void respondToOobCode() {
    DeviceMessage message =
        new DeviceMessage(
            /* recipient= */ null,
            /* isMessageEncrypted= */ false,
            FakeEncryptionRunner.VERIFICATION_CODE.getBytes(UTF_8));
    channel.onMessageReceived(message, OperationType.ENCRYPTION_HANDSHAKE);
  }
}
