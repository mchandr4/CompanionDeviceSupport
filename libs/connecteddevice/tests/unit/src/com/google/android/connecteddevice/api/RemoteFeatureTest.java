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

package com.google.android.connecteddevice.api;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.Connector.AppNameCallback;
import com.google.android.connecteddevice.api.Connector.QueryCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteFeatureTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  private final ParcelUuid featureId = new ParcelUuid(UUID.randomUUID());

  private final RemoteFeature spyFeature = spy(new RemoteFeature(context, featureId) {});

  private final Connector fakeConnector = spy(new FakeConnector());

  @Before
  public void setUp() {
    remoteFeature.start();
  }

  @Test
  public void start_connectsConnector() {
    verify(fakeConnector).connect();
  }

  @Test
  public void stop_disconnectsConnector() {
    remoteFeature.stop();

    verify(fakeConnector).disconnect();
  }

  @Test
  public void onNotReady_invokedWhenConnectorDisconnects() {
    fakeConnector.getCallback().onDisconnected();

    verify(spyFeature).onNotReady();
  }

  @Test
  public void onNotReady_invokedWhenConnectorFailsToConnect() {
    fakeConnector.getCallback().onFailedToConnect();

    verify(spyFeature).onNotReady();
  }

  @Test
  public void onReady_invokedWhenConnectorConnects() {
    verify(spyFeature).onReady();
  }

  @Test
  public void onAssociatedDeviceAdded_invokedWhenDeviceAdded() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            /* deviceAddress= */ "",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);

    fakeConnector.getCallback().onAssociatedDeviceAdded(device);

    verify(spyFeature).onAssociatedDeviceAdded(device);
  }

  @Test
  public void onAssociatedDeviceRemoved_invokedWhenDeviceRemoved() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            /* deviceAddress= */ "",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);

    fakeConnector.getCallback().onAssociatedDeviceRemoved(device);

    verify(spyFeature).onAssociatedDeviceRemoved(device);
  }

  @Test
  public void onAssociatedDeviceUpdated_invokedWhenDeviceUpdated() {
    AssociatedDevice device =
        new AssociatedDevice(
            UUID.randomUUID().toString(),
            /* deviceAddress= */ "",
            /* deviceName= */ null,
            /* isConnectionEnabled= */ true);

    fakeConnector.getCallback().onAssociatedDeviceUpdated(device);

    verify(spyFeature).onAssociatedDeviceUpdated(device);
  }

  @Test
  public void onDeviceError_invokedWhenThereIsADeviceError() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    int error = -1;

    fakeConnector.getCallback().onDeviceError(device, error);

    verify(spyFeature).onDeviceError(device, error);
  }

  @Test
  public void onQueryReceived_invokedWhenQueryReceived() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    int queryId = 2;
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);

    fakeConnector.getCallback().onQueryReceived(device, queryId, request, parameters);

    verify(spyFeature).onQueryReceived(device, queryId, request, parameters);
  }

  @Test
  public void onMessageReceived_invokedWhenMessageReceived() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    byte[] message = ByteUtils.randomBytes(10);

    fakeConnector.getCallback().onMessageReceived(device, message);

    verify(spyFeature).onMessageReceived(device, message);
  }

  @Test
  public void onMessageFailedToSend_invokedOnMessageSendFailure() {
    String deviceId = UUID.randomUUID().toString();
    byte[] message = ByteUtils.randomBytes(10);
    boolean isTransient = true;

    fakeConnector.getCallback().onMessageFailedToSend(deviceId, message, isTransient);

    verify(spyFeature).onMessageFailedToSend(deviceId, message, isTransient);
  }

  @Test
  public void onSecureChannelEstablished_invokedWhenSecureChannelEstablished() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);

    fakeConnector.getCallback().onSecureChannelEstablished(device);

    verify(spyFeature).onSecureChannelEstablished(device);
  }

  @Test
  public void onDeviceConnected_invokedWhenDeviceConnects() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);

    fakeConnector.getCallback().onDeviceConnected(device);

    verify(spyFeature).onDeviceConnected(device);
  }

  @Test
  public void onDeviceDisconnected_invokedWhenDeviceDisconnects() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);

    fakeConnector.getCallback().onDeviceDisconnected(device);

    verify(spyFeature).onDeviceDisconnected(device);
  }

  @Test
  public void getContext_returnsContext() {
    assertThat(remoteFeature.getContext()).isEqualTo(context);
  }

  @Test
  public void getFeatureId_returnsFeatureId() {
    assertThat(remoteFeature.getFeatureId()).isEqualTo(featureId);
  }

  @Test
  public void sendMessageSecurelyWithId_sendsMessage() {
    String deviceId = UUID.randomUUID().toString();
    byte[] message = ByteUtils.randomBytes(10);

    remoteFeature.sendMessageSecurely(deviceId, message);

    verify(fakeConnector).sendMessageSecurely(deviceId, message);
  }

  @Test
  public void sendMessageSecurely_sendsMessage() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    byte[] message = ByteUtils.randomBytes(10);

    remoteFeature.sendMessageSecurely(device, message);

    verify(fakeConnector).sendMessageSecurely(device, message);
  }

  @Test
  public void sendQuerySecurelyWithId_sendsQuery() {
    String deviceId = UUID.randomUUID().toString();
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = mock(QueryCallback.class);

    remoteFeature.sendQuerySecurely(deviceId, request, parameters, callback);

    verify(fakeConnector).sendQuerySecurely(deviceId, request, parameters, callback);
  }

  @Test
  public void sendQuerySecurely_sendsQuery() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = mock(QueryCallback.class);

    remoteFeature.sendQuerySecurely(device, request, parameters, callback);

    verify(fakeConnector).sendQuerySecurely(device, request, parameters, callback);
  }

  @Test
  public void respondToQuerySecurely_respondsToQuery() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    int queryId = 2;
    boolean success = true;
    byte[] response = ByteUtils.randomBytes(10);

    remoteFeature.respondToQuerySecurely(device, queryId, success, response);

    verify(fakeConnector).respondToQuerySecurely(device, queryId, success, response);
  }

  @Test
  public void getConnectedDeviceById_returnsConnectorDeviceResponse() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    when(fakeConnector.getConnectedDeviceById(device.getDeviceId())).thenReturn(device);

    assertThat(remoteFeature.getConnectedDeviceById(device.getDeviceId())).isEqualTo(device);
  }

  @Test
  public void getConnectedDevices_returnsConnectorDevices() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    when(fakeConnector.getConnectedDevices()).thenReturn(ImmutableList.of(device));

    assertThat(remoteFeature.getConnectedDevices()).containsExactly(device);
  }

  @Test
  public void getCompanionApplicationName_retreivesConnectorApplicationName() {
    ConnectedDevice device =
        new ConnectedDevice(
            UUID.randomUUID().toString(),
            /* deviceName= */ "",
            /* belongsToDriver= */ true,
            /* hasSecureChannel= */ true);
    AppNameCallback callback = mock(AppNameCallback.class);

    remoteFeature.getCompanionApplicationName(device, callback);

    verify(fakeConnector).retrieveCompanionApplicationName(device, callback);
  }

  private final RemoteFeature remoteFeature =
      new RemoteFeature(context, featureId, fakeConnector) {
        @Override
        public void stop() {
          super.stop();
          spyFeature.stop();
        }

        @Override
        protected void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {
          spyFeature.onAssociatedDeviceAdded(device);
        }

        @Override
        protected void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {
          spyFeature.onAssociatedDeviceRemoved(device);
        }

        @Override
        protected void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device) {
          spyFeature.onAssociatedDeviceUpdated(device);
        }

        @Override
        protected void onDeviceConnected(@NonNull ConnectedDevice device) {
          spyFeature.onDeviceConnected(device);
        }

        @Override
        protected void onDeviceDisconnected(@NonNull ConnectedDevice device) {
          spyFeature.onDeviceDisconnected(device);
        }

        @Override
        protected void onDeviceError(@NonNull ConnectedDevice device, int error) {
          spyFeature.onDeviceError(device, error);
        }

        @Override
        protected void onMessageFailedToSend(
            @NonNull String deviceId, @NonNull byte[] message, boolean isTransient) {
          spyFeature.onMessageFailedToSend(deviceId, message, isTransient);
        }

        @Override
        protected void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {
          spyFeature.onMessageReceived(device, message);
        }

        @Override
        protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {
          spyFeature.onSecureChannelEstablished(device);
        }

        @Override
        protected void onQueryReceived(
            @NonNull ConnectedDevice device,
            int queryId,
            @NonNull byte[] request,
            @NonNull byte[] parameters) {
          spyFeature.onQueryReceived(device, queryId, request, parameters);
        }

        @Override
        protected void onReady() {
          spyFeature.onReady();
        }

        @Override
        protected void onNotReady() {
          spyFeature.onNotReady();
        }
      };
}
