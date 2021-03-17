package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import com.google.android.connecteddevice.calendarsync.common.Scheduler.ScheduledTaskHandle;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class HandlerSchedulerTest {

  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);

  private HandlerScheduler scheduler;
  private Clock clock;
  private Handler handler;

  @Before
  public void setUp() {
    HandlerThread thread = new HandlerThread("Test thread");
    thread.start();
    handler = new Handler(thread.getLooper());
    clock = Clock.fixed(ZONED_DATE_TIME.toInstant(), ZONE_ID);
    scheduler = new HandlerScheduler(handler, clock);
  }

  @Test
  public void schedule_runsTask() {
    boolean[] ran = new boolean[1];
    Runnable task = () -> ran[0] = true;
    ShadowLooper shadowLooper = shadowOf(handler.getLooper());
    shadowLooper.pause();

    scheduler.schedule(clock.instant().plusSeconds(1), task);

    Duration expected = Duration.ofSeconds(1).plusMillis(SystemClock.uptimeMillis());
    assertThat(shadowLooper.getNextScheduledTaskTime()).isEqualTo(expected);
    shadowLooper.runToEndOfTasks();
    assertThat(ran[0]).isTrue();
  }

  @Test
  public void cancel_doesNotRunTask() {
    boolean[] ran = new boolean[1];
    Runnable task = () -> ran[0] = true;
    ShadowLooper shadowLooper = shadowOf(handler.getLooper());
    shadowLooper.pause();

    ScheduledTaskHandle handle = scheduler.schedule(clock.instant().plusSeconds(1), task);
    handle.cancel();

    shadowLooper.runToEndOfTasks();
    assertThat(ran[0]).isFalse();
  }
}
