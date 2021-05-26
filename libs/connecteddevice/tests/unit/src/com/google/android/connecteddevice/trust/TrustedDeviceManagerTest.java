package com.google.android.connecteddevice.trust;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import androidx.room.Room;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.api.IConnectedDeviceManager;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceMessage.MessageType;
import com.google.android.connecteddevice.trust.proto.TrustedDeviceMessageProto.TrustedDeviceState;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.List;
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
  private static final String DEFAULT_DEVICE_ID = "deviceId";

  // Note: This token needs to be of length 8 to be valid.
  private static final byte[] FAKE_TOKEN = "12345678".getBytes(UTF_8);

  // Note: The value of this handle is arbitrary.
  private static final long FAKE_HANDLE = 111L;

  private static final int DEFAULT_USER_ID = ActivityManager.getCurrentUser();

  private static final ConnectedDevice SECURE_CONNECTED_DEVICE =
      new ConnectedDevice(
          DEFAULT_DEVICE_ID,
          "secureConnectedDevice",
          /* belongsToActiveUser= */ true,
          /* hasSecureChannel= */ true);

  @Captor private ArgumentCaptor<List<TrustedDevice>> trustedDeviceListCaptor;

  @Mock private ITrustedDeviceEnrollmentCallback enrollmentCallback;
  @Mock private ITrustedDeviceCallback trustedDeviceCallback;
  @Mock private ITrustedDeviceAgentDelegate trustAgentDelegate;
  @Mock private IOnTrustedDevicesRetrievedListener trustedDeviceListener;
  @Mock private IConnectedDeviceManager mockConnectedDeviceManager;

  private TrustedDeviceManager manager;
  private TrustedDeviceFeature feature;
  private TrustedDeviceDatabase database;

  @Before
  public void setUp() throws RemoteException {
    MockitoAnnotations.initMocks(this);
    Context context = ApplicationProvider.getApplicationContext();

    // Required because ShadowRemoteCallbackList will invoke these methods and requires non-null
    // values.
    when(enrollmentCallback.asBinder()).thenReturn(mock(IBinder.class));
    when(trustedDeviceCallback.asBinder()).thenReturn(mock(IBinder.class));

    database =
        Room.inMemoryDatabaseBuilder(context, TrustedDeviceDatabase.class)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor())
            .build();

    feature = spy(new TrustedDeviceFeature(context, mockConnectedDeviceManager));

    manager =
        new TrustedDeviceManager(
            database,
            feature,
            /* databaseExecutor= */ directExecutor(),
            /* remoteCallbackExecutor= */ directExecutor());

    manager.setTrustedDeviceAgentDelegate(trustAgentDelegate);
    manager.registerTrustedDeviceEnrollmentCallback(enrollmentCallback);
    manager.registerTrustedDeviceCallback(trustedDeviceCallback);
  }

  @After
  public void tearDown() {
    database.close();
  }

  @Test
  public void testValidEnrollmentFlow_notifiesCallback() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlow();

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    verify(trustedDeviceCallback).onTrustedDeviceAdded(expectedTrustedDevice);
  }

  @Test
  public void testValidEnrollmentFlow_storesTrustedDevice() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlow();

    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  @Test
  public void testStateSync_removesStoredTrustedDevice() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlow();

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
    executeAndVerifyValidEnrollFlow();

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
    executeAndVerifyValidEnrollFlow();

    ConnectedDevice unenrolledDevice =
        new ConnectedDevice(
            "unenrolled",
            "unenrolled",
            /* belongsToActiveUser= */ true,
            /* hasSecureChannel= */ true);

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
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlow();

    TrustedDevice trustedDevice =
        new TrustedDevice(DEFAULT_DEVICE_ID, DEFAULT_USER_ID, FAKE_HANDLE);
    manager.removeTrustedDevice(trustedDevice);

    verify(feature)
        .sendMessageSecurely(SECURE_CONNECTED_DEVICE, createStateSyncMessage(/* enabled= */ false));
  }

  @Test
  public void testStateSync_doesNotSendDuplicateStateMessages() throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);
    executeAndVerifyValidEnrollFlow();

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
    executeAndVerifyValidEnrollFlow();

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
    executeAndVerifyValidEnrollFlow();

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
    executeAndVerifyValidEnrollFlow();

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
  public void testClearTrustedDeviceAgentDelegate_invalidateTrustedDevicesOnLockScreenRemoved()
      throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    executeAndVerifyValidEnrollFlow();

    manager.clearTrustedDeviceAgentDelegate(trustAgentDelegate, /* isDeviceSecure= */ false);
    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    assertThat(trustedDeviceListCaptor.getValue()).isEmpty();
  }

  @Test
  public void testClearTrustedDeviceAgentDelegate_invalidateTrustedDevicesOnLockScreenSet()
      throws RemoteException {
    triggerDeviceConnected(SECURE_CONNECTED_DEVICE);

    executeAndVerifyValidEnrollFlow();

    manager.clearTrustedDeviceAgentDelegate(trustAgentDelegate, /* isDeviceSecure= */ true);
    manager.retrieveTrustedDevicesForActiveUser(trustedDeviceListener);
    verify(trustedDeviceListener).onTrustedDevicesRetrieved(trustedDeviceListCaptor.capture());

    TrustedDevice expectedTrustedDevice =
        new TrustedDevice(SECURE_CONNECTED_DEVICE.getDeviceId(), DEFAULT_USER_ID, FAKE_HANDLE);

    assertThat(trustedDeviceListCaptor.getValue()).containsExactly(expectedTrustedDevice);
  }

  /**
   * Runs through a valid enrollment flow and verifies that the current flow is run.
   *
   * <p>At the end of this method, {@link #SECURE_CONNECTED_DEVICE} will be enrolled and {@link
   * #FAKE_TOKEN} will be activated.
   */
  private void executeAndVerifyValidEnrollFlow() throws RemoteException {
    // First, the phone will send an escrow token to start the enrollment.
    manager.featureCallback.onMessageReceived(
        SECURE_CONNECTED_DEVICE,
        createTokenMessage(FAKE_TOKEN));
    verify(trustAgentDelegate).addEscrowToken(FAKE_TOKEN, DEFAULT_USER_ID);

    // The system should then let the manager know the add was successful.
    manager.onEscrowTokenAdded(DEFAULT_USER_ID, FAKE_HANDLE);
    enrollmentCallback.onValidateCredentialsRequest();

    //  Now notify that the token has been activated by the user entering their credentials.
    manager.onEscrowTokenActivated(DEFAULT_USER_ID, FAKE_HANDLE);
  }

  private void triggerDeviceConnected(ConnectedDevice device) throws RemoteException {
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices())
        .thenReturn(Arrays.asList(device));
    feature.onSecureChannelEstablished(device);
  }

  private void mockNoDevicesConnected() throws RemoteException {
    when(mockConnectedDeviceManager.getActiveUserConnectedDevices()).thenReturn(ImmutableList.of());
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
