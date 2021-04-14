package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.connecteddevice.calendarsync.Calendar;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CalendarStoreTest {

  private static final String DEVICE_ID = "a device id";
  private static final String CALENDAR_KEY = "a calendar id";

  private CalendarStore store;

  @Before
  public void setUp() {
    store = new CalendarStore();
  }

  @Test
  public void store_canFetchCalendar() {
    Calendar calendar = Calendar.newBuilder().setKey(CALENDAR_KEY).build();

    store.store(DEVICE_ID, calendar);
    Calendar fetched = store.fetch(DEVICE_ID, CALENDAR_KEY);

    assertThat(fetched).isEqualTo(calendar);
  }

  @Test
  public void remove_cannotFetchCalendar() {
    Calendar calendar = Calendar.newBuilder().setKey(CALENDAR_KEY).build();

    store.store(DEVICE_ID, calendar);
    store.remove(DEVICE_ID, CALENDAR_KEY);
    Calendar fetched = store.fetch(DEVICE_ID, CALENDAR_KEY);

    assertThat(fetched).isNull();
  }

  @Test
  public void removeAll_cannotFetchCalendar() {
    Calendar calendar = Calendar.newBuilder().setKey(CALENDAR_KEY).build();

    store.store(DEVICE_ID, calendar);
    store.removeAll(DEVICE_ID);
    Calendar fetched = store.fetch(DEVICE_ID, CALENDAR_KEY);

    assertThat(fetched).isNull();
  }

  @Test
  public void clear_cannotFetchCalendar() {
    Calendar calendar = Calendar.newBuilder().setKey(CALENDAR_KEY).build();

    store.store(DEVICE_ID, calendar);
    store.clear();
    Calendar fetched = store.fetch(DEVICE_ID, CALENDAR_KEY);

    assertThat(fetched).isNull();
  }
}
