package com.google.android.connecteddevice.trust;

import static android.os.Looper.getMainLooper;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DISCONNECTED_DURING_ENROLLMENT;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_NO_CONNECTION;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNEXPECTED_STATE;
import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
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
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel.EnrollmentState;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

@RunWith(ParameterizedRobolectricTestRunner.class)
public final class TrustedDeviceViewModelTest {
  private static final String TEST_ASSOCIATED_DEVICE_ID = "test_device_id";
  private static final String TEST_ASSOCIATED_DEVICE_NAME = "test_device_name";
  private static final String TEST_ASSOCIATED_DEVICE_ADDRESS = "test_device_address";
  private static final int TEST_USER_ID = 10;
  private static final long TEST_HANDLE = 0L;

  @Mock private ITrustedDeviceManager mockTrustedDeviceManager;
  private KeyguardManager keyguardManager;
  @Mock private Observer<EnrollmentState> mockEnrollmentStateObserver;
  @Mock private Observer<Integer> mockEnrollmentErrorObserver;
  @Mock private Observer<List<TrustedDevice>> mockTrustedDevicesObserver;

  private TrustedDeviceViewModel viewModel;

  private ITrustedDeviceEnrollmentCallback enrollmentCallback;
  private ITrustedDeviceCallback trustedDeviceCallback;
  private IOnTrustedDevicesRetrievedListener devicesRetrievedListener;

  @Parameters(name = "trustedDeviceError={0}")
  public static List<Integer> parameters() {
    return Arrays.asList(
        TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN,
        TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED,
        TRUSTED_DEVICE_ERROR_UNKNOWN,
        TRUSTED_DEVICE_ERROR_UNEXPECTED_STATE,
        TRUSTED_DEVICE_ERROR_NO_CONNECTION,
        TRUSTED_DEVICE_ERROR_DISCONNECTED_DURING_ENROLLMENT);
  }

