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

package com.google.android.connecteddevice.ui;

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.FakeConnector;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.OobData;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel.AssociationState;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class AssociatedDeviceViewModelTest {
  private static final String TEST_ASSOCIATED_DEVICE_ID = "test_device_id";
  private static final String TEST_ASSOCIATED_DEVICE_NAME_1 = "test_device_name_1";
  private static final String TEST_ASSOCIATED_DEVICE_NAME_2 = "test_device_name_2";
  private static final String TEST_ASSOCIATED_DEVICE_ADDRESS = "test_device_address";
  private static final String TEST_CAR_NAME = "test_car_name";
  private static final String TEST_VERIFICATION_CODE = "test_code";
  private static final String TEST_BLE_DEVICE_NAME_PREFIX = "TestPrefix";
  private static final OobData TEST_OOB_DATA = new OobData(new byte[0], new byte[0], new byte[0]);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Rule
  public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  private final Application application = ApplicationProvider.getApplicationContext();

  private final BluetoothAdapter adapter =
      application.getSystemService(BluetoothManager.class).getAdapter();

  private final FakeConnector fakeConnector = spy(new FakeConnector());
  private final NeverConnectFakeConnector neverConnectFakeConnector =
      spy(new NeverConnectFakeConnector());

  @Mock private Observer<AssociationState> mockAssociationStateObserver;
  @Mock private Observer<List<AssociatedDeviceDetails>> mockDeviceDetailsObserver;
  @Mock private Observer<String> mockCarNameObserver;
  @Mock private Observer<StartAssociationResponse> mockAssociationResponseObserver;
  @Mock private Observer<String> mockPairingCodeObserver;
  @Mock private Observer<AssociatedDevice> mockRemovedDeviceObserver;

  private AssociatedDeviceViewModel viewModel;

  private IAssociationCallback associationCallback;

  @Before
  public void setUp() throws RemoteException {
    viewModel = createViewModel(fakeConnector);
    adapter.enable();
  }

  @Test
  public void acceptVerification() throws RemoteException {
    viewModel.acceptVerification();
    verify(fakeConnector).acceptVerification();
  }

  @Test
  public void acceptVerification_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);

    viewModel.acceptVerification();

    verify(neverConnectFakeConnector, never()).acceptVerification();
  }

  @Test
  public void removeDevice() {
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(device);
    viewModel.removeDevice(device);
    verify(fakeConnector).removeAssociatedDevice(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void removeDevice_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);

    viewModel.removeDevice(device);

    verify(neverConnectFakeConnector, never()).removeAssociatedDevice(anyString());
  }

  @Test
  public void toggleConnectionStatusForDevice_disableDevice() {
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(device);
    viewModel.toggleConnectionStatusForDevice(device);
    verify(fakeConnector).disableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForDevice_enableDevice() {
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ false);
    fakeConnector.addAssociatedDevice(device);
    viewModel.toggleConnectionStatusForDevice(device);
    verify(fakeConnector).enableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForDevice_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);
    AssociatedDevice device1 = createAssociatedDevice(/* isConnectionEnabled= */ false);
    AssociatedDevice device2 = createAssociatedDevice(/* isConnectionEnabled= */ true);

    viewModel.toggleConnectionStatusForDevice(device1);
    viewModel.toggleConnectionStatusForDevice(device2);

    verify(neverConnectFakeConnector, never()).enableAssociatedDeviceConnection(anyString());
    verify(neverConnectFakeConnector, never()).disableAssociatedDeviceConnection(anyString());
  }

  @Test
  public void startAssociation_startWithIdentifier() {
    ParcelUuid testIdentifier = new ParcelUuid(UUID.randomUUID());
    viewModel.startAssociation(testIdentifier);
    verify(fakeConnector).startAssociation(eq(testIdentifier), any());
  }

  @Test
  public void startAssociation_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);

    viewModel.startAssociation();
    verify(neverConnectFakeConnector, never()).startAssociation(any());
  }

  @Test
  public void startAssociation_deviceNotReady() {
    adapter.disable();
    viewModel.startAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.PENDING);
    verify(fakeConnector, never()).startAssociation(any());
  }

  @Test
  public void startAssociation_waitingForPasswordSetup() {
    viewModel.startAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTING);
    verify(fakeConnector).startAssociation(any());
  }

  @Test
  public void startAssociation_inProgress() throws RemoteException {
    StartAssociationResponse response =
        new StartAssociationResponse(TEST_OOB_DATA, new byte[0], TEST_CAR_NAME);
    viewModel.startAssociation();
    captureAssociationCallback();
    associationCallback.onAssociationStartSuccess(response);
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    viewModel.getAdvertisedCarName().observeForever(mockCarNameObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTED);
    verify(mockCarNameObserver).onChanged(TEST_BLE_DEVICE_NAME_PREFIX + TEST_CAR_NAME);
  }

  @Test
  public void startAssociation_waitingForVerification() throws RemoteException {
    StartAssociationResponse response =
        new StartAssociationResponse(TEST_OOB_DATA, new byte[0], TEST_CAR_NAME);
    viewModel.startAssociation();
    captureAssociationCallback();
    associationCallback.onAssociationStartSuccess(response);
    associationCallback.onVerificationCodeAvailable(TEST_VERIFICATION_CODE);
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    viewModel.getPairingCode().observeForever(mockPairingCodeObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTED);
    verify(mockPairingCodeObserver).onChanged(TEST_VERIFICATION_CODE);
  }

  @Test
  public void startAssociation_associationCompleted() throws RemoteException {
    StartAssociationResponse response =
        new StartAssociationResponse(TEST_OOB_DATA, new byte[0], TEST_CAR_NAME);
    viewModel.startAssociation();
    captureAssociationCallback();
    associationCallback.onAssociationStartSuccess(response);
    associationCallback.onVerificationCodeAvailable(TEST_VERIFICATION_CODE);
    associationCallback.onAssociationCompleted();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.COMPLETED);
  }

  @Test
  public void startAssociation_associationError_resetAdvertisingData() throws RemoteException {
    viewModel.startAssociation();
    captureAssociationCallback();
    viewModel.getAssociationResponse().observeForever(mockAssociationResponseObserver);
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    viewModel.getAdvertisedCarName().observeForever(mockCarNameObserver);
    viewModel.getPairingCode().observeForever(mockPairingCodeObserver);
    associationCallback.onAssociationError(0);
    verify(mockAssociationStateObserver).onChanged(AssociationState.ERROR);
    verify(mockAssociationResponseObserver, times(2)).onChanged(null);
    verify(mockCarNameObserver, times(2)).onChanged(null);
    verify(mockPairingCodeObserver, times(2)).onChanged(null);
  }

  @Test
  public void retryAssociation_retryWithPreviousIdentifier() {
    ParcelUuid testIdentifier = new ParcelUuid(UUID.randomUUID());
    viewModel.startAssociation(testIdentifier);
    viewModel.retryAssociation();
    verify(fakeConnector, times(2)).startAssociation(eq(testIdentifier), any());
  }

  @Test
  public void stopAssociation() {
    viewModel.startAssociation();
    viewModel.stopAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.NONE);
    verify(fakeConnector).stopAssociation();
  }

  @Test
  public void retrieveAssociatedDeviceForDriver() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(testDevice, ConnectionState.NOT_DETECTED);
    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void retrieveAssociatedDeviceForDriver_onConnection() {
    // Adding devices before the creation of the ViewModel since the connector is connected
    // upon construction.
    AssociatedDevice driverDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(driverDevice);
    fakeConnector.claimAssociatedDevice(driverDevice.getDeviceId());

    AssociatedDevice nonClaimedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(nonClaimedDevice);

    viewModel =
        new AssociatedDeviceViewModel(
            application,
            /* isSppEnabled= */ false,
            TEST_BLE_DEVICE_NAME_PREFIX,
            /* isPassengerEnabled= */ false,
            fakeConnector);
    adapter.enable();

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(driverDevice, ConnectionState.NOT_DETECTED);
    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);

    // Only the driver device should show up with passenger mode disabled.
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void retrieveAssociatedDevices_onConnection_passengerEnabled() {
    // Adding devices before the creation of the ViewModel since the connector is connected
    // upon construction.
    AssociatedDevice driverDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(driverDevice);
    fakeConnector.claimAssociatedDevice(driverDevice.getDeviceId());

    AssociatedDevice nonClaimedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(nonClaimedDevice);

    viewModel =
        new AssociatedDeviceViewModel(
            application,
            /* isSppEnabled= */ false,
            TEST_BLE_DEVICE_NAME_PREFIX,
            /* isPassengerEnabled= */ true,
            fakeConnector);
    adapter.enable();

    AssociatedDeviceDetails driverDetails =
        new AssociatedDeviceDetails(driverDevice, ConnectionState.NOT_DETECTED);
    AssociatedDeviceDetails passengerDetails =
        new AssociatedDeviceDetails(nonClaimedDevice, ConnectionState.NOT_DETECTED);

    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(
        driverDetails, passengerDetails);
  }

  @Test
  public void associatedDeviceUpdated() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);
    AssociatedDevice updatedDevice =
        new AssociatedDevice(
            TEST_ASSOCIATED_DEVICE_ID,
            TEST_ASSOCIATED_DEVICE_ADDRESS,
            TEST_ASSOCIATED_DEVICE_NAME_2,
            /* isConnectionEnabled= */ true);

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(updatedDevice, ConnectionState.NOT_DETECTED);

    fakeConnector.getCallback().onAssociatedDeviceUpdated(updatedDevice);
    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void removeAssociatedDevice() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);
    viewModel.removeDevice(testDevice);
    verify(fakeConnector).removeAssociatedDevice(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void associatedDeviceRemoved() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);

    fakeConnector.removeAssociatedDevice(testDevice);

    viewModel.getRemovedDevice().observeForever(mockRemovedDeviceObserver);
    verify(mockRemovedDeviceObserver).onChanged(testDevice);
  }

  @Test
  public void onDeviceConnected_updateConnectionStatusToDetected() {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testAssociatedDevice);
    ConnectedDevice testConnectedDevice = createDetectedDevice();
    when(fakeConnector.getConnectedDevices()).thenReturn(ImmutableList.of(testConnectedDevice));

    fakeConnector.getCallback().onDeviceConnected(testConnectedDevice);

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(testAssociatedDevice, ConnectionState.DETECTED);

    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void onSecureChannelEstablished_updatesConnectionStatusToConnected() {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testAssociatedDevice);
    ConnectedDevice testConnectedDevice = createConnectedDevice();
    when(fakeConnector.getConnectedDevices()).thenReturn(ImmutableList.of(testConnectedDevice));

    fakeConnector.getCallback().onSecureChannelEstablished(testConnectedDevice);

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(testAssociatedDevice, ConnectionState.CONNECTED);
    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void onDeviceDisconnected_updatesConnectionStatusToNotDetected() {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testAssociatedDevice);
    ConnectedDevice testConnectedDevice = createConnectedDevice();

    fakeConnector.getCallback().onDeviceDisconnected(testConnectedDevice);

    AssociatedDeviceDetails expectedDetails =
        new AssociatedDeviceDetails(testAssociatedDevice, ConnectionState.NOT_DETECTED);

    viewModel.getAssociatedDevicesDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getAssociatedDevicesDetails().getValue()).containsExactly(expectedDetails);
  }

  @Test
  public void onCleared_doesNotThrowBeforeConnectionEstablished() {
    AssociatedDeviceViewModel associatedDeviceViewModel =
        new AssociatedDeviceViewModel(
            ApplicationProvider.getApplicationContext(),
            /* isSppEnabled= */ false,
            TEST_BLE_DEVICE_NAME_PREFIX,
            /* isPassengerEnabled= */ false);
    associatedDeviceViewModel.onCleared();
  }

  @Test
  public void claimDevice_claimsAssociatedDevice() {
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(device);

    viewModel.claimDevice(device);

    verify(fakeConnector).claimAssociatedDevice(device.getDeviceId());
  }

  @Test
  public void claimDevice_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);

    viewModel.claimDevice(device);

    verify(neverConnectFakeConnector, never()).claimAssociatedDevice(anyString());
  }

  @Test
  public void removeClaimOnDevice_removesAssociatedDeviceClaim() {
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(device);

    viewModel.removeClaimOnDevice(device);

    verify(fakeConnector).removeAssociatedDeviceClaim(device.getDeviceId());
  }

  @Test
  public void removeClaimOnDevice_notInvokedIfServiceBotConnected() {
    viewModel = createViewModel(neverConnectFakeConnector);
    AssociatedDevice device = createAssociatedDevice(/* isConnectionEnabled= */ true);

    viewModel.claimDevice(device);

    verify(neverConnectFakeConnector, never()).claimAssociatedDevice(anyString());
  }

  @Test
  public void connectTimeout() {
    viewModel = createViewModel(neverConnectFakeConnector);

    shadowOf(getMainLooper()).runToEndOfTasks();

    verify(neverConnectFakeConnector).disconnect();
  }

  private void captureAssociationCallback() {
    ArgumentCaptor<IAssociationCallback> associationCallbackCaptor =
        ArgumentCaptor.forClass(IAssociationCallback.class);
    verify(fakeConnector).startAssociation(associationCallbackCaptor.capture());
    associationCallback = associationCallbackCaptor.getValue();
  }

  private static AssociatedDevice createAssociatedDevice(boolean isConnectionEnabled) {
    return new AssociatedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_ADDRESS,
        TEST_ASSOCIATED_DEVICE_NAME_1,
        isConnectionEnabled);
  }

  private static ConnectedDevice createConnectedDevice() {
    return new ConnectedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_NAME_1,
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true);
  }

  private static ConnectedDevice createDetectedDevice() {
    return new ConnectedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_NAME_1,
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ false);
  }

  private AssociatedDeviceViewModel createViewModel(Connector connector) {
    return new AssociatedDeviceViewModel(
        application,
        /* isSppEnabled= */ false,
        TEST_BLE_DEVICE_NAME_PREFIX,
        /* isPassengerEnabled= */ false,
        connector);
  }

  /** Fake connector that never connects. */
  private static class NeverConnectFakeConnector extends FakeConnector {
    @Override
    public void connect() {
      // Do nothing
    }
  }
}
