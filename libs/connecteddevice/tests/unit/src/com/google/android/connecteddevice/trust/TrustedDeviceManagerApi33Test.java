package com.google.android.connecteddevice.trust;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.KeyguardManager.WeakEscrowTokenActivatedListener;
import android.app.KeyguardManager.WeakEscrowTokenRemovedListener;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.Connector;
import com.google.android.connecteddevice.api.FakeConnector;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationRequestListener;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage.MessageType;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceTokenEntity;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = VERSION_CODES.TIRAMISU)
public final class TrustedDeviceManagerApi33Test {
  private static final String DEFAULT_DEVICE_ID = UUID.randomUUID().toString();

  // Note: This token needs to be of length 8 to be valid.
  private static final byte[] FAKE_TOKEN = "12345678".getBytes(UTF_8);

  // Note: The value of this handle is arbitrary.
  private static final long FAKE_HANDLE = 111L;

  private static final int DEFAULT_USER_ID = ActivityManager.getCurrentUser();

  private static final UserHandle DEFAULT_USER = UserHandle.of(DEFAULT_USER_ID);

  private static final ConnectedDevice SECURE_CONNECTED_DEVICE =
      new ConnectedDevice(
          DEFAULT_DEVICE_ID,
          "secureConnectedDevice",
          /* belongsToDriver= */ true,
          /* hasSecureChannel= */ true);

  private final Connector fakeConnector = spy(new FakeConnector());

  @Captor private ArgumentCaptor<List<TrustedDevice>> trustedDeviceListCaptor;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private ITrustedDeviceEnrollmentCallback enrollmentCallback;
  @Mock private ITrustedDeviceCallback trustedDeviceCallback;
  @Mock private ITrustedDeviceAgentDelegate trustAgentDelegate;
  @Mock private IOnTrustedDevicesRetrievedListener trustedDeviceListener;
  @Mock private IOnTrustedDeviceEnrollmentNotificationRequestListener notificationRequestListener;
  @Mock private KeyguardManager keyguardManager;

  private TrustedDeviceManagerApi33 manager;
  private TrustedDeviceFeature feature;
  private TrustedDeviceDatabase database;
  private WeakEscrowTokenRemovedListener tokenRemovedListener;

  @Before
  public void setUp() throws RemoteException {
    Context context = ApplicationProvider.getApplicationContext();

    // Required because ShadowRemoteCallbackList will invoke these methods and requires non-null
    // values.
    when(enrollmentCallback.asBinder()).thenReturn(mock(IBinder.class));
    when(trustedDeviceCallback.asBinder()).thenReturn(mock(IBinder.class));
    when(notificationRequestListener.asBinder()).thenReturn(mock(IBinder.class));

    when(keyguardManager.addWeakEscrowToken(eq(FAKE_TOKEN), eq(DEFAULT_USER), any(), any()))
        .thenReturn(FAKE_HANDLE);

    database =
        Room.inMemoryDatabaseBuilder(context, TrustedDeviceDatabase.class)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build();

    feature = spy(new TrustedDeviceFeature(context, fakeConnector));

    manager =
        new TrustedDeviceManagerApi33(
            keyguardManager,
            database,
            feature,
            /* databaseExecutor= */ directExecutor(),
            /* remoteCallbackExecutor= */ directExecutor());

    manager.setTrustedDeviceAgentDelegate(trustAgentDelegate);
    manager.registerTrustedDeviceEnrollmentCallback(enrollmentCallback);
    manager.registerTrustedDeviceCallback(trustedDeviceCallback);
    manager.registerTrustedDeviceEnrollmentNotificationRequestListener(notificationRequestListener);
    ArgumentCaptor<TrustedDeviceFeature.Callback> callbackCaptor =
        ArgumentCaptor.forClass(TrustedDeviceFeature.Callback.class);
    verify(feature).setCallback(callbackCaptor.capture());
    ArgumentCaptor<WeakEscrowTokenRemovedListener> listenerCaptor =
        ArgumentCaptor.forClass(WeakEscrowTokenRemovedListener.class);
    verify(keyguardManager).registerWeakEscrowTokenRemovedListener(any(), listenerCaptor.capture());
    tokenRemovedListener = listenerCaptor.getValue();
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void testValidEnrollmentFlow_onSecureCar_notifiesCallback() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    verify(trustedDeviceCallback).onTrustedDeviceAdded(expectedTrustedDevice);
  }