  @Parameter public int trustedDeviceError;

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
    viewModel.getEnrollmentError().observeForever(mockEnrollmentErrorObserver);
    waitForLiveDataUpdate();
    verify(mockEnrollmentErrorObserver).onChanged(TRUSTED_DEVICE_ERROR_NO_CONNECTION);
  }

  @Test
  public void enrollTrustedDevice_beforeProcessEscrowToken_setEmptyTrustedDevices()
      throws RemoteException {
    List<TrustedDevice> emptyList = new ArrayList<>();
    ArgumentCaptor<IOnTrustedDevicesRetrievedListener> listenerCaptor =
        ArgumentCaptor.forClass(IOnTrustedDevicesRetrievedListener.class);
    viewModel.enrollTrustedDevice(createAssociatedDevice());
    // This first call is when the view model is initialized.
    verify(mockTrustedDeviceManager, times(2))
        .retrieveTrustedDevicesForActiveUser(listenerCaptor.capture());
    listenerCaptor.getAllValues().get(1).onTrustedDevicesRetrieved(emptyList);
    viewModel.getTrustedDevices().observeForever(mockTrustedDevicesObserver);
    waitForLiveDataUpdate();
    verify(mockTrustedDevicesObserver).onChanged(emptyList);
  }

  @Test
  public void disableTrustedDevice() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    viewModel.disableTrustedDevice(testDevice);
    verify(mockTrustedDeviceManager).removeTrustedDevice(eq(testDevice));
  }

  @Test
  public void testEnrollmentState_onEscrowTokenReceived_noPreviousEnrollment()
      throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);

    enrollmentCallback.onEscrowTokenReceived();

    waitForLiveDataUpdate();
    // No previous enrollment. State: NONE -> NONE
    verify(mockEnrollmentStateObserver, times(2)).onChanged(EnrollmentState.NONE);
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
  public void testEnrollmentState_onEscrowTokenReceived_existsPreviousEnrollment()
      throws RemoteException {
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);

    viewModel.processEnrollment();
    waitForLiveDataUpdate();
    verify(mockEnrollmentStateObserver).onChanged(EnrollmentState.IN_PROGRESS);
    enrollmentCallback.onEscrowTokenReceived();

    waitForLiveDataUpdate();
    // Exists previous incomplete enrollment. State: NONE -> IN_PROGRESS -> NONE
    verify(mockEnrollmentStateObserver, times(2)).onChanged(EnrollmentState.NONE);
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
  public void receivedError_abortEnrollment() throws RemoteException {
    ArgumentCaptor<EnrollmentState> captor = ArgumentCaptor.forClass(EnrollmentState.class);
    shadowOf(keyguardManager).setIsDeviceSecure(false);
    viewModel.getEnrollmentState().observeForever(mockEnrollmentStateObserver);
    viewModel.processEnrollment();
    waitForLiveDataUpdate();

    enrollmentCallback.onTrustedDeviceEnrollmentError(trustedDeviceError);

    waitForLiveDataUpdate();
    verify(mockTrustedDeviceManager).abortEnrollment();
    // NONE -> IN_PROGRESS -> NONE
    verify(mockEnrollmentStateObserver, times(3)).onChanged(captor.capture());
    assertThat(captor.getValue()).isEqualTo(EnrollmentState.NONE);
  }

  @Test
  public void trustedDeviceRetrieved_valueUpdated() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    devicesRetrievedListener.onTrustedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.getTrustedDevices().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getTrustedDevices().getValue().get(0).equals(testDevice)).isTrue();
  }

  @Test
  public void onTrustedDeviceAdded_retrieveDeviceFromManager_updateLiveData()
      throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    trustedDeviceCallback.onTrustedDeviceAdded(testDevice);

    ArgumentCaptor<IOnTrustedDevicesRetrievedListener> devicesRetrievedListenerCaptor =
        ArgumentCaptor.forClass(IOnTrustedDevicesRetrievedListener.class);
    verify(mockTrustedDeviceManager, times(2))
        .retrieveTrustedDevicesForActiveUser(devicesRetrievedListenerCaptor.capture());
    devicesRetrievedListenerCaptor
        .getValue()
        .onTrustedDevicesRetrieved(Collections.singletonList(testDevice));
    viewModel.getTrustedDevices().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getTrustedDevices().getValue().get(0).equals(testDevice)).isTrue();
  }

  @Test
  public void onTrustedDeviceRemoved_retrieveDeviceFromManager() throws RemoteException {
    TrustedDevice testDevice = createTrustedDevice();
    devicesRetrievedListener.onTrustedDevicesRetrieved(Collections.singletonList(testDevice));

    trustedDeviceCallback.onTrustedDeviceRemoved(testDevice);
    ArgumentCaptor<IOnTrustedDevicesRetrievedListener> devicesRetrievedListenerCaptor =
        ArgumentCaptor.forClass(IOnTrustedDevicesRetrievedListener.class);
    verify(mockTrustedDeviceManager, times(2))
        .retrieveTrustedDevicesForActiveUser(devicesRetrievedListenerCaptor.capture());
    devicesRetrievedListenerCaptor.getValue().onTrustedDevicesRetrieved(ImmutableList.of());
    viewModel.getTrustedDevices().observeForever(v -> {});
    waitForLiveDataUpdate();
    assertThat(viewModel.getTrustedDevices().getValue()).isEmpty();
  }

  @Test
  public void onCleared_unregisterCallbacks() throws RemoteException {
    viewModel.onCleared();

    verify(mockTrustedDeviceManager).unregisterTrustedDeviceCallback(any());
    verify(mockTrustedDeviceManager).unregisterTrustedDeviceEnrollmentCallback(any());
    verify(mockTrustedDeviceManager).unregisterAssociatedDeviceCallback(any());
  }

  @Test
  public void onCredentialVerified_forwardToTrustedDeviceManager() throws RemoteException {
    viewModel.onCredentialVerified();

    verify(mockTrustedDeviceManager).onCredentialVerified();
  }

  @Test
  public void onCredentialVerified_nullTrustedDeviceManager_noException() throws RemoteException {
    mockTrustedDeviceManager = null;

    assertDoesNotThrow(() -> viewModel.onCredentialVerified());
  }

  @Test
  public void abortEnrollment_updateTrustedDeviceFromServer() throws RemoteException {
    viewModel.abortEnrollment();

    verify(mockTrustedDeviceManager, times(2)).retrieveTrustedDevicesForActiveUser(any());
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

  private static void assertDoesNotThrow(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
      fail("Unexpected exception: " + e);
    }
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
