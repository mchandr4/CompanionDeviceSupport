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

package com.google.android.connecteddevice.connection;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange;
import com.google.android.companionprotos.VersionExchangeProto.VersionExchange;
import com.google.android.connecteddevice.connection.DeviceMessageStream.DataReceivedListener;
import com.google.android.connecteddevice.connection.DeviceMessageStream.MessageReceivedErrorListener;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class ConnectionResolverTest {

  private static final int WRITE_SIZE = 500;

  private DeviceMessageStream stream;
  private ConnectionResolver connectionResolver;
  @Mock private MessageReceivedErrorListener mockErrorListener;
  @Mock private DataReceivedListener mockDataReceivedListener;
  @Mock private ConnectionResolver.ConnectionResolvedListener mockConnectionResolvedListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    stream =
        spy(
            new DeviceMessageStream(WRITE_SIZE) {
              @Override
              protected void send(byte[] data) {}
            });
    stream.setMessageReceivedErrorListener(mockErrorListener);
    stream.setDataReceivedListener(mockDataReceivedListener);
    connectionResolver = new ConnectionResolver(stream, /* isCapabilitiesEligible= */ true);
  }

  @Test
  public void resolveConnection_invalidVersionExchangeMessage() {
    connectionResolver.resolveConnection(mockConnectionResolvedListener);
    connectionResolver.onMessageReceived(ByteUtils.randomBytes(WRITE_SIZE));
    verify(mockErrorListener).onMessageReceivedError(any(InvalidProtocolBufferException.class));
    verify(stream, times(0)).send(any());
    verify(mockConnectionResolvedListener, times(0)).onConnectionResolved(any());
  }

  @Test
  public void resolveConnection_invalidVersionNumbers() {
    connectionResolver.resolveConnection(mockConnectionResolvedListener);
    int invalidVersion = 0;
    byte[] phoneVersionBytes =
        VersionExchange.newBuilder()
            .setMinSupportedMessagingVersion(invalidVersion)
            .setMaxSupportedMessagingVersion(invalidVersion)
            .setMinSupportedSecurityVersion(invalidVersion)
            .setMaxSupportedSecurityVersion(invalidVersion)
            .build()
            .toByteArray();
    stream.onDataReceived(phoneVersionBytes);
    verify(mockErrorListener).onMessageReceivedError(any(IllegalStateException.class));
    verify(stream, times(0)).send(any());
    verify(mockConnectionResolvedListener, times(0)).onConnectionResolved(any());
  }

  @Test
  public void resolveConnection_securityVersion2_success() {
    connectionResolver.resolveConnection(mockConnectionResolvedListener);

    byte[] phoneVersionBytes = VersionExchange.newBuilder()
        .setMinSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMaxSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMinSupportedSecurityVersion(2)
        .setMaxSupportedSecurityVersion(2)
        .build()
        .toByteArray();
    byte[] expectedVersionBytes = VersionExchange.newBuilder()
        .setMinSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMaxSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMinSupportedSecurityVersion(ConnectionResolver.MIN_SECURITY_VERSION)
        .setMaxSupportedSecurityVersion(ConnectionResolver.MAX_SECURITY_VERSION)
        .build()
        .toByteArray();
    connectionResolver.onMessageReceived(phoneVersionBytes);
    verify(stream).writeRawBytes(expectedVersionBytes);

    ArgumentCaptor<ConnectionResolver.ResolvedConnection> resolvedConnectionCaptor =
        ArgumentCaptor.forClass(ConnectionResolver.ResolvedConnection.class);
    verify(mockConnectionResolvedListener).onConnectionResolved(resolvedConnectionCaptor.capture());

    ConnectionResolver.ResolvedConnection resolvedConnection = resolvedConnectionCaptor.getValue();
    assertThat(resolvedConnection.messagingVersion())
        .isEqualTo(ConnectionResolver.MESSAGING_VERSION);
    assertThat(resolvedConnection.securityVersion())
        .isEqualTo(2);
    assertThat(resolvedConnection.oobChannels()).isNull();
  }

  @Test
  @Ignore("b/175066810")
  public void resolveConnection_isNotCapabilitiesEligible_capabilitesAreNotExchanged() {
    connectionResolver = new ConnectionResolver(stream, /* isCapabilitiesEligible= */ false);
    connectionResolver.resolveConnection(mockConnectionResolvedListener);

    byte[] phoneVersionBytes = createVersionExchangeMessage().toByteArray();
    connectionResolver.onMessageReceived(phoneVersionBytes);
    verify(stream).send(phoneVersionBytes);
    ArgumentCaptor<ConnectionResolver.ResolvedConnection> resolvedConnectionCaptor =
        ArgumentCaptor.forClass(ConnectionResolver.ResolvedConnection.class);
    verify(mockConnectionResolvedListener).onConnectionResolved(resolvedConnectionCaptor.capture());

    ConnectionResolver.ResolvedConnection resolvedConnection = resolvedConnectionCaptor.getValue();
    assertThat(resolvedConnection.messagingVersion())
        .isEqualTo(ConnectionResolver.MESSAGING_VERSION);
    assertThat(resolvedConnection.securityVersion())
        .isEqualTo(ConnectionResolver.MAX_SECURITY_VERSION);
    assertThat(resolvedConnection.oobChannels()).isNull();
  }

  @Test
  @Ignore("b/175066810")
  public void resolveConnection_securityVersion3_success() {
    connectionResolver.resolveConnection(mockConnectionResolvedListener);

    byte[] phoneVersionBytes = createVersionExchangeMessage().toByteArray();
    connectionResolver.onMessageReceived(phoneVersionBytes);
    verify(stream).send(phoneVersionBytes);

    byte[] phoneCapabilitiesBytes = CapabilitiesExchange.getDefaultInstance().toByteArray();
    connectionResolver.onMessageReceived(phoneCapabilitiesBytes);
    verify(stream).send(phoneCapabilitiesBytes);

    ArgumentCaptor<ConnectionResolver.ResolvedConnection> resolvedConnectionCaptor =
        ArgumentCaptor.forClass(ConnectionResolver.ResolvedConnection.class);
    verify(mockConnectionResolvedListener).onConnectionResolved(resolvedConnectionCaptor.capture());

    ConnectionResolver.ResolvedConnection resolvedConnection = resolvedConnectionCaptor.getValue();
    assertThat(resolvedConnection.messagingVersion())
        .isEqualTo(ConnectionResolver.MESSAGING_VERSION);
    assertThat(resolvedConnection.securityVersion())
        .isEqualTo(ConnectionResolver.MAX_SECURITY_VERSION);
    assertThat(resolvedConnection.oobChannels()).isNotNull();
  }

  @Test
  @Ignore("b/175066810")
  public void processCapabilitiesExchange_invalidCapabilitiesExchangeMessage() {
    connectionResolver.resolveConnection(mockConnectionResolvedListener);

    byte[] phoneVersionBytes = createVersionExchangeMessage().toByteArray();
    connectionResolver.onMessageReceived(phoneVersionBytes);
    verify(stream).send(phoneVersionBytes);

    stream.onDataReceived(ByteUtils.randomBytes(WRITE_SIZE));
    verify(mockErrorListener).onMessageReceivedError(any(InvalidProtocolBufferException.class));
    verifyNoMoreInteractions(stream);
    verify(mockConnectionResolvedListener, times(0)).onConnectionResolved(any());
  }

  private static VersionExchange createVersionExchangeMessage() {
    return VersionExchange.newBuilder()
        .setMinSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMaxSupportedMessagingVersion(ConnectionResolver.MESSAGING_VERSION)
        .setMinSupportedSecurityVersion(ConnectionResolver.MIN_SECURITY_VERSION)
        .setMaxSupportedSecurityVersion(ConnectionResolver.MAX_SECURITY_VERSION)
        .build();
  }
}
