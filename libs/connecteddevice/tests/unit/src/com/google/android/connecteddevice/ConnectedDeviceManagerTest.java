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

package com.google.android.connecteddevice;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.ConnectedDeviceManager.ConnectionCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceAssociationCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.DeviceCallback;
import com.google.android.connecteddevice.ConnectedDeviceManager.MessageDeliveryDelegate;
import com.google.android.connecteddevice.connection.CarBluetoothManager;
import com.google.android.connecteddevice.connection.DeviceMessage;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.Errors;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage;
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage.AssociatedDeviceCallback;
import com.google.android.connecteddevice.transport.spp.ConnectedDeviceSppDelegateBinder;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ConnectedDeviceManagerTest {

  private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";

  private static final String TEST_DEVICE_NAME = "TEST_DEVICE_NAME";

  private final Executor directExecutor = directExecutor();

  private final UUID recipientId = UUID.randomUUID();

  private final List<String> userDeviceIds = new ArrayList<>();

  private final List<AssociatedDevice> userDevices = new ArrayList<>();

  @Mock private ConnectedDeviceStorage mockStorage;

  @Mock private CarBluetoothManager mockCarBluetoothManager;
  @Mock private ConnectionCallback mockConnectionCallback;
  @Mock private DeviceCallback mockDeviceCallback;
  @Mock private DeviceAssociationCallback mockDeviceAssociationCallback;

  private ConnectedDeviceManager connectedDeviceManager;

  private AssociatedDeviceCallback associatedDeviceCallback;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ArgumentCaptor<AssociatedDeviceCallback> callbackCaptor =
        ArgumentCaptor.forClass(AssociatedDeviceCallback.class);
    connectedDeviceManager =
        new ConnectedDeviceManager(
            mockCarBluetoothManager,
            mockStorage,
            new ConnectedDeviceSppDelegateBinder(),
            directExecutor,
            directExecutor,
            directExecutor);
    verify(mockStorage).setAssociatedDeviceCallback(callbackCaptor.capture());
    when(mockStorage.getActiveUserAssociatedDevices()).thenReturn(userDevices);
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(userDeviceIds);
    associatedDeviceCallback = callbackCaptor.getValue();
    connectedDeviceManager.start();
  }

  @Test
  public void getActiveUserConnectedDevices_initiallyShouldReturnEmptyList() {
    assertThat(connectedDeviceManager.getActiveUserConnectedDevices()).isEmpty();
  }

  @Test
  public void getActiveUserConnectedDevices_includesNewlyConnectedDevice() {
    String deviceId = connectNewDevice();
    List<ConnectedDevice> activeUserDevices =
        connectedDeviceManager.getActiveUserConnectedDevices();
    ConnectedDevice expectedDevice =
        new ConnectedDevice(
            deviceId,
            /* deviceName= */ null,
            /* belongsToActiveUser= */ true,
            /* hasSecureChannel= */ false);
    assertThat(activeUserDevices).containsExactly(expectedDevice);
  }

  @Test
  public void getActiveUserConnectedDevices_excludesDevicesNotBelongingToActiveUser() {
    String deviceId = UUID.randomUUID().toString();
    String otherUserDeviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds())
        .thenReturn(ImmutableList.of(otherUserDeviceId));
    connectedDeviceManager.addConnectedDevice(deviceId);
    assertThat(connectedDeviceManager.getActiveUserConnectedDevices()).isEmpty();
  }

  @Test
  public void getActiveUserConnectedDevices_reflectsSecureChannelEstablished() {
    String deviceId = connectNewDevice();
    connectedDeviceManager.onSecureChannelEstablished(deviceId);
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    assertThat(connectedDevice.hasSecureChannel()).isTrue();
  }

  @Test
  public void getActiveUserConnectedDevices_excludesDisconnectedDevice() {
    String deviceId = connectNewDevice();
    connectedDeviceManager.removeConnectedDevice(deviceId);
    assertThat(connectedDeviceManager.getActiveUserConnectedDevices()).isEmpty();
  }

  @Test
  public void sendMessageSecurely_throwsIllegalStateExceptionIfNoSecureChannel() {
    connectNewDevice();
    ConnectedDevice device = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    UUID recipientId = UUID.randomUUID();
    byte[] message = ByteUtils.randomBytes(10);
    assertThrows(
        IllegalStateException.class,
        () -> connectedDeviceManager.sendMessageSecurely(device, recipientId, message));
  }

  @Test
  public void sendMessageSecurely_sendsEncryptedMessage() {
    String deviceId = connectNewDevice();
    connectedDeviceManager.onSecureChannelEstablished(deviceId);
    ConnectedDevice device = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    UUID recipientId = UUID.randomUUID();
    byte[] message = ByteUtils.randomBytes(10);
    connectedDeviceManager.sendMessageSecurely(device, recipientId, message);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockCarBluetoothManager).sendMessage(eq(deviceId), messageCaptor.capture());
    assertThat(messageCaptor.getValue().isMessageEncrypted()).isTrue();
  }

  @Test
  public void sendMessageSecurely_doesNotSendIfDeviceDisconnected() {
    String deviceId = connectNewDevice();
    ConnectedDevice device = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.removeConnectedDevice(deviceId);
    UUID recipientId = UUID.randomUUID();
    byte[] message = ByteUtils.randomBytes(10);
    connectedDeviceManager.sendMessageSecurely(device, recipientId, message);
    verify(mockCarBluetoothManager, never()).sendMessage(eq(deviceId), any(DeviceMessage.class));
  }

  @Test
  public void sendMessageUnsecurely_sendsMessageWithoutEncryption() {
    String deviceId = connectNewDevice();
    ConnectedDevice device = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    UUID recipientId = UUID.randomUUID();
    byte[] message = ByteUtils.randomBytes(10);
    connectedDeviceManager.sendMessageUnsecurely(device, recipientId, message);
    ArgumentCaptor<DeviceMessage> messageCaptor = ArgumentCaptor.forClass(DeviceMessage.class);
    verify(mockCarBluetoothManager).sendMessage(eq(deviceId), messageCaptor.capture());
    assertThat(messageCaptor.getValue().isMessageEncrypted()).isFalse();
  }

  @Test
  public void connectionCallback_onDeviceConnectedInvokedForNewlyConnectedDevice()
      throws InterruptedException {
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    String deviceId = connectNewDevice();
    ArgumentCaptor<ConnectedDevice> deviceCaptor = ArgumentCaptor.forClass(ConnectedDevice.class);
    verify(mockConnectionCallback).onDeviceConnected(deviceCaptor.capture());
    ConnectedDevice connectedDevice = deviceCaptor.getValue();
    assertThat(connectedDevice.getDeviceId()).isEqualTo(deviceId);
    assertThat(connectedDevice.hasSecureChannel()).isFalse();
  }

  @Test
  public void connectionCallback_onDeviceConnectedNotInvokedDeviceConnectedForDifferentUser()
      throws InterruptedException {
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    String deviceId = UUID.randomUUID().toString();
    String otherUserDeviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds())
        .thenReturn(ImmutableList.of(otherUserDeviceId));
    connectedDeviceManager.addConnectedDevice(deviceId);
  }

  @Test
  public void connectionCallback_onDeviceConnectedNotInvokedForDifferentBleManager()
      throws InterruptedException {
    String deviceId = connectNewDevice();
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    connectedDeviceManager.addConnectedDevice(deviceId);
  }

  @Test
  public void connectionCallback_onDeviceDisconnectedInvokedForActiveUserDevice()
      throws InterruptedException {
    String deviceId = connectNewDevice();
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    connectedDeviceManager.removeConnectedDevice(deviceId);
    ArgumentCaptor<ConnectedDevice> deviceCaptor = ArgumentCaptor.forClass(ConnectedDevice.class);
    verify(mockConnectionCallback).onDeviceDisconnected(deviceCaptor.capture());
    assertThat(deviceCaptor.getValue().getDeviceId()).isEqualTo(deviceId);
  }

  @Test
  public void connectionCallback_onDeviceDisconnectedNotInvokedDeviceForDifferentUser()
      throws InterruptedException {
    String deviceId = UUID.randomUUID().toString();
    connectedDeviceManager.addConnectedDevice(deviceId);
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    connectedDeviceManager.removeConnectedDevice(deviceId);
  }

  @Test
  public void unregisterConnectionCallback_removesCallbackAndNotInvoked()
      throws InterruptedException {
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    connectedDeviceManager.unregisterConnectionCallback(mockConnectionCallback);
    connectNewDevice();
  }

  @Test
  public void registerDeviceCallback_denyListsDuplicateRecipientId() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    DeviceCallback firstDeviceCallback = mock(DeviceCallback.class);
    DeviceCallback secondDeviceCallback = mock(DeviceCallback.class);
    DeviceCallback thirdDeviceCallback = mock(DeviceCallback.class);

    // Register three times for following chain of events:
    // 1. First callback registered without issue.
    // 2. Second callback with same recipientId triggers deny listing both callbacks and issues
    //    error callbacks on both. Both callbacks should be unregistered at this point.
    // 3. Third callback gets rejected at registration and issues error callback.

    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, firstDeviceCallback, directExecutor);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, secondDeviceCallback, directExecutor);
    DeviceMessage message = new DeviceMessage(recipientId, false, new byte[10]);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
    verify(firstDeviceCallback)
        .onDeviceError(connectedDevice, Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED);
    verify(secondDeviceCallback)
        .onDeviceError(connectedDevice, Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED);
    verify(firstDeviceCallback, never()).onMessageReceived(any(), any());
    verify(secondDeviceCallback, never()).onMessageReceived(any(), any());

    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, thirdDeviceCallback, directExecutor);
    verify(thirdDeviceCallback)
        .onDeviceError(connectedDevice, Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED);
  }

  @Test
  public void deviceCallback_onSecureChannelEstablishedInvoked() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    connectedDeviceManager.onSecureChannelEstablished(connectedDevice.getDeviceId());
    connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    verify(mockDeviceCallback).onSecureChannelEstablished(connectedDevice);
  }

  @Test
  public void deviceCallback_onMessageReceivedInvokedForSameRecipientId()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message = new DeviceMessage(recipientId, false, payload);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
    verify(mockDeviceCallback).onMessageReceived(connectedDevice, payload);
  }

  @Test
  public void deviceCallback_onMessageReceivedNotInvokedForDifferentRecipientId()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message = new DeviceMessage(UUID.randomUUID(), false, payload);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
  }

  @Test
  public void deviceCallback_onDeviceErrorInvokedOnChannelError() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    connectedDeviceManager.deviceErrorOccurred(connectedDevice.getDeviceId());
    verify(mockDeviceCallback)
        .onDeviceError(connectedDevice, Errors.DEVICE_ERROR_INVALID_SECURITY_KEY);
  }

  @Test
  public void unregisterDeviceCallback_removesCallbackAndNotInvoked() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    connectedDeviceManager.unregisterDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback);
    connectedDeviceManager.onSecureChannelEstablished(connectedDevice.getDeviceId());
  }

  @Test
  public void registerDeviceCallback_sendsMissedMessageAfterRegistration()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message = new DeviceMessage(recipientId, false, payload);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    verify(mockDeviceCallback).onMessageReceived(connectedDevice, payload);
  }

  @Test
  public void registerDeviceCallback_sendsMultipleMissedMessagesAfterRegistration()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    byte[] payload1 = ByteUtils.randomBytes(10);
    byte[] payload2 = ByteUtils.randomBytes(10);
    DeviceMessage message1 = new DeviceMessage(recipientId, false, payload1);
    DeviceMessage message2 = new DeviceMessage(recipientId, false, payload2);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message1);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message2);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    verify(mockDeviceCallback).onMessageReceived(connectedDevice, payload1);
    verify(mockDeviceCallback).onMessageReceived(connectedDevice, payload2);
  }

  @Test
  public void registerDeviceCallback_doesNotSendMissedMessageForDifferentRecipient()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message = new DeviceMessage(UUID.randomUUID(), false, payload);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
  }

  @Test
  public void registerDeviceCallback_doesNotSendMissedMessageForDifferentDevice()
      throws InterruptedException {
    connectNewDevice();
    connectNewDevice();
    List<ConnectedDevice> connectedDevices = connectedDeviceManager.getActiveUserConnectedDevices();
    ConnectedDevice connectedDevice = connectedDevices.get(0);
    ConnectedDevice otherDevice = connectedDevices.get(1);
    byte[] payload = ByteUtils.randomBytes(10);
    DeviceMessage message = new DeviceMessage(recipientId, false, payload);
    connectedDeviceManager.onMessageReceived(otherDevice.getDeviceId(), message);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
  }

  @Test
  public void onAssociationCompleted_disconnectsOriginalDeviceAndReconnectsAsActiveUser()
      throws InterruptedException {
    String deviceId = UUID.randomUUID().toString();
    connectedDeviceManager.addConnectedDevice(deviceId);
    connectedDeviceManager.registerActiveUserConnectionCallback(
        mockConnectionCallback, directExecutor);
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(ImmutableList.of(deviceId));
    connectedDeviceManager.onAssociationCompleted(deviceId);
  }

  @Test
  public void deviceAssociationCallback_onAssociatedDeviceAdded() throws InterruptedException {
    connectedDeviceManager.registerDeviceAssociationCallback(
        mockDeviceAssociationCallback, directExecutor);
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice testDevice =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    associatedDeviceCallback.onAssociatedDeviceAdded(testDevice);
    verify(mockDeviceAssociationCallback).onAssociatedDeviceAdded(eq(testDevice));
  }

  @Test
  public void deviceAssociationCallback_onAssociationDeviceRemoved() throws InterruptedException {
    connectedDeviceManager.registerDeviceAssociationCallback(
        mockDeviceAssociationCallback, directExecutor);
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice testDevice =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    associatedDeviceCallback.onAssociatedDeviceRemoved(testDevice);
    verify(mockDeviceAssociationCallback).onAssociatedDeviceRemoved(eq(testDevice));
  }

  @Test
  public void deviceAssociationCallback_onAssociatedDeviceUpdated() throws InterruptedException {
    connectedDeviceManager.registerDeviceAssociationCallback(
        mockDeviceAssociationCallback, directExecutor);
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice testDevice =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    associatedDeviceCallback.onAssociatedDeviceUpdated(testDevice);
    verify(mockDeviceAssociationCallback).onAssociatedDeviceUpdated(eq(testDevice));
  }

  @Test
  public void removeConnectedDevice_startsAdvertisingForActiveUserDeviceOnActiveUserDisconnect() {
    String deviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(ImmutableList.of(deviceId));
    AssociatedDevice device =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    when(mockStorage.getActiveUserAssociatedDevices()).thenReturn(ImmutableList.of(device));
    connectedDeviceManager.addConnectedDevice(deviceId);
    connectedDeviceManager.removeConnectedDevice(deviceId);
    verify(mockCarBluetoothManager).connectToDevice(eq(UUID.fromString(deviceId)));
  }

  @Test
  public void removeConnectedDevice_startsAdvertisingForActiveUserDeviceOnLastDeviceDisconnect() {
    String deviceId = UUID.randomUUID().toString();
    String userDeviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(ImmutableList.of(userDeviceId));
    AssociatedDevice userDevice =
        new AssociatedDevice(
            userDeviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    when(mockStorage.getActiveUserAssociatedDevices()).thenReturn(ImmutableList.of(userDevice));
    connectedDeviceManager.addConnectedDevice(deviceId);
    connectedDeviceManager.removeConnectedDevice(deviceId);
    verify(mockCarBluetoothManager).connectToDevice(eq(UUID.fromString(userDeviceId)));
  }

  @Test
  public void removeConnectedDevice_doesNotAdvertiseForNonActiveUserDeviceNotLastDevice() {
    String deviceId = UUID.randomUUID().toString();
    String userDeviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(ImmutableList.of(userDeviceId));
    AssociatedDevice userDevice =
        new AssociatedDevice(
            userDeviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    when(mockStorage.getActiveUserAssociatedDevices()).thenReturn(ImmutableList.of(userDevice));
    connectedDeviceManager.addConnectedDevice(deviceId);
    connectedDeviceManager.addConnectedDevice(userDeviceId);
    connectedDeviceManager.removeConnectedDevice(deviceId);
    verify(mockCarBluetoothManager, never()).connectToDevice(any());
  }

  @Test
  public void removeConnectedDevice_startsAdvertisingForActiveUserDeviceWithNullDevice() {
    String deviceId = UUID.randomUUID().toString();
    when(mockStorage.getActiveUserAssociatedDeviceIds()).thenReturn(ImmutableList.of(deviceId));
    AssociatedDevice device =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    when(mockStorage.getActiveUserAssociatedDevices()).thenReturn(ImmutableList.of(device));
    connectedDeviceManager.removeConnectedDevice(deviceId);
    verify(mockCarBluetoothManager).connectToDevice(eq(UUID.fromString(deviceId)));
  }

  @Test
  public void removeActiveUserAssociatedDevice_deletesAssociatedDeviceFromStorage() {
    String deviceId = UUID.randomUUID().toString();
    connectedDeviceManager.removeActiveUserAssociatedDevice(deviceId);
    verify(mockStorage).removeAssociatedDeviceForActiveUser(deviceId);
  }

  @Test
  public void removeActiveUserAssociatedDevice_disconnectsIfConnected() {
    String deviceId = connectNewDevice();
    connectedDeviceManager.removeActiveUserAssociatedDevice(deviceId);
    verify(mockCarBluetoothManager).disconnectDevice(deviceId);
  }

  @Test
  public void enableAssociatedDeviceConnection_enableDeviceConnectionInStorage() {
    String deviceId = UUID.randomUUID().toString();
    connectedDeviceManager.enableAssociatedDeviceConnection(deviceId);
    verify(mockStorage).updateAssociatedDeviceConnectionEnabled(deviceId, true);
  }

  @Test
  public void disableAssociatedDeviceConnection_disableDeviceConnectionInStorage() {
    String deviceId = UUID.randomUUID().toString();
    connectedDeviceManager.disableAssociatedDeviceConnection(deviceId);
    verify(mockStorage).updateAssociatedDeviceConnectionEnabled(deviceId, false);
  }

  @Test
  public void disableAssociatedDeviceConnection_disconnectsIfConnected() {
    String deviceId = connectNewDevice();
    connectedDeviceManager.disableAssociatedDeviceConnection(deviceId);
    verify(mockCarBluetoothManager).disconnectDevice(deviceId);
  }

  @Test
  public void onMessageReceived_deliversMessageIfDelegateIsNull() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    DeviceMessage message = new DeviceMessage(recipientId, false, new byte[10]);
    connectedDeviceManager.setMessageDeliveryDelegate(null);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
  }

  @Test
  public void onMessageReceived_deliversMessageIfDelegateAccepts() throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    DeviceMessage message = new DeviceMessage(recipientId, false, new byte[10]);
    MessageDeliveryDelegate delegate = device -> true;
    connectedDeviceManager.setMessageDeliveryDelegate(delegate);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
  }

  @Test
  public void onMessageReceived_doesNotDeliverMessageIfDelegateRejects()
      throws InterruptedException {
    connectNewDevice();
    ConnectedDevice connectedDevice = connectedDeviceManager.getActiveUserConnectedDevices().get(0);
    connectedDeviceManager.registerDeviceCallback(
        connectedDevice, recipientId, mockDeviceCallback, directExecutor);
    DeviceMessage message = new DeviceMessage(recipientId, false, new byte[10]);
    MessageDeliveryDelegate delegate = device -> false;
    connectedDeviceManager.setMessageDeliveryDelegate(delegate);
    connectedDeviceManager.onMessageReceived(connectedDevice.getDeviceId(), message);
  }

  @NonNull
  private String connectNewDevice() {
    String deviceId = UUID.randomUUID().toString();
    AssociatedDevice device =
        new AssociatedDevice(
            deviceId, TEST_DEVICE_ADDRESS, TEST_DEVICE_NAME, /* isConnectionEnabled= */ true);
    userDeviceIds.add(deviceId);
    userDevices.add(device);
    connectedDeviceManager.addConnectedDevice(deviceId);
    return deviceId;
  }
}
