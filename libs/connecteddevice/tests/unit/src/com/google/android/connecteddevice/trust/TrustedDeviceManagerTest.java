package com.google.android.connecteddevice.trust;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
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
import com.google.android.connecteddevice.trust.proto.PhoneAuthProto.PhoneCredentials;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage.MessageType;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceState;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceTokenEntity;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class TrustedDeviceManagerTest {
  private static final String DEFAULT_DEVICE_ID = UUID.randomUUID().toString();

  // Note: This token needs to be of length 8 to be valid.
  private static final byte[] FAKE_TOKEN = "12345678".getBytes(UTF_8);

  // Note: The value of this handle is arbitrary.
  private static final long FAKE_HANDLE = 111L;

  private static final int DEFAULT_USER_ID = ActivityManager.getCurrentUser();

  private static final ConnectedDevice SECURE_CONNECTED_DEVICE =
      new ConnectedDevice(
          DEFAULT_DEVICE_ID,
          "secureConnectedDevice",
          /* belongsToDriver= */ true,
          /* hasSecureChannel= */ true);

  private final Connector fakeConnector = spy(new FakeConnector());

  @Captor private ArgumentCaptor<List<TrustedDevice>> trustedDeviceListCaptor;

  @Mock private ITrustedDeviceEnrollmentCallback enrollmentCallback;
  @Mock private ITrustedDeviceCallback trustedDeviceCallback;
  @Mock private ITrustedDeviceAgentDelegate trustAgentDelegate;
  @Mock private IOnTrustedDevicesRetrievedListener trustedDeviceListener;
  @Mock private IOnTrustedDeviceEnrollmentNotificationRequestListener notificationRequestListener;

  private TrustedDeviceManager manager;
  private TrustedDeviceFeature feature;
  private TrustedDeviceDatabase database;
  private TrustedDeviceFeature.Callback featureCallback;

  @Before
  public void setUp() throws RemoteException {
    MockitoAnnotations.initMocks(this);
    Context context = ApplicationProvider.getApplicationContext();

    // Required because ShadowRemoteCallbackList will invoke these methods and requires non-null
    // values.
    when(enrollmentCallback.asBinder()).thenReturn(mock(IBinder.class));
    when(trustedDeviceCallback.asBinder()).thenReturn(mock(IBinder.class));
    when(notificationRequestListener.asBinder()).thenReturn(mock(IBinder.class));

    database =
        Room.inMemoryDatabaseBuilder(context, TrustedDeviceDatabase.class)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build();

    feature = spy(new TrustedDeviceFeature(context, fakeConnector));

    manager =
        new TrustedDeviceManager(
            database,
            feature,
            /* databaseExecutor= */ directExecutor(),
            /* remoteCallbackExecutor= */ directExecutor());

    manager.setTrustedDeviceAgentDelegate(trustAgentDelegate);
    manager.registerTrustedDeviceEnrollmentCallback(enrollmentCallback);
    manager.registerTrustedDeviceCallback(trustedDeviceCallback);
    manager.registerTrustedDeviceEnrollmentNotificationRequestListener(notificationRequestListener);
    ArgumentCaptor<TrustedDeviceFeature.Callback> captor =
        ArgumentCaptor.forClass(TrustedDeviceFeature.Callback.class);
    verify(feature).setCallback(captor.capture());
    featureCallback = captor.getValue();
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
    verify(trustAgentDelegate).removeEscrowToken(FAKE_HANDLE, DEFAULT_USER_ID);
  }

  @Test
  public void testStateSync_removesStoredTrustedDevice() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE,
        createStateSyncMessage(/* enabled= */ false));

    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    assertThat(trustedDeviceListCaptor.getValue()).isEmpty();
  }

  @Test
  public void testStateSync_ignoresEnabledMessage() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE,
        createStateSyncMessage(/* enabled= */ true));

    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  @Test
  public void testStateSync_ignoresStateMessageFromUnenrolledDevice() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    ConnectedDevice unenrolledDevice =
        new ConnectedDevice(
            "unenrolled", "unenrolled", /* belongsToDriver= */ true, /* hasSecureChannel= */ true);

    manager.featureCallback.onMessageReceived(
        unenrolledDevice, createStateSyncMessage(/* enabled= */ false));

    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    // Should not affect the existing trusted device.
    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  @Test
  public void testStateSync_sendsStateMessage() throws RemoteException {
    fakeConnector
        .getConnectedDevices()
        .add(new ConnectedDevice(DEFAULT_DEVICE_ID, null, true, true));
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice trustedDevice =
        new TrustedDevice(DEFAULT_DEVICE_ID, DEFAULT_USER_ID, FAKE_HANDLE);
    manager.removeTrustedDevice(trustedDevice);

    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));
  }

  @Test
  public void testStateSync_doesNotSendDuplicateStateMessages() throws RemoteException {
    when(fakeConnector.getConnectedDeviceById(DEFAULT_DEVICE_ID))
        .thenReturn(new ConnectedDevice(DEFAULT_DEVICE_ID, null, true, true));
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice trustedDevice =
        new TrustedDevice(DEFAULT_DEVICE_ID, DEFAULT_USER_ID, FAKE_HANDLE);
    manager.removeTrustedDevice(trustedDevice);

    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));

    mockNoDevicesConnected();
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    // Should not have sent an additional message, so the count remains at 1.
    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));
  }

  @Test
  public void testStateSync_savesStateMessageUntilNextConnection() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice trustedDevice =
        new TrustedDevice(DEFAULT_DEVICE_ID, DEFAULT_USER_ID, FAKE_HANDLE);

    mockNoDevicesConnected();
    manager.removeTrustedDevice(trustedDevice);
    verify(feature, never())
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));

    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));

    mockNoDevicesConnected();
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    // Should not send an additional message, so the count remains at 1.
    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));
  }

  @Test
  public void testRemoveTrustedDevice_trustedDeviceAgentDelegateNotSet() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice device =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    manager.clearTrustedDeviceAgentDelegate(trustAgentDelegate, /* isDeviceSecure= */ false);
    List<TrustedDeviceEntity> invalidEntities =
        database.trustedDeviceDao().getInvalidTrustedDevicesForUser(DEFAULT_USER_ID);
    List<TrustedDeviceEntity> validEntities =
        database.trustedDeviceDao().getValidTrustedDevicesForUser(DEFAULT_USER_ID);
    TrustedDeviceEntity expectedEntity = new TrustedDeviceEntity(device, /* isValid= */ false);
    assertThat(invalidEntities).containsExactly(expectedEntity);
    assertThat(validEntities).isEmpty();

    verify(trustedDeviceCallback).onTrustedDeviceRemoved(device);
  }

  @Test
  public void testRemoveTrustedDevice_trustedDeviceAgentDelegateSet() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice device =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);
    manager.removeTrustedDevice(device);
    List<TrustedDeviceEntity> invalidEntities =
        database.trustedDeviceDao().getInvalidTrustedDevicesForUser(DEFAULT_USER_ID);
    List<TrustedDeviceEntity> validEntities =
        database.trustedDeviceDao().getValidTrustedDevicesForUser(DEFAULT_USER_ID);
    assertThat(invalidEntities).isEmpty();
    assertThat(validEntities).isEmpty();

    verify(trustedDeviceCallback).onTrustedDeviceRemoved(device);
  }

  @Test
  public void testRemoveEscrowToken_removeEscrowToken() throws RemoteException {
    manager.removeEscrowToken(FAKE_HANDLE, DEFAULT_USER_ID);

    verify(trustAgentDelegate).removeEscrowToken(eq(FAKE_HANDLE), eq(DEFAULT_USER_ID));
  }

  @Test
  public void testClearTrustedDeviceAgentDelegate_invalidateTrustedDevicesOnLockScreenRemoved()
      throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    executeAndVerifyValidEnrollFlowOnSecureCar();

    manager.clearTrustedDeviceAgentDelegate(trustAgentDelegate, /* isDeviceSecure= */ false);
    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    assertThat(trustedDeviceListCaptor.getValue()).isEmpty();
  }

  @Test
  public void testClearTrustedDeviceAgentDelegate_invalidateTrustedDevicesOnLockScreenSet()
      throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    executeAndVerifyValidEnrollFlowOnSecureCar();

    manager.clearTrustedDeviceAgentDelegate(trustAgentDelegate, /* isDeviceSecure= */ true);
    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  @Test
  public void unlock_validTokenPassedToDelegate() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDeviceMessage unlockMessage =
        TrustedDeviceMessage.newBuilder()
            .setType(MessageType.UNLOCK_CREDENTIALS)
            .setPayload(
                PhoneCredentials.newBuilder()
                    .setEscrowToken(ByteString.copyFrom(FAKE_TOKEN))
                    .setHandle(ByteString.copyFrom(ByteUtils.longToBytes(FAKE_HANDLE)))
                    .build()
                    .toByteString())
            .build();
    featureCallback.onMessageReceived(SECURE_CONNECTED_DEVICE, unlockMessage.toByteArray());

    verify(trustAgentDelegate).unlockUserWithToken(FAKE_TOKEN, FAKE_HANDLE, DEFAULT_USER_ID);
  }

  @Test
  public void unlock_invalidTokenIsNotPassedToDelegate() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();
    byte[] incorrectToken = ByteUtils.randomBytes(8);

    TrustedDeviceMessage unlockMessage =
        TrustedDeviceMessage.newBuilder()
            .setType(MessageType.UNLOCK_CREDENTIALS)
            .setPayload(
                PhoneCredentials.newBuilder()
                    .setEscrowToken(ByteString.copyFrom(incorrectToken))
                    .setHandle(ByteString.copyFrom(ByteUtils.longToBytes(FAKE_HANDLE)))
                    .build()
                    .toByteString())
            .build();
    featureCallback.onMessageReceived(SECURE_CONNECTED_DEVICE, unlockMessage.toByteArray());

    verify(trustAgentDelegate, never()).unlockUserWithToken(any(), anyLong(), anyInt());
  }

  @Test
  public void unlock_missingHashedTokenIsNotPassedToDelegate() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();
    database.trustedDeviceDao().removeTrustedDeviceHashedToken(DEFAULT_DEVICE_ID);

    TrustedDeviceMessage unlockMessage =
        TrustedDeviceMessage.newBuilder()
            .setType(MessageType.UNLOCK_CREDENTIALS)
            .setPayload(
                PhoneCredentials.newBuilder()
                    .setEscrowToken(ByteString.copyFrom(FAKE_TOKEN))
                    .setHandle(ByteString.copyFrom(ByteUtils.longToBytes(FAKE_HANDLE)))
                    .build()
                    .toByteString())
            .build();
    featureCallback.onMessageReceived(SECURE_CONNECTED_DEVICE, unlockMessage.toByteArray());

    verify(trustAgentDelegate, never()).unlockUserWithToken(any(), anyLong(), anyInt());
  }

  @Test
  public void removeTrustedDevice_removesHashedToken() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();
    ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
    verify(trustedDeviceCallback).onTrustedDeviceAdded(captor.capture());
    TrustedDevice enrolledDevice = captor.getValue();

    manager.removeTrustedDevice(enrolledDevice);

    TrustedDeviceTokenEntity entity =
        database.trustedDeviceDao().getTrustedDeviceHashedToken(DEFAULT_DEVICE_ID);
    assertThat(entity).isNull();
  }

  @Test
  public void setTrustedDeviceAgentDelegate_generatesFeatureStateAndRemovesAllInvalidDevices()
      throws RemoteException {
    String deviceId = UUID.randomUUID().toString();
    TrustedDeviceEntity entity =
        new TrustedDeviceEntity(
            new TrustedDevice(deviceId, DEFAULT_USER_ID, FAKE_HANDLE), /* isValid= */ false);
    database.trustedDeviceDao().addOrReplaceTrustedDevice(entity);
    TrustedDeviceManager manager =
        new TrustedDeviceManager(
            database,
            feature,
            /* databaseExecutor= */ directExecutor(),
            /* remoteCallbackExecutor= */ directExecutor());

    manager.setTrustedDeviceAgentDelegate(trustAgentDelegate);

    assertThat(database.trustedDeviceDao().getFeatureState(deviceId)).isNotNull();
    assertThat(database.trustedDeviceDao().getTrustedDevice(deviceId)).isNull();
    verify(trustAgentDelegate).removeEscrowToken(FAKE_HANDLE, DEFAULT_USER_ID);
  }

  @Test
  public void testNotifyRemoteEnrollmentCallbacks_callbackNotified() throws RemoteException {
    manager.notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onSecureDeviceRequest();
          } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
          }
        });
    manager.notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onValidateCredentialsRequest();
          } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
          }
        });

    verify(enrollmentCallback).onSecureDeviceRequest();
    verify(enrollmentCallback).onValidateCredentialsRequest();
  }

  @Test
  public void testInvalidateTrustedDevice_deviceInvalidated() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlowOnSecureCar();

    TrustedDevice testTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);
    manager.invalidateTrustedDevice(new TrustedDeviceEntity(testTrustedDevice));
    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    assertThat(trustedDeviceListCaptor.getValue()).isEmpty();
  }

  @Test
  public void testGetPendingToken() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyTokenAddedInEnrollFlowOnSecureCar();

    assertThat(manager.getPendingToken()).isEqualTo(FAKE_TOKEN);
  }

  @Test
  public void testGetTrustedDeviceDatabase() {
    assertThat(manager.getTrustedDeviceDatabase()).isEqualTo(database.trustedDeviceDao());
  }

  /**
   * Runs through adding token flow in enrollment and verifies it is run on a secure car.
   *
   * <p>At the end of this method, {@link #FAKE_TOKEN} will be added and waiting to be activated for
   * {@link #SECURE_CONNECTED_DEVICE}.
   */
  private void executeAndVerifyTokenAddedInEnrollFlowOnSecureCar() throws RemoteException {
    // First, the phone will send an escrow token to start the enrollment.
    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE, createTokenMessage(FAKE_TOKEN));
    verify(notificationRequestListener).onTrustedDeviceEnrollmentNotificationRequest();

    // The user confirms enrollment through UI on the secure car.
    manager.processEnrollment(/* isDeviceSecure= */ true);

    verify(trustAgentDelegate).addEscrowToken(FAKE_TOKEN, DEFAULT_USER_ID);

    // The system should then let the manager know the add was successful.
    manager.onEscrowTokenAdded(DEFAULT_USER_ID, FAKE_HANDLE);
    verify(enrollmentCallback).onValidateCredentialsRequest();
  }

  /**
   * Runs through a valid enrollment flow and verifies that the current flow is run on a secure car.
   *
   * <p>At the end of this method, {@link #SECURE_CONNECTED_DEVICE} will be enrolled and {@link
   * #FAKE_TOKEN} will be activated.
   */
  private void executeAndVerifyValidEnrollFlowOnSecureCar() throws RemoteException {
    // Add escrow token
    executeAndVerifyTokenAddedInEnrollFlowOnSecureCar();

    // Now notify that the token has been activated by the user entering their credentials.
    manager.onEscrowTokenActivated(DEFAULT_USER_ID, FAKE_HANDLE);
  }

  /**
   * Runs through a valid enrollment flow and verifies that the current flow is run on an insecure
   * car.
   *
   * <p>At the end of this method, {@link #SECURE_CONNECTED_DEVICE} will be enrolled and {@link
   * #FAKE_TOKEN} will be activated.
   */
  private void executeAndVerifyValidEnrollFlowOnInsecureCar() throws RemoteException {
    // First, the phone will send an escrow token to start the enrollment.
    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE,
        createTokenMessage(FAKE_TOKEN));
    verify(notificationRequestListener).onTrustedDeviceEnrollmentNotificationRequest();

    // The user confirms enrollment through UI on the insecure car.
    manager.processEnrollment(/* isDeviceSecure= */ false);
    verify(enrollmentCallback).onSecureDeviceRequest();

    // The user creates credential and continues enrollment on the secure car.
    manager.processEnrollment(/* isDeviceSecure= */ true);
    verify(trustAgentDelegate).addEscrowToken(FAKE_TOKEN, DEFAULT_USER_ID);

    // The system should then let the manager know the add was successful.
    manager.onEscrowTokenAdded(DEFAULT_USER_ID, FAKE_HANDLE);
    enrollmentCallback.onValidateCredentialsRequest();

    //  Now notify that the token has been activated by the user entering their credentials.
    manager.onEscrowTokenActivated(DEFAULT_USER_ID, FAKE_HANDLE);
  }

  private void triggerDeviceConnected(ConnectedDevice device) {
    when(fakeConnector.getConnectedDevices()).thenReturn(Arrays.asList(device));
    feature.onSecureChannelEstablished(device);
  }

  private void mockNoDevicesConnected() {
    when(fakeConnector.getConnectedDevices()).thenReturn(ImmutableList.of());
  }

  private static byte[] createTokenMessage(byte[] token) {
    return TrustedDeviceMessage.newBuilder()
        .setVersion(TrustedDeviceManager.TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.ESCROW_TOKEN)
        .setPayload(ByteString.copyFrom(token))
        .build()
        .toByteArray();
  }

  private static byte[] createStateSyncMessage(boolean enabled) {
    TrustedDeviceState state = TrustedDeviceState.newBuilder().setEnabled(enabled).build();

    return TrustedDeviceMessage.newBuilder()
        .setVersion(TrustedDeviceManager.TRUSTED_DEVICE_MESSAGE_VERSION)
        .setType(MessageType.STATE_SYNC)
        .setPayload(state.toByteString())
        .build()
        .toByteArray();
  }
}
