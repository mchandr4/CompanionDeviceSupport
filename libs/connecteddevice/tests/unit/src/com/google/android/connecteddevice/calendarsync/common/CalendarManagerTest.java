package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.connecteddevice.calendarsync.Calendar;
import com.google.android.connecteddevice.calendarsync.Event;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class CalendarManagerTest {
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);
  private static final Instant FROM_TIME = ZONED_DATE_TIME.toInstant();
  private static final Instant TO_TIME = ZONED_DATE_TIME.plusDays(2).toInstant();
  private static final Range<Instant> TIME_RANGE = Range.openClosed(FROM_TIME, TO_TIME);
  private static final Object DEVICE_ID = "a device id";
  private static final String CALENDAR_KEY = "a calendar id";
  private static final long CALENDAR_ID = 1L;

  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private PlatformContentDelegate<Calendar> mockCalendarContentDelegate;
  @Mock private EventManagerFactory mockEventManagerFactory;
  @Mock private EventManager mockEventManager;
  @Mock private CalendarStore mockCalendarStore;

  @Captor ArgumentCaptor<Range<Instant>> timeRangeCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));

    when(mockCalendarContentDelegate.read(DEVICE_ID, CALENDAR_KEY))
        .thenReturn(new Content<>(Calendar.newBuilder().setKey(CALENDAR_KEY).build(), CALENDAR_ID));
    when(mockCalendarContentDelegate.readAll(DEVICE_ID))
        .thenReturn(
            ImmutableList.of(
                new Content<>(Calendar.newBuilder().setKey(CALENDAR_KEY).build(), CALENDAR_ID)));
    when(mockCalendarContentDelegate.insert(eq(DEVICE_ID), any())).thenReturn(CALENDAR_ID);

    when(mockEventManagerFactory.create(any())).thenReturn(mockEventManager);

    when(mockEventManager.readAll(any()))
        .thenReturn(ImmutableList.of(Event.newBuilder().setKey("Some event key").build()));
  }

  @Test
  public void read_hasChildren() {
    Map<String, Range<Instant>> calendarKeyToTimeRange = new HashMap<>();
    calendarKeyToTimeRange.put(CALENDAR_KEY, TIME_RANGE);
    CalendarManager manager = createCalendarManager(calendarKeyToTimeRange);

    Calendar calendar = manager.read(DEVICE_ID, CALENDAR_KEY);

    assertThat(calendar.getEventsCount()).isEqualTo(1);
  }

  @Test
  public void read_hasTimeRange() {
    CalendarManager manager =
        createCalendarManager(ImmutableMap.of(CALENDAR_KEY, Range.closedOpen(FROM_TIME, TO_TIME)));
    Calendar calendar = manager.read(DEVICE_ID, CALENDAR_KEY);

    assertThat(calendar.hasRange()).isTrue();
    assertThat(calendar.getRange().getFrom().getSeconds()).isEqualTo(FROM_TIME.getEpochSecond());
    assertThat(calendar.getRange().getTo().getSeconds()).isEqualTo(TO_TIME.getEpochSecond());
  }

  @Test
  public void readAll_returnsCalendars() {
    Map<String, Range<Instant>> calendarKeyToTimeRange = new HashMap<>();
    calendarKeyToTimeRange.put(CALENDAR_KEY, TIME_RANGE);
    CalendarManager manager = createCalendarManager(calendarKeyToTimeRange);
    ImmutableList<Calendar> calendars = manager.readAll(DEVICE_ID);

    assertThat(calendars).hasSize(1);
  }

  @Test
  public void create_createsChild() {
    CalendarManager manager = createCalendarManager(new HashMap<>());
    String eventKey = "child event key";
    Event event = Event.newBuilder().setKey(eventKey).build();
    Calendar calendar =
        Calendar.newBuilder()
            .setKey(CALENDAR_KEY)
            .setRange(TimeProtoUtil.toTimeRange(TIME_RANGE))
            .addEvents(event)
            .build();

    manager.create(DEVICE_ID, calendar);

    verify(mockEventManager).create(CALENDAR_ID, event);
  }

  @Test
  public void create_createsEventManagerWithTimeRange() {
    CalendarManager manager = createCalendarManager(new HashMap<>());
    Calendar calendar =
        Calendar.newBuilder()
            .setKey(CALENDAR_KEY)
            .setRange(TimeProtoUtil.toTimeRange(TIME_RANGE))
            .build();

    manager.create(DEVICE_ID, calendar);

    // Conversion to proto timestamp loses the nanos.
    verify(mockEventManagerFactory).create(timeRangeCaptor.capture());
    Range<Instant> range = timeRangeCaptor.getValue();
    assertThat(range.lowerEndpoint().getEpochSecond()).isEqualTo(FROM_TIME.getEpochSecond());
    assertThat(range.upperEndpoint().getEpochSecond()).isEqualTo(TO_TIME.getEpochSecond());
  }

  @Test
  public void create_insertsCalendar() {
    CalendarManager manager = createCalendarManager(new HashMap<>());
    Calendar calendar =
        Calendar.newBuilder()
            .setKey(CALENDAR_KEY)
            .setRange(TimeProtoUtil.toTimeRange(TIME_RANGE))
            .build();

    manager.create(DEVICE_ID, calendar);

    verify(mockCalendarContentDelegate).insert(DEVICE_ID, calendar);
  }

  @Test
  public void delete_deletesCalendar() {
    Map<String, Range<Instant>> calendarKeyToTimeRange = new HashMap<>();
    calendarKeyToTimeRange.put(CALENDAR_KEY, TIME_RANGE);
    CalendarManager manager = createCalendarManager(calendarKeyToTimeRange);

    manager.delete(DEVICE_ID, CALENDAR_KEY);

    verify(mockCalendarContentDelegate).delete(DEVICE_ID, CALENDAR_KEY);
  }

  @Test
  public void deleteAll_deletesAllCalendars() {
    Map<String, Range<Instant>> calendarKeyToTimeRange = new HashMap<>();
    calendarKeyToTimeRange.put(CALENDAR_KEY, TIME_RANGE);
    CalendarManager manager = createCalendarManager(calendarKeyToTimeRange);

    manager.deleteAll(DEVICE_ID);

    verify(mockCalendarContentDelegate).deleteAll(DEVICE_ID);
  }

  private CalendarManager createCalendarManager(
      Map<String, Range<Instant>> calendarKeyToTimeRange) {
    return new CalendarManager(
        mockLoggerFactory,
        mockCalendarContentDelegate,
        mockEventManagerFactory,
        mockCalendarStore,
        calendarKeyToTimeRange);
  }
}
