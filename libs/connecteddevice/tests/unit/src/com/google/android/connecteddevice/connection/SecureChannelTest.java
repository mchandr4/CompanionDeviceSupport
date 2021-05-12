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

import static com.google.android.connecteddevice.connection.SecureChannel.CHANNEL_ERROR_INVALID_HANDSHAKE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.DeviceMessage.OperationType;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.encryptionrunner.EncryptionRunnerFactory;
import com.google.android.encryptionrunner.HandshakeException;
import com.google.android.encryptionrunner.Key;
import java.security.SignatureException;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class SecureChannelTest {
  @Mock private DeviceMessageStream mockStream;
  @Mock private SecureChannel.Callback mockCallback;

  private Key fakeKey;

  private SecureChannel secureChannel;

  @Before
  public void setUp() throws SignatureException {
    MockitoAnnotations.initMocks(this);

    fakeKey =
        spy(
            new Key() {
              @Override
              public byte[] asBytes() {
                return new byte[0];
              }

              @Override
              public byte[] encryptData(byte[] data) {
                return data;
              }

              @Override
              public byte[] decryptData(byte[] encryptedData) {
                return encryptedData;
              }

              @Override
              public byte[] getUniqueSession() {
                return new byte[0];
              }
            });

    secureChannel =
        new SecureChannel(mockStream, EncryptionRunnerFactory.newFakeRunner()) {
          @Override
          void processHandshake(byte[] message) {}
        };
    secureChannel.setEncryptionKey(fakeKey);
  }

  @Test
  public void processMessage_doesNothingForUnencryptedMessage() throws SignatureException {
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ false,
            OperationType.CLIENT_MESSAGE,
            payload);
    secureChannel.processMessage(message);
    assertThat(message.getMessage()).isEqualTo(payload);
    verify(fakeKey, never()).decryptData(any());
  }

  @Test
  public void processMessage_decryptsEncryptedMessage() throws SignatureException {
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ true,
            OperationType.CLIENT_MESSAGE,
            payload);
    secureChannel.processMessage(message);
    verify(fakeKey).decryptData(any());
  }

  @Test
  public void processMessage_onMessageReceivedErrorForEncryptedMessageWithNoKey()
      throws InterruptedException {
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ true,
            OperationType.CLIENT_MESSAGE,
            ByteUtils.randomBytes(10));

    secureChannel.setEncryptionKey(null);
    secureChannel.registerCallback(mockCallback);
    secureChannel.processMessage(message);

    verify(mockCallback).onMessageReceivedError(isNull());
    assertThat(message.getMessage()).isNull();
  }

  @Test
  public void onMessageReceived_onEstablishSecureChannelFailureBadHandshakeMessage() {
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ true,
            OperationType.ENCRYPTION_HANDSHAKE,
            ByteUtils.randomBytes(10));

    secureChannel.setEncryptionKey(null);
    secureChannel.registerCallback(mockCallback);
    secureChannel.onMessageReceived(message);

    verify(mockCallback).onEstablishSecureChannelFailure(CHANNEL_ERROR_INVALID_HANDSHAKE);
  }

  @Test
  public void onMessageReceived_onMessageReceivedNotIssuedForNullMessage() {
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ false,
            OperationType.CLIENT_MESSAGE,
            /* message= */ null);

    secureChannel.registerCallback(mockCallback);
    secureChannel.onMessageReceived(message);

    verify(mockCallback, never()).onMessageReceived(any());
  }

  @Test
  public void onMessageReceived_processHandshakeExceptionIssuesSecureChannelFailureCallback() {
    SecureChannel secureChannel =
        new SecureChannel(mockStream, EncryptionRunnerFactory.newFakeRunner()) {
          @Override
          void processHandshake(byte[] message) throws HandshakeException {
            throw new HandshakeException("test");
          }
        };
    secureChannel.registerCallback(mockCallback);
    DeviceMessage message =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ true,
            OperationType.ENCRYPTION_HANDSHAKE,
            ByteUtils.randomBytes(10));

    secureChannel.onMessageReceived(message);

    verify(mockCallback).onEstablishSecureChannelFailure(CHANNEL_ERROR_INVALID_HANDSHAKE);
  }

  @Test
  public void decompressMessage_returnsOriginalMessageIfOriginalSizeIsZero() {
    byte[] message = ByteUtils.randomBytes(10);
    DeviceMessage deviceMessage =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ false,
            OperationType.CLIENT_MESSAGE,
            message);
    deviceMessage.setOriginalMessageSize(0);
    assertThat(secureChannel.decompressMessage(deviceMessage)).isTrue();
    assertThat(deviceMessage.getMessage()).isEqualTo(message);
  }

  @Test
  public void compressMessage_returnsCompressedMessageWithOriginalSize() {
    byte[] message = new byte[100];
    DeviceMessage deviceMessage =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ false,
            OperationType.CLIENT_MESSAGE,
            message);
    secureChannel.compressMessage(deviceMessage);
    assertThat(deviceMessage.getMessage()).isNotEqualTo(message);
    assertThat(deviceMessage.getOriginalMessageSize()).isEqualTo(message.length);
  }

  @Test
  public void compressedMessageCanBeDecompressed() {
    byte[] message = new byte[100];
    DeviceMessage deviceMessage =
        new DeviceMessage(
            UUID.randomUUID(),
            /* isMessageEncrypted= */ false,
            OperationType.CLIENT_MESSAGE,
            message);
    secureChannel.compressMessage(deviceMessage);
    assertThat(secureChannel.decompressMessage(deviceMessage)).isTrue();
    assertThat(deviceMessage.getMessage()).isEqualTo(message);
  }
}
