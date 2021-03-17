package com.google.android.connecteddevice.calendarsync.android;

import static com.google.android.connecteddevice.calendarsync.android.BaseContentDelegate.addSyncAdapterParameters;
import static com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.NULL_VALUE;
import static com.google.android.connecteddevice.calendarsync.common.TimeProtoUtil.toTimestamp;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.connecteddevice.calendarsync.Color;
import com.google.android.connecteddevice.calendarsync.Event;
import com.google.android.connecteddevice.calendarsync.Event.Status;
import com.google.android.connecteddevice.calendarsync.TimeZone;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall.MethodType;
import com.google.android.connecteddevice.calendarsync.common.CommonLogger;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class EventContentDelegateTest {

  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);
  private static final Instant BEGIN_TIME = ZONED_DATE_TIME.toInstant();
  private static final Instant END_TIME = ZONED_DATE_TIME.plusDays(2).toInstant();
  private static final long ID = 987654321L;
  private static final long CALENDAR_ID = 123454657879L;
  private static final String KEY = EventContentDelegate.createSingleKey(ID);
  private static final String TITLE = "the title";
  private static final String DESCRIPTION = "the description";
  private static final String ORGANIZER = "the organizer";
  private static final String LOCATION = "the location";
  private static final boolean ALL_DAY = false;
  private static final int COLOR_RGB = 0xBEEFD00D;

  private static final ImmutableMap<String, Object> TEST_COLUMN_VALUES =
      ImmutableMap.<String, Object>builder()
          .put(Instances.EVENT_ID, ID)
          .put(Events._SYNC_ID, KEY)
          .put(Instances.STATUS, Instances.STATUS_CONFIRMED)
          .put(Instances.TITLE, TITLE)
          .put(Instances.DESCRIPTION, DESCRIPTION)
          .put(Instances.EVENT_LOCATION, LOCATION)
          .put(Instances.BEGIN, BEGIN_TIME.toEpochMilli())
          .put(Instances.END, END_TIME.toEpochMilli())
          .put(Instances.EVENT_TIMEZONE, ZONE_ID.getId())
          .put(Instances.EVENT_END_TIMEZONE, ZONE_ID.getId())
          .put(Instances.ALL_DAY, ALL_DAY ? 1 : 0)
          .put(Instances.EVENT_COLOR, COLOR_RGB)
          .put(Instances.ORGANIZER, ORGANIZER)
          .put(Instances.RRULE, NULL_VALUE)
          .put(Instances.RDATE, NULL_VALUE)
          .put(Instances.ORIGINAL_ID, NULL_VALUE)
          .put(Instances.ORIGINAL_INSTANCE_TIME, NULL_VALUE)
          .build();

  private TestCalendarProvider testCalendarProvider;

  @Before
  public void setUp() {
    ProviderInfo info = new ProviderInfo();
    info.authority = CalendarContract.AUTHORITY;
    testCalendarProvider =
        Robolectric.buildContentProvider(TestCalendarProvider.class).create(info).get();
    testCalendarProvider.setColumns(TEST_COLUMN_VALUES.keySet().asList());
  }

  private EventContentDelegate createEventContentDelegate(ContentOwnership ownership) {
    Context context = ApplicationProvider.getApplicationContext();
    return new EventContentDelegate(
        new CommonLogger.NoOpLoggerFactory(),
        context.getContentResolver(),
        ownership,
        Range.openClosed(BEGIN_TIME, END_TIME));
  }

  @Test
  public void read_source_allFields() {
    // In this case using org.robolectric.ParameterizedRobolectricTestRunner is more complex.
    testReadAllFields(ContentOwnership.SOURCE);
  }

  @Test
  public void read_replica_allFields() {
    testReadAllFields(ContentOwnership.REPLICA);
  }

  private void testReadAllFields(ContentOwnership ownership) {
    EventContentDelegate delegate = createEventContentDelegate(ownership);

    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Content<Event> content = delegate.read(CALENDAR_ID, KEY);

    assertThat(content.getId()).isEqualTo(ID);
    assertThat(content.getMessage().getKey()).isEqualTo(KEY);
    assertThat(content.getMessage().getStatus()).isEqualTo(Status.CONFIRMED);
    assertThat(content.getMessage().getTitle()).isEqualTo(TITLE);
    assertThat(content.getMessage().getDescription()).isEqualTo(DESCRIPTION);
    assertThat(content.getMessage().getBeginTime().getSeconds())
        .isEqualTo(BEGIN_TIME.getEpochSecond());
    assertThat(content.getMessage().getEndTime().getSeconds()).isEqualTo(END_TIME.getEpochSecond());
    assertThat(content.getMessage().getTimeZone().getName()).isEqualTo(ZONE_ID.getId());
    assertThat(content.getMessage().getEndTimeZone().getName()).isEqualTo(ZONE_ID.getId());
    assertThat(content.getMessage().getIsAllDay()).isEqualTo(ALL_DAY);
    assertThat(content.getMessage().getColor().getArgb()).isEqualTo(COLOR_RGB);
    assertThat(content.getMessage().getOrganizer()).isEqualTo(ORGANIZER);
  }

  @Test
  public void readAll_statusAndType() {
    // Using only a single ownership gives coverage of the required paths.
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);

    addTestRowWithStatus(-1); // Invalid value does not add a status at all.
    addTestRowWithStatus(Events.STATUS_CANCELED);
    addTestRowWithStatus(Events.STATUS_CONFIRMED);
    addTestRowWithStatus(Events.STATUS_TENTATIVE);

    Iterator<Content<Event>> contents = delegate.readAll(CALENDAR_ID).iterator();

    assertThat(next(contents).getStatus()).isEqualTo(Status.UNSPECIFIED_STATUS);
    assertThat(next(contents).getStatus()).isEqualTo(Status.CANCELED);
    assertThat(next(contents).getStatus()).isEqualTo(Status.CONFIRMED);
    assertThat(next(contents).getStatus()).isEqualTo(Status.TENTATIVE);
  }

  @Test
  public void read_recurringSourceEvent() {
    testCalendarProvider.addRowWithReplacement(TEST_COLUMN_VALUES, Events.RRULE, "a rrule");
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);

    Content<Event> content =
        delegate.read(CALENDAR_ID, EventContentDelegate.createRecurringKey(ID, BEGIN_TIME));

    assertThat(content).isNotNull();
  }

  @Test
  public void read_exceptionSourceEvent() {
    HashMap<String, Object> exceptionEventRow = new HashMap<>(TEST_COLUMN_VALUES);
    exceptionEventRow.put(Events.ORIGINAL_ID, 1);
    exceptionEventRow.put(Events.ORIGINAL_INSTANCE_TIME, BEGIN_TIME.toEpochMilli());
    testCalendarProvider.addRow(exceptionEventRow);
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);

    Content<Event> content =
        delegate.read(CALENDAR_ID, EventContentDelegate.createExceptionKey(1, BEGIN_TIME));

    assertThat(content).isNotNull();
  }

  @Test
  public void insert_source_allFields() {
    // In this case using org.robolectric.ParameterizedRobolectricTestRunner is more complex.
    testInsertAllFields(ContentOwnership.SOURCE);
  }

  @Test
  public void insert_replica_allFields() {
    testInsertAllFields(ContentOwnership.REPLICA);
  }

  private void testInsertAllFields(ContentOwnership ownership) {
    EventContentDelegate delegate = createEventContentDelegate(ownership);

    Event event =
        Event.newBuilder()
            .setKey(KEY)
            .setStatus(Status.CONFIRMED)
            .setTitle(TITLE)
            .setDescription(DESCRIPTION)
            .setLocation(LOCATION)
            .setBeginTime(toTimestamp(BEGIN_TIME))
            .setEndTime(toTimestamp(END_TIME))
            .setTimeZone(TimeZone.newBuilder().setName(ZONE_ID.getId()).build())
            .setEndTimeZone(TimeZone.newBuilder().setName(ZONE_ID.getId()).build())
            .setIsAllDay(ALL_DAY)
            .setColor(Color.newBuilder().setArgb(COLOR_RGB).build())
            .setOrganizer(ORGANIZER)
            .build();

    delegate.insert(CALENDAR_ID, event);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    Uri expectedUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.INSERT, expectedUri);
    ContentValues values = expected.getValues();
    values.put(Attendees.CALENDAR_ID, CALENDAR_ID);
    values.put(Events.STATUS, Events.STATUS_CONFIRMED);
    values.put(Events.TITLE, TITLE);
    values.put(Events.DESCRIPTION, DESCRIPTION);
    values.put(Events.EVENT_LOCATION, LOCATION);
    values.put(Events.DTSTART, BEGIN_TIME.toEpochMilli());
    values.put(Events.DTEND, END_TIME.toEpochMilli());
    values.put(Events.EVENT_TIMEZONE, ZONE_ID.getId());
    values.put(Events.EVENT_END_TIMEZONE, ZONE_ID.getId());
    values.put(Events.ALL_DAY, ALL_DAY ? 1 : 0);
    values.put(Events.EVENT_COLOR, COLOR_RGB);
    values.put(Events.ORGANIZER, ORGANIZER);

    if (ownership == ContentOwnership.REPLICA) {
      values.put(Events._SYNC_ID, KEY);
    }

    Iterables.getOnlyElement(testCalendarProvider.getCalls()).assertEquals(expected);
  }

  @Test
  public void delete_source() {
    testDelete(ContentOwnership.SOURCE);
  }

  @Test
  public void delete_replica() {
    testDelete(ContentOwnership.REPLICA);
  }

  private void testDelete(ContentOwnership ownership) {
    EventContentDelegate delegate = createEventContentDelegate(ownership);

    delegate.delete(CALENDAR_ID, KEY);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    Uri expectedUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    if (ownership == ContentOwnership.SOURCE) {
      expected.setSelection(
          "calendar_id = ? AND event_id = ?",
          new String[] {Long.toString(CALENDAR_ID), Long.toString(ID)});
    } else {
      expected.setSelection(
          "calendar_id = ? AND _sync_id = ?", new String[] {Long.toString(CALENDAR_ID), KEY});
    }
    Iterables.getOnlyElement(testCalendarProvider.getCalls()).assertEquals(expected);
  }

  @Test
  public void deleteAll() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.REPLICA);

    delegate.deleteAll(CALENDAR_ID);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    Uri expectedUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    expected.setSelection("calendar_id = ?", new String[] {Long.toString(CALENDAR_ID)});
    Iterables.getOnlyElement(testCalendarProvider.getCalls()).assertEquals(expected);
  }

  private Event next(Iterator<Content<Event>> contents) {
    return contents.next().getMessage();
  }

  private void addTestRowWithStatus(int status) {
    testCalendarProvider.addRowWithReplacement(TEST_COLUMN_VALUES, Events.STATUS, status);
  }
}
