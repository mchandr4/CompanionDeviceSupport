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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.companionprotos.Query;
import com.google.android.companionprotos.QueryResponse;
import com.google.android.connecteddevice.api.RemoteFeature.QueryCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.DeviceMessage.OperationType;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class RemoteFeatureTest {

  @Mock private IConnectedDeviceManager mockConnectedDeviceManager;

  private final Context context = ApplicationProvider.getApplicationContext();

  private final ParcelUuid featureId = new ParcelUuid(UUID.randomUUID());

  private RemoteFeature remoteFeature;

  private RemoteFeature spyFeature;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    spyFeature = spy(new RemoteFeature(context, featureId) { });
    remoteFeature = createRemoteFeature(mockConnectedDeviceManager);
  }

  @Test
  public void start_registersCallbacks() throws RemoteException {
    remoteFeature.start();
    verify(mockConnectedDeviceManager).registerActiveUserConnectionCallback(any());
    verify(mockConnectedDeviceManager).registerDeviceAssociationCallback(any());
    verify(mockConnectedDeviceManager).registerOnLogRequestedListener(anyInt(), any());
  }

  @Test
  public void noCallbacksRegisteredWithoutCallingStart() throws RemoteException {
    verify(mockConnectedDeviceManager, never()).registerActiveUserConnectionCallback(any());
    verify(mockConnectedDeviceManager, never()).registerDeviceAssociationCallback(any());
    verify(mockConnectedDeviceManager, never()).registerOnLogRequestedListener(anyInt(), any());
  }

  @Test
  public void start_registersDeviceCallbacksForAlreadyConnectedDevices() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    verify(mockConnectedDeviceManager).registerDeviceCallback(eq(device), eq(featureId), any());
  }

  @Test
  public void stop_unregistersCallbacks() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.stop();
    verify(mockConnectedDeviceManager).unregisterConnectionCallback(any());
    verify(mockConnectedDeviceManager).unregisterDeviceAssociationCallback(any());
    verify(mockConnectedDeviceManager).unregisterOnLogRequestedListener(anyInt(), any());
    verify(mockConnectedDeviceManager).unregisterDeviceCallback(eq(device), eq(featureId), any());
  }

  @Test
  public void onDeviceConnected_registersDeviceCallback() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    remoteFeature.start();
    ArgumentCaptor<IConnectionCallback> callbackCaptor =
        ArgumentCaptor.forClass(IConnectionCallback.class);
    verify(mockConnectedDeviceManager)
        .registerActiveUserConnectionCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onDeviceConnected(device);
    verify(mockConnectedDeviceManager).registerDeviceCallback(eq(device), eq(featureId), any());
  }

  @Test
  public void onDeviceConnected_invokedWhenDeviceConnects() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    remoteFeature.start();
    ArgumentCaptor<IConnectionCallback> callbackCaptor =
        ArgumentCaptor.forClass(IConnectionCallback.class);
    verify(mockConnectedDeviceManager)
        .registerActiveUserConnectionCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onDeviceConnected(device);
    verify(spyFeature).onDeviceConnected(device);
  }

  @Test
  public void onDeviceConnected_invokedOnStartupIfDeviceAlreadyConnected() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    verify(spyFeature).onDeviceConnected(eq(device));
  }

  @Test
  public void onDeviceDisconnected_invokedWhenDeviceDisconnects() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    remoteFeature.start();
    ArgumentCaptor<IConnectionCallback> callbackCaptor =
        ArgumentCaptor.forClass(IConnectionCallback.class);
    verify(mockConnectedDeviceManager)
        .registerActiveUserConnectionCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onDeviceDisconnected(device);
    verify(spyFeature).onDeviceDisconnected(device);
  }

  @Test
  public void onDeviceDisconnected_unregistersCallback() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    remoteFeature.start();
    ArgumentCaptor<IConnectionCallback> callbackCaptor =
        ArgumentCaptor.forClass(IConnectionCallback.class);
    verify(mockConnectedDeviceManager)
        .registerActiveUserConnectionCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onDeviceDisconnected(device);
    verify(mockConnectedDeviceManager)
        .unregisterDeviceCallback(eq(device), eq(featureId), any());
  }

  @Test
  public void onSecureChannelEstablished_invokedWhenChannelEstablished() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    ConnectedDevice secureDevice = new ConnectedDevice(
        device.getDeviceId(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ true);
    callbackCaptor.getValue().onSecureChannelEstablished(secureDevice);
    verify(spyFeature).onSecureChannelEstablished(secureDevice);
  }

  @Test
  public void onSecureChannelEstablished_invokedOnStartupIfChannelAlreadyEstablished()
      throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ true);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    verify(spyFeature).onSecureChannelEstablished(eq(device));
  }

  @Test
  public void onDeviceError_invokedOnError() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    int error = -1;
    callbackCaptor.getValue().onDeviceError(device, error);
    verify(spyFeature).onDeviceError(device, error);
  }

  @Test
  public void onAssociatedDeviceAdded_invokedWhenNewAssociatedDeviceAdded() throws RemoteException {
    AssociatedDevice device = new AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true);
    remoteFeature.start();
    ArgumentCaptor<IDeviceAssociationCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceAssociationCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceAssociationCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onAssociatedDeviceAdded(device);
    verify(spyFeature).onAssociatedDeviceAdded(device);
  }

  @Test
  public void onAssociatedDeviceRemoved_invokedWhenAssociatedDeviceRemoved()
      throws RemoteException {
    AssociatedDevice device = new AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true);
    remoteFeature.start();
    ArgumentCaptor<IDeviceAssociationCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceAssociationCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceAssociationCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onAssociatedDeviceRemoved(device);
    verify(spyFeature).onAssociatedDeviceRemoved(device);
  }

  @Test
  public void onAssociatedDeviceUpdated_invokedWhenAssociatedDeviceUpdated()
      throws RemoteException {
    AssociatedDevice device = new AssociatedDevice(
        UUID.randomUUID().toString(),
        /* deviceAddress= */ "",
        /* deviceName= */ null,
        /* isConnectionEnabled= */ true);
    remoteFeature.start();
    ArgumentCaptor<IDeviceAssociationCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceAssociationCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceAssociationCallback(callbackCaptor.capture());
    callbackCaptor.getValue().onAssociatedDeviceUpdated(device);
    verify(spyFeature).onAssociatedDeviceUpdated(device);
  }

  @Test
  public void sendMessageSecurely_sendsMessageSecurelyToDevice() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();

    byte[] message = ByteUtils.randomBytes(10);
    remoteFeature.sendMessageSecurely(device.getDeviceId(), message);
    ArgumentCaptor<DeviceMessage> captor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), captor.capture());
    DeviceMessage deviceMessage = captor.getValue();
    assertThat(deviceMessage.getRecipient()).isEqualTo(featureId.getUuid());
    assertThat(deviceMessage.isMessageEncrypted()).isTrue();
    assertThat(deviceMessage.getOperationType()).isEqualTo(OperationType.CLIENT_MESSAGE);
    assertThat(deviceMessage.getMessage()).isEqualTo(message);
  }

  @Test
  public void onMessageFailedToSend_invokedWhenDeviceNotFound() throws RemoteException {
    String deviceId = UUID.randomUUID().toString();
    byte[] message = ByteUtils.randomBytes(10);
    remoteFeature.start();
    remoteFeature.sendMessageSecurely(deviceId, message);
    verify(spyFeature).onMessageFailedToSend(deviceId, message, /* isTransient= */ false);
  }

  @Test
  public void onMessageFailedToSend_invokedWhenRemoteExceptionThrown() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    when(mockConnectedDeviceManager.sendMessage(any(), any()))
        .thenThrow(new RemoteException());
    byte[] message = ByteUtils.randomBytes(10);
    remoteFeature.start();
    remoteFeature.sendMessageSecurely(device.getDeviceId(), message);
    verify(spyFeature)
        .onMessageFailedToSend(device.getDeviceId(), message, /* isTransient= */ false);
  }

  @Test
  public void onMessageFailedToSend_invokedWhenErrorRetrievingDevices() throws RemoteException {
    String deviceId = UUID.randomUUID().toString();
    byte[] message = ByteUtils.randomBytes(10);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenThrow(new RemoteException());
    remoteFeature.start();
    remoteFeature.sendMessageSecurely(deviceId, message);
    verify(spyFeature)
        .onMessageFailedToSend(deviceId, message, /* isTransient= */ false);
  }

  @Test
  public void onMessageFailedToSend_invokedWhenSendMessageCalledBeforeServiceConnection()
      throws RemoteException{
    RemoteFeature disconnectedFeature = createRemoteFeature(/* connectedDeviceManager= */ null);
    String deviceId = UUID.randomUUID().toString();
    byte[] message = ByteUtils.randomBytes(10);
    disconnectedFeature.sendMessageSecurely(deviceId, message);
    verify(spyFeature).onMessageFailedToSend(deviceId, message, /* isTransient= */ true);
  }

  @Test
  public void onMessageReceived_invokedWhenGenericMessageReceived() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ true);
    byte[] message = ByteUtils.randomBytes(10);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.CLIENT_MESSAGE,
            message);
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);
    verify(spyFeature).onMessageReceived(device, message);
  }

  @Test
  public void getConnectedDeviceById_returnsDeviceWhenConnected() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    assertThat(remoteFeature.getConnectedDeviceById(device.getDeviceId())).isEqualTo(device);
  }

  @Test
  public void getConnectedDeviceById_returnsNullWhenNotConnected() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    assertThat(remoteFeature.getConnectedDeviceById(UUID.randomUUID().toString())).isNull();
  }

  @Test
  public void getConnectedDeviceById_returnsNullWhenRemoteExceptionThrown() throws RemoteException {
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenThrow(new RemoteException());
    assertThat(remoteFeature.getConnectedDeviceById(UUID.randomUUID().toString())).isNull();
  }

  @Test
  public void getConnectedDeviceById_returnsNullBeforeServiceConnection() {
    RemoteFeature disconnectedFeature = createRemoteFeature(/* connectedDeviceManager= */ null);
    assertThat(disconnectedFeature.getConnectedDeviceById(UUID.randomUUID().toString())).isNull();
  }

  @Test
  public void sendQuerySecurely_sendsQueryToOwnFeatureId()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();

    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    ArgumentCaptor<DeviceMessage> captor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), captor.capture());
    Query query =
        Query.parseFrom(captor.getValue().getMessage(), ExtensionRegistryLite.getEmptyRegistry());
    assertThat(query.getRequest().toByteArray()).isEqualTo(request);
    assertThat(query.getParameters().toByteArray()).isEqualTo(parameters);
  }

  @Test
  public void sendQuery_queryCallbackOnSuccessInvoked()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    ArgumentCaptor<DeviceMessage> captor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), captor.capture());
    Query query =
        Query.parseFrom(captor.getValue().getMessage(), ExtensionRegistryLite.getEmptyRegistry());
    byte[] response = ByteUtils.randomBytes(10);
    QueryResponse queryResponse =
        QueryResponse
            .newBuilder()
            .setQueryId(query.getId())
            .setSuccess(true)
            .setResponse(ByteString.copyFrom(response))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY_RESPONSE,
            queryResponse.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);
    verify(callback).onSuccess(response);
  }

  @Test
  public void sendQuery_queryCallbackOnErrorInvoked()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    ArgumentCaptor<DeviceMessage> captor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), captor.capture());
    Query query =
        Query.parseFrom(captor.getValue().getMessage(), ExtensionRegistryLite.getEmptyRegistry());
    byte[] response = ByteUtils.randomBytes(10);
    QueryResponse queryResponse =
        QueryResponse
            .newBuilder()
            .setQueryId(query.getId())
            .setSuccess(false)
            .setResponse(ByteString.copyFrom(response))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY_RESPONSE,
            queryResponse.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);
    verify(callback).onError(response);
  }

  @Test
  public void sendQuery_queryCallbackNotInvokedOnDifferentQueryIdResponse()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    ArgumentCaptor<DeviceMessage> captor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), captor.capture());
    Query query =
        Query.parseFrom(captor.getValue().getMessage(), ExtensionRegistryLite.getEmptyRegistry());
    byte[] response = ByteUtils.randomBytes(10);
    QueryResponse queryResponse =
        QueryResponse
            .newBuilder()
            .setQueryId(query.getId() + 1)
            .setSuccess(false)
            .setResponse(ByteString.copyFrom(response))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY_RESPONSE,
            queryResponse.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);
    verify(callback, never()).onSuccess(any());
    verify(callback, never()).onError(any());
  }

  @Test
  public void sendQuery_queryCallbackOnQueryNotSentInvokedBeforeServiceConnectionWithId() {
    RemoteFeature disconnectedFeature = createRemoteFeature(/* connectedDeviceManager= */ null);
    String deviceId = UUID.randomUUID().toString();
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    disconnectedFeature.sendQuerySecurely(deviceId, request, parameters, callback);
    verify(callback).onQueryFailedToSend(/* isTransient= */ true);
  }

  @Test
  public void sendQuery_queryCallbackOnQueryNotSentInvokedBeforeServiceConnectionWithDevice() {
    RemoteFeature disconnectedFeature = createRemoteFeature(/* connectedDeviceManager= */ null);
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    disconnectedFeature.sendQuerySecurely(device, request, parameters, callback);
    verify(callback).onQueryFailedToSend(/* isTransient= */ true);
  }

  @Test
  public void sendQuery_queryCallbackOnQueryNotSentInvokedIfSendMessageThrowsRemoteException()
      throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    when(mockConnectedDeviceManager.sendMessage(any(), any()))
        .thenThrow(new RemoteException());
    remoteFeature.start();

    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = null;
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    verify(callback).onQueryFailedToSend(/* isTransient= */ false);
  }

  @Test
  public void sendQuery_queryCallbackOnQueryNotSentInvokedIfDeviceNotFound()
      throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    remoteFeature.start();

    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    QueryCallback callback = spy(new QueryCallback() {});
    remoteFeature.sendQuerySecurely(device.getDeviceId(), request, parameters, callback);
    verify(callback).onQueryFailedToSend(/* isTransient= */ false);
  }

  @Test
  public void onQueryReceived_invokedWithQueryFields() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    int queryId = 1;
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    Query query =
        Query
            .newBuilder()
            .setId(queryId)
            .setRequest(ByteString.copyFrom(request))
            .setParameters(ByteString.copyFrom(parameters))
            .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId.getUuid())))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY,
            query.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);
    verify(spyFeature).onQueryReceived(device, queryId, request, parameters);
  }

  @Test
  public void respondToQuery_doesNotSendResponseWithUnrecognizedQueryId() throws RemoteException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    int nonExistentQueryId = 0;
    byte[] response = ByteUtils.randomBytes(10);
    remoteFeature.respondToQuerySecurely(device, nonExistentQueryId, /* success= */ true, response);
    verify(mockConnectedDeviceManager, never()).sendMessage(any(), any());
  }

  @Test
  public void respondToQuery_sendsResponseToSenderIfSameFeatureId()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    int queryId = 1;
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    Query query =
        Query
            .newBuilder()
            .setId(queryId)
            .setRequest(ByteString.copyFrom(request))
            .setParameters(ByteString.copyFrom(parameters))
            .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId.getUuid())))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY,
            query.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);

    byte[] response = ByteUtils.randomBytes(10);
    remoteFeature.respondToQuerySecurely(device, queryId, /* success= */ true, response);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager).sendMessage(eq(device), messageCaptor.capture());
    QueryResponse queryResponse =
        QueryResponse.parseFrom(
            messageCaptor.getValue().getMessage(),
            ExtensionRegistryLite.getEmptyRegistry());
    assertThat(queryResponse.getQueryId()).isEqualTo(queryId);
    assertThat(queryResponse.getSuccess()).isTrue();
    assertThat(queryResponse.getResponse().toByteArray()).isEqualTo(response);
  }

  @Test
  public void respondToQuery_sendResponseToSenderIfDifferentFeatureId()
      throws RemoteException, InvalidProtocolBufferException {
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(ImmutableList.of(device));
    remoteFeature.start();
    ArgumentCaptor<IDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(IDeviceCallback.class);
    verify(mockConnectedDeviceManager)
        .registerDeviceCallback(eq(device), eq(featureId), callbackCaptor.capture());
    ParcelUuid sender = new ParcelUuid(UUID.randomUUID());
    int queryId = 1;
    byte[] request = ByteUtils.randomBytes(10);
    byte[] parameters = ByteUtils.randomBytes(10);
    Query query =
        Query
            .newBuilder()
            .setId(queryId)
            .setRequest(ByteString.copyFrom(request))
            .setParameters(ByteString.copyFrom(parameters))
            .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(sender.getUuid())))
            .build();
    DeviceMessage deviceMessage =
        new DeviceMessage(
            featureId.getUuid(),
            /* isMessageEncrypted= */ true,
            OperationType.QUERY,
            query.toByteArray());
    callbackCaptor.getValue().onMessageReceived(device, deviceMessage);

    byte[] response = ByteUtils.randomBytes(10);
    remoteFeature.respondToQuerySecurely(device, queryId, /* success= */ true, response);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockConnectedDeviceManager)
        .sendMessage(eq(device), messageCaptor.capture());
    QueryResponse queryResponse =
        QueryResponse.parseFrom(
            messageCaptor.getValue().getMessage(),
            ExtensionRegistryLite.getEmptyRegistry());
    assertThat(queryResponse.getQueryId()).isEqualTo(queryId);
    assertThat(queryResponse.getSuccess()).isTrue();
    assertThat(queryResponse.getResponse().toByteArray()).isEqualTo(response);
  }

  @Test
  public void respondToQuery_doesNotThrowBeforeServiceConnectionWithDevice() {
    RemoteFeature disconnectedFeature = createRemoteFeature(/* connectedDeviceManager= */ null);
    ConnectedDevice device = new ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ "",
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ false);
    int queryId = 0;
    byte[] response = ByteUtils.randomBytes(10);
    disconnectedFeature.respondToQuerySecurely(device, queryId, /* success= */ true, response);
  }

  private RemoteFeature createRemoteFeature(
      @Nullable IConnectedDeviceManager connectedDeviceManager) {
    // Mockito.verify() will not work with inner classes. All callbacks internally use
    // RemoteFeature.this... to invoke the proper logic. Mockito.spy creates a copy of the class
    // and this results in the real class being invoked but not the spy. All tests must interact
    // with remoteFeature, but verify on spyFeature.
    return new RemoteFeature(context, featureId, connectedDeviceManager) {
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
      protected void onMessageFailedToSend(@NonNull String deviceId, @NonNull byte[] message,
          boolean isTransient) {
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
      protected void onQueryReceived(@NonNull ConnectedDevice device, int queryId,
          @NonNull byte[] request, @NonNull byte[] parameters) {
        spyFeature.onQueryReceived(device, queryId, request, parameters);
      }
    };
  }
}
