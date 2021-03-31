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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.lifecycle.Observer;
import android.bluetooth.BluetoothAdapter;
import android.os.RemoteException;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.AssociatedDeviceDetails;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel.AssociationState;
import java.util.Collections;
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

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Rule
  public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  @Mock private IAssociatedDeviceManager mockAssociatedDeviceManager;
  @Mock private Observer<AssociationState> mockAssociationStateObserver;
  @Mock private Observer<AssociatedDeviceDetails> mockDeviceDetailsObserver;
  @Mock private Observer<String> mockCarNameObserver;
  @Mock private Observer<String> mockPairingCodeObserver;
  @Mock private Observer<AssociatedDevice> mockRemovedDeviceObserver;

  private AssociatedDeviceViewModel viewModel;

  private IAssociationCallback associationCallback;
  private IDeviceAssociationCallback deviceAssociationCallback;
  private IConnectionCallback connectionCallback;
  private IOnAssociatedDevicesRetrievedListener devicesRetrievedListener;

  @Before
  public void setUp() throws RemoteException {
    viewModel =
        new AssociatedDeviceViewModel(
            ApplicationProvider.getApplicationContext(),
            mockAssociatedDeviceManager,
            /* isSppEnabled= */ false);
    BluetoothAdapter.getDefaultAdapter().enable();
    captureCallbacks();
  }

  @Test
  public void acceptVerification() throws RemoteException {
    viewModel.acceptVerification();
    verify(mockAssociatedDeviceManager).acceptVerification();
  }

  @Test
  public void removeCurrentDevice() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.removeCurrentDevice();
    verify(mockAssociatedDeviceManager).removeAssociatedDevice(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForCurrentDevice_disableDevice() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.toggleConnectionStatusForCurrentDevice();
    verify(mockAssociatedDeviceManager)
        .disableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForCurrentDevice_enableDevice() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ false);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.toggleConnectionStatusForCurrentDevice();
    verify(mockAssociatedDeviceManager)
        .enableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void startAssociation_deviceNotReady() throws RemoteException {
    BluetoothAdapter.getDefaultAdapter().disable();
    viewModel.startAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.PENDING);
    verify(mockAssociatedDeviceManager, never()).startAssociation();
  }

  @Test
  public void startAssociation_waitingForPasswordSetup() throws RemoteException {
    viewModel.startAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTING);
    verify(mockAssociatedDeviceManager).startAssociation();
  }

  @Test
  public void startAssociation_inProgress() throws RemoteException {
    viewModel.startAssociation();
    associationCallback.onAssociationStartSuccess(TEST_CAR_NAME);
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    viewModel.getAdvertisedCarName().observeForever(mockCarNameObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTED);
    verify(mockCarNameObserver).onChanged(TEST_CAR_NAME);
  }

  @Test
  public void startAssociation_waitingForVerification() throws RemoteException {
    viewModel.startAssociation();
    associationCallback.onAssociationStartSuccess(TEST_CAR_NAME);
    associationCallback.onVerificationCodeAvailable(TEST_VERIFICATION_CODE);
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    viewModel.getPairingCode().observeForever(mockPairingCodeObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.STARTED);
    verify(mockPairingCodeObserver).onChanged(TEST_VERIFICATION_CODE);
  }

  @Test
  public void startAssociation_associationCompleted() throws RemoteException {
    viewModel.startAssociation();
    associationCallback.onAssociationStartSuccess(TEST_CAR_NAME);
    associationCallback.onVerificationCodeAvailable(TEST_VERIFICATION_CODE);
    associationCallback.onAssociationCompleted();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.COMPLETED);
  }

  @Test
  public void stopAssociation() throws RemoteException {
    viewModel.startAssociation();
    viewModel.stopAssociation();
    viewModel.getAssociationState().observeForever(mockAssociationStateObserver);
    verify(mockAssociationStateObserver).onChanged(AssociationState.NONE);
    verify(mockAssociatedDeviceManager).stopAssociation();
  }

  @Test
  public void retrieveAssociatedDevice() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().getAssociatedDevice())
        .isEqualTo(testDevice);
  }

  @Test
  public void associatedDeviceUpdated() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    AssociatedDevice updatedDevice =
        new AssociatedDevice(
            TEST_ASSOCIATED_DEVICE_ID,
            TEST_ASSOCIATED_DEVICE_ADDRESS,
            TEST_ASSOCIATED_DEVICE_NAME_2,
            /* isConnectionEnabled= */ true);
    deviceAssociationCallback.onAssociatedDeviceUpdated(updatedDevice);
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().getDeviceName())
        .isEqualTo(TEST_ASSOCIATED_DEVICE_NAME_2);
  }

  @Test
  public void removeAssociatedDevice() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.removeCurrentDevice();
    verify(mockAssociatedDeviceManager).removeAssociatedDevice(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void associatedDeviceRemoved() throws RemoteException {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(Collections.singletonList(testDevice));
    deviceAssociationCallback.onAssociatedDeviceRemoved(testDevice);
    viewModel.getRemovedDevice().observeForever(mockRemovedDeviceObserver);
    verify(mockRemovedDeviceObserver).onChanged(testDevice);
  }

  @Test
  public void associatedDeviceConnected() throws RemoteException {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(
        Collections.singletonList(testAssociatedDevice));
    ConnectedDevice testConnectedDevice = createConnectedDevice();
    connectionCallback.onDeviceConnected(testConnectedDevice);
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().isConnected()).isTrue();
  }

  @Test
  public void associatedDeviceDisconnected() throws RemoteException {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    devicesRetrievedListener.onAssociatedDevicesRetrieved(
        Collections.singletonList(testAssociatedDevice));
    ConnectedDevice testConnectedDevice = createConnectedDevice();
    connectionCallback.onDeviceConnected(testConnectedDevice);
    connectionCallback.onDeviceDisconnected(testConnectedDevice);
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().isConnected()).isFalse();
  }

  @Test
  public void onCleard_doesNotThrowBeforeConnectionEstablished() {
    AssociatedDeviceViewModel associatedDeviceViewModel = new AssociatedDeviceViewModel(
        ApplicationProvider.getApplicationContext(),
        /* associatedDeviceManager= */ null,
        /* isSppEnabled= */ false);
    associatedDeviceViewModel.onCleared();
  }

  private void captureCallbacks() throws RemoteException {
    ArgumentCaptor<IAssociationCallback> associationCallbackCaptor =
        ArgumentCaptor.forClass(IAssociationCallback.class);
    verify(mockAssociatedDeviceManager).setAssociationCallback(associationCallbackCaptor.capture());
    associationCallback = associationCallbackCaptor.getValue();

    ArgumentCaptor<IDeviceAssociationCallback> deviceAssociationCallbackCaptor =
        ArgumentCaptor.forClass(IDeviceAssociationCallback.class);
    verify(mockAssociatedDeviceManager)
        .setDeviceAssociationCallback(deviceAssociationCallbackCaptor.capture());
    deviceAssociationCallback = deviceAssociationCallbackCaptor.getValue();

    ArgumentCaptor<IConnectionCallback> connectionCallbackCaptor =
        ArgumentCaptor.forClass(IConnectionCallback.class);
    verify(mockAssociatedDeviceManager).setConnectionCallback(connectionCallbackCaptor.capture());
    connectionCallback = connectionCallbackCaptor.getValue();

    ArgumentCaptor<IOnAssociatedDevicesRetrievedListener> devicesRetrievedListenerCaptor =
        ArgumentCaptor.forClass(IOnAssociatedDevicesRetrievedListener.class);
    verify(mockAssociatedDeviceManager)
        .retrievedActiveUserAssociatedDevices(devicesRetrievedListenerCaptor.capture());
    devicesRetrievedListener = devicesRetrievedListenerCaptor.getValue();
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
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ true);
  }
}
