package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.calendarsync.common.CommonLogger;
import com.google.android.connecteddevice.calendarsync.common.ReplicaCalendarSync;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CalendarSyncAccessTest {

  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private ReplicaCalendarSync mockCalendarSync;

  private CalendarSyncAccess<ReplicaCalendarSync> calendarSyncAccess;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));
    calendarSyncAccess = new CalendarSyncAccess<>(mockLoggerFactory, (handler) -> mockCalendarSync);
  }

  @Test
  public void factoryCreate_startSync_doesNotDie() {
    Context context = ApplicationProvider.getApplicationContext();
    CalendarSyncAccess.Factory<ReplicaCalendarSync> factory =
        CalendarSyncAccess.Factory.createReplicaFactory(
            mockLoggerFactory, context.getContentResolver());

    CalendarSyncAccess<ReplicaCalendarSync> calendarSyncAccess =
        factory.create((id, message) -> {});
    calendarSyncAccess.start();
  }

  @Test
  public void start_isStarted() {
    assertThat(calendarSyncAccess.isStarted()).isFalse();

    calendarSyncAccess.start();

    assertThat(calendarSyncAccess.isStarted()).isTrue();
  }

  @Test
  public void stop_isNotStarted() {
    calendarSyncAccess.start();
    calendarSyncAccess.stop();

    assertThat(calendarSyncAccess.isStarted()).isFalse();
  }

  @Test
  public void access_runsTaskOnBackgroundLooper() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    calendarSyncAccess.start();
    calendarSyncAccess.access(
        (sync) -> {
          assertThat(sync).isNotNull();
          assertThat(Looper.myLooper()).isNotEqualTo(Looper.getMainLooper());
          latch.countDown();
        });

    assertThat(latch.await(2, SECONDS)).isTrue();
  }
}
