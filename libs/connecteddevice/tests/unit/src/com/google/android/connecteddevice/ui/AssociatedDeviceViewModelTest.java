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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.FakeConnector;
import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.AssociatedDeviceDetails;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.OobData;
import com.google.android.connecteddevice.model.StartAssociationResponse;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel.AssociationState;
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

  @Mock private Observer<AssociationState> mockAssociationStateObserver;
  @Mock private Observer<AssociatedDeviceDetails> mockDeviceDetailsObserver;
  @Mock private Observer<String> mockCarNameObserver;
  @Mock private Observer<String> mockPairingCodeObserver;
  @Mock private Observer<AssociatedDevice> mockRemovedDeviceObserver;

  private AssociatedDeviceViewModel viewModel;

  private IAssociationCallback associationCallback;

  @Before
  public void setUp() throws RemoteException {
    viewModel =
        new AssociatedDeviceViewModel(
            application, /* isSppEnabled= */ false, TEST_BLE_DEVICE_NAME_PREFIX, fakeConnector);
    adapter.enable();
  }

  @Test
  public void acceptVerification() throws RemoteException {
    viewModel.acceptVerification();
    verify(fakeConnector).acceptVerification();
  }

  @Test
  public void removeCurrentDevice() {
    fakeConnector.addAssociatedDevice(createAssociatedDevice(/* isConnectionEnabled= */ true));
    viewModel.removeCurrentDevice();
    verify(fakeConnector).removeAssociatedDevice(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForCurrentDevice_disableDevice() {
    fakeConnector.addAssociatedDevice(createAssociatedDevice(/* isConnectionEnabled= */ true));
    viewModel.toggleConnectionStatusForCurrentDevice();
    verify(fakeConnector).disableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void toggleConnectionStatusForCurrentDevice_enableDevice() {
    fakeConnector.addAssociatedDevice(createAssociatedDevice(/* isConnectionEnabled= */ false));
    viewModel.toggleConnectionStatusForCurrentDevice();
    verify(fakeConnector).enableAssociatedDeviceConnection(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void startAssociation_startWithIdentifier() {
    ParcelUuid testIdentifier = new ParcelUuid(UUID.randomUUID());
    viewModel.startAssociation(testIdentifier);
    verify(fakeConnector).startAssociation(eq(testIdentifier), any());
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
  public void retrieveAssociatedDevice() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().getAssociatedDevice())
        .isEqualTo(testDevice);
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
    fakeConnector.getCallback().onAssociatedDeviceUpdated(updatedDevice);
    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().getDeviceName())
        .isEqualTo(TEST_ASSOCIATED_DEVICE_NAME_2);
  }

  @Test
  public void removeAssociatedDevice() {
    AssociatedDevice testDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testDevice);
    viewModel.removeCurrentDevice();
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
  public void associatedDeviceConnected() {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testAssociatedDevice);
    ConnectedDevice testConnectedDevice = createConnectedDevice();

    fakeConnector.getCallback().onDeviceConnected(testConnectedDevice);

    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().isConnected()).isTrue();
  }

  @Test
  public void associatedDeviceDisconnected() {
    AssociatedDevice testAssociatedDevice = createAssociatedDevice(/* isConnectionEnabled= */ true);
    fakeConnector.addAssociatedDevice(testAssociatedDevice);
    ConnectedDevice testConnectedDevice = createConnectedDevice();

    fakeConnector.getCallback().onDeviceConnected(testConnectedDevice);
    fakeConnector.getCallback().onDeviceDisconnected(testConnectedDevice);

    viewModel.getCurrentDeviceDetails().observeForever(mockDeviceDetailsObserver);
    assertThat(viewModel.getCurrentDeviceDetails().getValue().isConnected()).isFalse();
  }

  @Test
  public void onCleared_doesNotThrowBeforeConnectionEstablished() {
    AssociatedDeviceViewModel associatedDeviceViewModel =
        new AssociatedDeviceViewModel(
            ApplicationProvider.getApplicationContext(),
            /* isSppEnabled= */ false,
            TEST_BLE_DEVICE_NAME_PREFIX);
    associatedDeviceViewModel.onCleared();
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
        /* belongsToActiveUser= */ true,
        /* hasSecureChannel= */ true);
  }
}
