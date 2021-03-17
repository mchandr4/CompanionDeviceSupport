package com.google.android.connecteddevice.calendarsync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.calendarsync.android.CalendarSyncAccess;
import com.google.android.connecteddevice.calendarsync.common.ReplicaCalendarSync;
import com.google.android.connecteddevice.model.ConnectedDevice;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class CalendarSyncFeature2Test {
  private static final String DEVICE_ID = "The device id";
  private static final byte[] MESSAGE = "A message from the phone".getBytes(StandardCharsets.UTF_8);

  @Mock private CalendarSyncAccess.Factory<ReplicaCalendarSync> mockCalendarSyncAccessFactory;
  @Mock private CalendarSyncAccess<ReplicaCalendarSync> mockCalendarSyncAccess;
  @Mock private ConnectedDevice mockConnectedDevice;
  @Mock private ReplicaCalendarSync mockCalendarSync;

  private CalendarSyncFeature2 feature;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Context context = ApplicationProvider.getApplicationContext();
    when(mockConnectedDevice.getDeviceId()).thenReturn(DEVICE_ID);
    when(mockConnectedDevice.hasSecureChannel()).thenReturn(true);

    // When access(callback) is called, pass mockCalendarSync to the callback.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  Consumer<ReplicaCalendarSync> callback = invocation.getArgument(0);
                  callback.accept(mockCalendarSync);
                  return null;
                })
        .when(mockCalendarSyncAccess)
        .access(any());
    when(mockCalendarSyncAccessFactory.create(any())).thenReturn(mockCalendarSyncAccess);

    feature = new CalendarSyncFeature2(context, mockCalendarSyncAccessFactory);
  }

  @Test
  public void start_startsCalendarSyncAccess() {
    feature.start();

    verify(mockCalendarSyncAccess).start();
  }

  @Test
  public void stop_stopsCalendarSyncAccess() {
    feature.stop();

    verify(mockCalendarSyncAccess).stop();
  }

  @Test
  public void onMessageReceived_callsSyncReceive() {
    feature.onMessageReceived(mockConnectedDevice, MESSAGE);

    verify(mockCalendarSync).receive(DEVICE_ID, MESSAGE);
  }

  @Test
  public void onDeviceDisconnected_callsSyncClear() {
    feature.onDeviceDisconnected(mockConnectedDevice);

    verify(mockCalendarSync).clear(DEVICE_ID);
  }
}
