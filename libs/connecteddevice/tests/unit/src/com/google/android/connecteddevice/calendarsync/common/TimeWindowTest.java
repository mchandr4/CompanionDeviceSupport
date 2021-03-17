package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TimeWindowTest {
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);

  @Test
  public void wholeDayTimeWindow_isCorrectDuration() {
    TimeWindow window = TimeWindow.wholeDayTimeWindow(ZONED_DATE_TIME, 7);
    assertThat(window.getTo()).isGreaterThan(window.getFrom());
    assertThat(Duration.between(window.getFrom(), window.getTo())).isEqualTo(Duration.of(7, DAYS));
  }
}
