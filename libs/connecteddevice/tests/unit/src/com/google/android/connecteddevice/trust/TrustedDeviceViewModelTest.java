package com.google.android.connecteddevice.trust;

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.KeyguardManager;
import android.os.RemoteException;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel.EnrollmentState;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class TrustedDeviceViewModelTest {
  private static final String TEST_ASSOCIATED_DEVICE_ID = "test_device_id";
  private static final String TEST_ASSOCIATED_DEVICE_NAME = "test_device_name";
  private static final String TEST_ASSOCIATED_DEVICE_ADDRESS = "test_device_address";
  private static final int TEST_USER_ID = 10;
  private static final long TEST_HANDLE = 0L;

  @Mock private ITrustedDeviceManager mockTrustedDeviceManager;
  private KeyguardManager keyguardManager;
  @Mock private Observer<EnrollmentState> mockEnrollmentStateObserver;

  private TrustedDeviceViewModel viewModel;

  private ITrustedDeviceEnrollmentCallback enrollmentCallback;
  private ITrustedDeviceCallback trustedDeviceCallback;
  private IOnTrustedDevicesRetrievedListener devicesRetrievedListener;

  @Before
  public void setUp() throws RemoteException {
    MockitoAnnotations.initMocks(this);
    Application application = ApplicationProvider.getApplicationContext();
    keyguardManager = application.getSystemService(KeyguardManager.class);
    viewModel = new TrustedDeviceViewModel(application, mockTrustedDeviceManager);
    viewModel.setAssociatedDevice(createAssociatedDevice());
    captureCallbacks();
  }

  @Test
  public void enrollTrustedDevice_deviceConnected() throws RemoteException {
    when(mockTrustedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(Collections.singletonList(createConnectedDevice()));
    viewModel.enrollTrustedDevice(createAssociatedDevice());
    verify(mockTrustedDeviceManager).initiateEnrollment(eq(TEST_ASSOCIATED_DEVICE_ID));
  }

  @Test
  public void enrollTrustedDevice_deviceNotConnected() throws RemoteException {
    when(mockTrustedDeviceManager.getActiveUserConnectedDevices()).thenReturn(new ArrayList<>());
    viewModel.enrollTrustedDevice(createAssociatedDevice());
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.NO_CONNECTION);
  }

  @Test
  public void disableTrustedDevice() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    viewModel.disableTrustedDevice(testDevice);
    verify(mockTrustedDeviceManager).removeTrustedDevice(eq(testDevice));
  }

  @Test
  public void testEnrollmentState_processEnrollmentOnInsecureDevice() throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);

    viewModel.processEnrollment();

    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.IN_PROGRESS);
    verify(mockTrustedDeviceManager).processEnrollment(eq(false));
  }

  @Test
  public void testEnrollmentState_processEnrollmentOnSecureDevice() throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(true);
    // User confirms enrollment through notification.
    viewModel.processEnrollment();
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.IN_PROGRESS);
    verify(mockTrustedDeviceManager).processEnrollment(eq(true));
  }

  @Test
  public void testEnrollmentState_onSecureDeviceRequest() throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);

    enrollmentCallback.onSecureDeviceRequest();

    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.WAITING_FOR_PASSWORD_SETUP);
  }

  @Test
  public void testEnrollmentState_processEnrollmentOnNewSecureDevice() throws RemoteException {
    ArgumentCaptor<EnrollmentState> stateCaptor = ArgumentCaptor.forClass(EnrollmentState.class);
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.processEnrollment();
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    waitForLiveDataUpdate();
    enrollmentCallback.onSecureDeviceRequest();
    waitForLiveDataUpdate();

    shadowOf(keyguardManager).setIsDeviceSecure(true);
    viewModel.processEnrollment();

    waitForLiveDataUpdate();
    // NONE -> IN_PROGRESS -> WAITING_FOR_PASSWORD_SETUP -> IN_PROGRESS
    verify(mockEnrollmentStateObserver, times(4)).onChanged(stateCaptor.capture());
    assertThat(stateCaptor.getValue()).isEqualTo(EnrollmentState.IN_PROGRESS);
    verify(mockTrustedDeviceManager).processEnrollment(eq(true));
  }

  @Test
  public void testEnrollmentState_onValidateCredentialsRequest() throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(true);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    viewModel.processEnrollment();
    waitForLiveDataUpdate();

    enrollmentCallback.onValidateCredentialsRequest();

    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.CREDENTIAL_PENDING);
  }

  @Test
  public void testEnrollmentState_abortEnrollment() throws RemoteException {
    ArgumentCaptor<EnrollmentState> captor = ArgumentCaptor.forClass(EnrollmentState.class);
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    viewModel.processEnrollment();
    waitForLiveDataUpdate();

    viewModel.abortEnrollment();

    waitForLiveDataUpdate();
    verify(mockTrustedDeviceManager).abortEnrollment();
    // NONE -> IN_PROGRESS -> NONE
    verify(mockEnrollmentStateObserver, times(3)).onChanged(captor.capture());
    assertThat(captor.getValue()).isEqualTo(EnrollmentState.NONE);
  }

  @Test
  public void trustedDeviceEnabled_valueUpdated() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    trustedDeviceCallback.onTrustedDeviceAdded(testDevice);
    viewModel.getEnabledDevice().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getEnabledDevice().getValue().equals(testDevice)).isTrue();
  }

  @Test
  public void trustedDeviceDisabled_valueUpdated() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    trustedDeviceCallback.onTrustedDeviceRemoved(testDevice);
    viewModel.getDisabledDevice().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getDisabledDevice().getValue().equals(testDevice)).isTrue();
  }

  @Test
  public void trustedDeviceRetrieved_valueUpdated() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    devicesRetrievedListener.onTrustedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.getTrustedDevices().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getTrustedDevices().getValue().get(0).equals(testDevice)).isTrue();
  }

  private static void waitForLiveDataUpdate() {
    shadowOf(getMainLooper()).idle();
  }

  private void captureCallbacks() throws RemoteException {
    ArgumentCaptor<ITrustedDeviceEnrollmentCallback> enrollmentCallbackCaptor =
        ArgumentCaptor.forClass(ITrustedDeviceEnrollmentCallback.class);
    verify(mockTrustedDeviceManager)
        .registerTrustedDeviceEnrollmentCallback(enrollmentCallbackCaptor.capture());
    enrollmentCallback = enrollmentCallbackCaptor.getValue();

    ArgumentCaptor<ITrustedDeviceCallback> trustedDeviceCallbackCaptor =
        ArgumentCaptor.forClass(ITrustedDeviceCallback.class);
    verify(mockTrustedDeviceManager)
        .registerTrustedDeviceCallback(trustedDeviceCallbackCaptor.capture());
    trustedDeviceCallback = trustedDeviceCallbackCaptor.getValue();

    ArgumentCaptor<IOnTrustedDevicesRetrievedListener> devicesRetrievedListenerCaptor =
        ArgumentCaptor.forClass(IOnTrustedDevicesRetrievedListener.class);
    verify(mockTrustedDeviceManager)
        .retrieveTrustedDevicesForActiveUser(devicesRetrievedListenerCaptor.capture());
    devicesRetrievedListener = devicesRetrievedListenerCaptor.getValue();
  }

  private static AssociatedDevice createAssociatedDevice() {
    return new AssociatedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_ADDRESS,
        TEST_ASSOCIATED_DEVICE_NAME,
        /* isConnectionEnabled= */ true);
  }

  private static ConnectedDevice createConnectedDevice() {
    return new ConnectedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_NAME,
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true);
  }

  private static TrustedDevice createTrustedDevice() {
    return new TrustedDevice(TEST_ASSOCIATED_DEVICE_ID, TEST_USER_ID, TEST_HANDLE);
  }
}