  @Test
  public void testValidEnrollmentFlow_onInsecureCar_notifiesCallback() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnInsecureCar();

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    verify(trustedDeviceCallback).onTrustedDeviceAdded(expectedTrustedDevice);
  }

  @Test
  public void testValidEnrollmentFlow_onInsecureCar_pinVerificationNotRequired()
      throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnInsecureCar();

    verify(enrollmentCallback, never()).onValidateCredentialsRequest();
  }

  @Test
  public void testValidEnrollmentFlow_onInsecureCar_tokenAddedOneTime() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnInsecureCar();

    verify(keyguardManager).addWeakEscrowToken(eq(FAKE_TOKEN), eq(DEFAULT_USER), any(), any());
  }

  @Test
  public void testValidEnrollmentFlow_storesTrustedDevice() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  @Test
  public void enrollment_storesHashedToken() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDeviceTokenEntity entity =
        database.trustedDeviceDao().getTrustedDeviceHashedToken(DEFAULT_DEVICE_ID);
    assertThat(entity).isNotNull();
  }

  @Test
  public void abortEnrollment_addedTokenRemoved() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyTokenAddedInEnrollFlowOnSecureCar();

    manager.abortEnrollment();
    verify(keyguardManager).removeWeakEscrowToken(eq(FAKE_HANDLE), eq(DEFAULT_USER));
  }

  @Test
  public void abortEnrollment_removeAddedTokenOnly() throws RemoteException {
    manager.abortEnrollment();

    verify(keyguardManager, never()).removeWeakEscrowToken(anyLong(), any(UserHandle.class));
  }

  @Test
  public void removeTrustedDeviceWithWeakEscrowToken_tokenRemoved() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice trustedDevice =
        new TrustedDevice(DEFAULT_DEVICE_ID, DEFAULT_USER_ID, FAKE_HANDLE);
    manager.removeTrustedDevice(trustedDevice);

    verify(keyguardManager).removeWeakEscrowToken(eq(FAKE_HANDLE), eq(DEFAULT_USER));
    TrustedDeviceTokenEntity entity =
        database.trustedDeviceDao().getTrustedDeviceHashedToken(DEFAULT_DEVICE_ID);
    assertThat(entity).isNull();
  }

  @Test
  public void onWeakEscrowTokenRemoved_trustedDeviceRemoved() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    tokenRemovedListener.onWeakEscrowTokenRemoved(FAKE_HANDLE, DEFAULT_USER);

    TrustedDevice device =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);
    List<TrustedDeviceEntity> invalidEntities =
        database.trustedDeviceDao().getInvalidTrustedDevicesForUser(DEFAULT_USER_ID);
    List<TrustedDeviceEntity> validEntities =
        database.trustedDeviceDao().getValidTrustedDevicesForUser(DEFAULT_USER_ID);
    assertThat(invalidEntities).isEmpty();
    assertThat(validEntities).isEmpty();
    verify(trustedDeviceCallback).onTrustedDeviceRemoved(device);
  }

  @Test
  public void cleanup_tokenRemovedListenerUnregistered() {
    manager.cleanup();

    verify(keyguardManager).unregisterWeakEscrowTokenRemovedListener(eq(tokenRemovedListener));
  }

  @Test
  public void removeEscrowToken_removeWeakEscrowToken() {
    manager.removeEscrowToken(FAKE_HANDLE, DEFAULT_USER_ID);

    verify(keyguardManager).removeWeakEscrowToken(eq(FAKE_HANDLE), eq(DEFAULT_USER));
  }

  /**
   * Runs through adding token flow in enrollment and verifies it is run on a secure car.
   *
   * <p>Returns the {@link WeakEscrowTokenActivatedListener} for added {@link #FAKE_TOKEN} in the
   * enrollment.
   *
   * <p>At the end of this method, {@link #FAKE_TOKEN} will be added and waiting to be activated for
   * {@link #SECURE_CONNECTED_DEVICE}.
   */
  private WeakEscrowTokenActivatedListener executeAndVerifyTokenAddedInEnrollFlowOnSecureCar()
      throws RemoteException {
    ArgumentCaptor<WeakEscrowTokenActivatedListener> captor =
        ArgumentCaptor.forClass(WeakEscrowTokenActivatedListener.class);
    // First, the phone will send an escrow token to start the enrollment.
    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE, createTokenMessage(FAKE_TOKEN));
    verify(notificationRequestListener).onTrustedDeviceEnrollmentNotificationRequest();

    // The user confirms enrollment through UI on the secure car.
    manager.processEnrollment(/* isDeviceSecure= */ true);

    verify(keyguardManager)
        .addWeakEscrowToken(eq(FAKE_TOKEN), eq(DEFAULT_USER), any(), captor.capture());

    verify(enrollmentCallback).onValidateCredentialsRequest();

    return captor.getValue();
  }

  /**
   * Runs through a valid enrollment flow and verifies that the current flow is run on a secure car.
   *
   * <p>At the end of this method, {@link #SECURE_CONNECTED_DEVICE} will be enrolled and {@link
   * #FAKE_TOKEN} will be activated.
   */
  private void executeAndVerifyValidEnrollFlowOnSecureCar() throws RemoteException {
    // Add escrow token
    WeakEscrowTokenActivatedListener listener = executeAndVerifyTokenAddedInEnrollFlowOnSecureCar();

    // Now notify that the token has been activated by the user entering their credentials.
    listener.onWeakEscrowTokenActivated(FAKE_HANDLE, DEFAULT_USER);
  }

  /**
   * Runs through a valid enrollment flow and verifies that the current flow is run on an insecure
   * car.
   *
   * <p>At the end of this method, {@link #SECURE_CONNECTED_DEVICE} will be enrolled and {@link
   * #FAKE_TOKEN} will be activated.
   */
  private void executeAndVerifyValidEnrollFlowOnInsecureCar() throws RemoteException {
    ArgumentCaptor<WeakEscrowTokenActivatedListener> captor =
        ArgumentCaptor.forClass(WeakEscrowTokenActivatedListener.class);
    // First, the phone will send an escrow token to start the enrollment.
    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE, createTokenMessage(FAKE_TOKEN));
    verify(notificationRequestListener).onTrustedDeviceEnrollmentNotificationRequest();

    // The user confirms enrollment through UI on the insecure car.
    manager.processEnrollment(/* isDeviceSecure= */ false);
    verify(keyguardManager)
        .addWeakEscrowToken(eq(FAKE_TOKEN), eq(DEFAULT_USER), any(), captor.capture());

    captor.getValue().onWeakEscrowTokenActivated(FAKE_HANDLE, DEFAULT_USER);
    verify(enrollmentCallback).onSecureDeviceRequest();
    assertThat(manager.isWaitingForCredentialSetUp.get()).isTrue();

    // The user creates credential and continues enrollment on the secure car.
    manager.setTrustedDeviceAgentDelegate(trustAgentDelegate);
    manager.processEnrollment(/* isDeviceSecure= */ true);
    assertThat(manager.isWaitingForCredentialSetUp.get()).isFalse();
  }

  private void triggerDeviceConnected(ConnectedDevice device) throws RemoteException {
    when(fakeConnector.getConnectedDevices()).thenReturn(Arrays.asList(device));
    feature.onSecureChannelEstablished(device);
  }

  private static byte[] createTokenMessage(byte[] token) {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TrustedDeviceManager.TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.ESCROW_TOKEN)
        .setPayload(ByteString.copyFrom(token))
        .build()
        .toByteArray();
  }
}
