package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.connecteddevice.calendarsync.Attendee;
import com.google.android.connecteddevice.calendarsync.Event;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.EventContentDelegateFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class EventManagerTest {
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);
  private static final Instant FROM_TIME = ZONED_DATE_TIME.toInstant();
  private static final Instant TO_TIME = ZONED_DATE_TIME.plusDays(2).toInstant();
  private static final Range<Instant> TIME_RANGE = Range.openClosed(FROM_TIME, TO_TIME);
  private static final Object CALENDAR_KEY = "a calendar id";
  private static final String EVENT_KEY = "an event id";

  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private PlatformContentDelegate<Event> mockEventContent;
  @Mock private EventContentDelegateFactory mockEventContentDelegateFactory;
  @Mock private AttendeeManager mockAttendeeManager;

  private EventManager eventManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));

    when(mockEventContent.read(CALENDAR_KEY, EVENT_KEY))
        .thenReturn(new Content<>(Event.newBuilder().setKey(EVENT_KEY).build(), 1L));
    when(mockEventContent.readAll(CALENDAR_KEY))
        .thenReturn(
            ImmutableList.of(new Content<>(Event.newBuilder().setKey(EVENT_KEY).build(), 1L)));
    when(mockEventContentDelegateFactory.create(any())).thenReturn(mockEventContent);

    when(mockAttendeeManager.readAll(any()))
        .thenReturn(
            ImmutableList.of(Attendee.newBuilder().setEmail("Some attendee email").build()));

    eventManager =
        new EventManager(
            mockLoggerFactory, mockEventContentDelegateFactory, mockAttendeeManager, TIME_RANGE);
  }

  @Test
  public void read_hasChildren() {
    Event event = eventManager.read(CALENDAR_KEY, EVENT_KEY);

    assertThat(event.getAttendeesCount()).isEqualTo(1);
  }

  @Test
  public void readAll_returnsEvents() {
    ImmutableList<Event> events = eventManager.readAll(CALENDAR_KEY);

    assertThat(events).hasSize(1);
  }
}
