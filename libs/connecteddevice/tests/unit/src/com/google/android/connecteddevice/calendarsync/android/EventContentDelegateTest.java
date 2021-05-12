package com.google.android.connecteddevice.calendarsync.android;

import static com.google.android.connecteddevice.calendarsync.android.BaseContentDelegate.addSyncAdapterParameters;
import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.REPLICA;
import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.SOURCE;
import static com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.NULL_VALUE;
import static com.google.android.connecteddevice.calendarsync.common.TimeProtoUtil.toTimestamp;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.ContentUris;
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
import com.google.common.collect.Range;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
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
          .put(Instances.ALL_DAY, 0)
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
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Content<Event> content = delegate.read(CALENDAR_ID, KEY);

    assertAllReadFields(content);
  }

  @Test
  public void read_replica_allFields() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.REPLICA);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Content<Event> content = delegate.read(CALENDAR_ID, KEY);

    assertAllReadFields(content);
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
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);
    Event event = createTestEvent();

    Object insertedId = delegate.insert(CALENDAR_ID, event);

    assertThat(insertedId).isEqualTo(1); // Hard coded result from TestConProvider.
    ProviderCall expected = new ProviderCall(MethodType.INSERT, Events.CONTENT_URI);
    expected.setValues(createExpectedContentValues());
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void insert_replica_allFields() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.REPLICA);
    Event event = createTestEvent();

    Object insertedId = delegate.insert(CALENDAR_ID, event);

    assertThat(insertedId).isEqualTo(1); // Hard coded result from TestConProvider.
    ProviderCall expected =
        new ProviderCall(MethodType.INSERT, addSyncAdapterParameters(Events.CONTENT_URI));
    ContentValues expectedContentValues = createExpectedContentValues();
    expectedContentValues.put(Events._SYNC_ID, KEY);
    expected.setValues(expectedContentValues);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void delete_source() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.SOURCE);

    delegate.delete(CALENDAR_ID, KEY);

    ProviderCall expected = new ProviderCall(MethodType.DELETE, Events.CONTENT_URI);
    expected.setSelection(
        "calendar_id = ? AND event_id = ?",
        new String[] {Long.toString(CALENDAR_ID), Long.toString(ID)});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void delete_replica() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.REPLICA);

    delegate.delete(CALENDAR_ID, KEY);

    ProviderCall expected =
        new ProviderCall(MethodType.DELETE, addSyncAdapterParameters(Events.CONTENT_URI));
    expected.setSelection(
        "calendar_id = ? AND _sync_id = ?", new String[] {Long.toString(CALENDAR_ID), KEY});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void deleteAll() {
    EventContentDelegate delegate = createEventContentDelegate(ContentOwnership.REPLICA);

    delegate.deleteAll(CALENDAR_ID);

    Uri expectedUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    expected.setSelection("calendar_id = ?", new String[] {Long.toString(CALENDAR_ID)});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void update_source() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    Event event = createTestEvent();

    delegate.update(CALENDAR_ID, KEY, event);

    ProviderCall expected = new ProviderCall(MethodType.UPDATE, Events.CONTENT_URI);
    expected.setSelection(
        "calendar_id = ? AND event_id = ?",
        new String[] {Long.toString(CALENDAR_ID), Long.toString(ID)});
    expected.setValues(createExpectedContentValues());
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void update_replica() {
    EventContentDelegate delegate = createEventContentDelegate(REPLICA);
    Event event = createTestEvent();

    delegate.update(CALENDAR_ID, KEY, event);

    ProviderCall expected =
        new ProviderCall(MethodType.UPDATE, addSyncAdapterParameters(Events.CONTENT_URI));
    expected.setSelection(
        "calendar_id = ? AND _sync_id = ?", new String[] {Long.toString(CALENDAR_ID), KEY});
    ContentValues expectedContentValues = createExpectedContentValues();
    expectedContentValues.put(Events._SYNC_ID, KEY);
    expected.setValues(expectedContentValues);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void find_source() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    testCalendarProvider.addRow(ImmutableMap.of(Instances.EVENT_ID, 123));

    Object id = delegate.find(CALENDAR_ID, KEY);

    assertThat(id).isEqualTo(123);
    Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
    ContentUris.appendId(builder, BEGIN_TIME.toEpochMilli());
    ContentUris.appendId(builder, END_TIME.toEpochMilli());
    Uri expectedUri = builder.build();
    ProviderCall expected = new ProviderCall(MethodType.QUERY, expectedUri);
    expected.setSelection(
        "calendar_id = ? AND event_id = ?",
        new String[] {Long.toString(CALENDAR_ID), Long.toString(ID)});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void find_replica() {
    EventContentDelegate delegate = createEventContentDelegate(REPLICA);
    testCalendarProvider.addRow(ImmutableMap.of(Instances.EVENT_ID, 123));

    Object id = delegate.find(CALENDAR_ID, KEY);

    assertThat(id).isEqualTo(123);
    Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
    ContentUris.appendId(builder, BEGIN_TIME.toEpochMilli());
    ContentUris.appendId(builder, END_TIME.toEpochMilli());
    Uri expectedUri = addSyncAdapterParameters(builder.build());
    ProviderCall expected = new ProviderCall(MethodType.QUERY, expectedUri);
    expected.setSelection(
        "calendar_id = ? AND _sync_id = ?", new String[] {Long.toString(CALENDAR_ID), KEY});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void updateRecurringEvent_source_insertsException() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);
    Event recurringEvent = createTestEvent().toBuilder().setKey(recurringKey).build();

    delegate.update(CALENDAR_ID, recurringKey, recurringEvent);

    Uri.Builder expectedUriBuilder = Events.CONTENT_EXCEPTION_URI.buildUpon();
    ContentUris.appendId(expectedUriBuilder, ID);
    Uri expectedUri = expectedUriBuilder.build();

    ProviderCall expected = new ProviderCall(MethodType.INSERT, expectedUri);
    ContentValues expectedContentValues = new ContentValues();
    expectedContentValues.put(Events.ORIGINAL_INSTANCE_TIME, BEGIN_TIME.toEpochMilli());
    expectedContentValues.put(Events.STATUS, Events.STATUS_CONFIRMED);
    expectedContentValues.put(Events.TITLE, TITLE);
    expectedContentValues.put(Events.DESCRIPTION, DESCRIPTION);
    expectedContentValues.put(Events.EVENT_LOCATION, LOCATION);
    expectedContentValues.put(Events.DTSTART, BEGIN_TIME.toEpochMilli());
    expectedContentValues.put(Events.EVENT_TIMEZONE, ZONE_ID.getId());
    expectedContentValues.put(Events.EVENT_END_TIMEZONE, ZONE_ID.getId());
    expectedContentValues.put(Events.ALL_DAY, 0);
    expectedContentValues.put(Events.EVENT_COLOR, COLOR_RGB);
    expectedContentValues.put(Events.ORGANIZER, ORGANIZER);
    expected.setValues(expectedContentValues);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void updateRecurringEvent_replica_normalUpdate() {
    EventContentDelegate delegate = createEventContentDelegate(REPLICA);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);
    Event recurringEvent = createTestEvent().toBuilder().setKey(recurringKey).build();

    delegate.update(CALENDAR_ID, recurringKey, recurringEvent);

    Uri expectedContentUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.UPDATE, expectedContentUri);
    expected.setSelection(
        "calendar_id = ? AND sync_id = ?", new String[] {Long.toString(CALENDAR_ID), recurringKey});
    ContentValues expectedContentValues = createExpectedContentValues();
    expectedContentValues.put(Events._SYNC_ID, recurringKey);
    expected.setValues(expectedContentValues);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void deleteRecurringEvent_source_insertsException() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);

    boolean deleted = delegate.delete(CALENDAR_ID, recurringKey);

    assertThat(deleted).isTrue();
    Uri.Builder expectedUriBuilder = Events.CONTENT_EXCEPTION_URI.buildUpon();
    ContentUris.appendId(expectedUriBuilder, ID);
    Uri expectedUri = expectedUriBuilder.build();

    ProviderCall expected = new ProviderCall(MethodType.INSERT, expectedUri);

    ContentValues expectedContentValues = new ContentValues();
    expectedContentValues.put(Events.STATUS, Events.STATUS_CANCELED);
    expectedContentValues.put(Events.ORIGINAL_INSTANCE_TIME, BEGIN_TIME.toEpochMilli());
    expected.setValues(expectedContentValues);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void deleteRecurringEvent_replica_normalDelete() {
    EventContentDelegate delegate = createEventContentDelegate(REPLICA);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);

    boolean deleted = delegate.delete(CALENDAR_ID, recurringKey);

    assertThat(deleted).isTrue();
    Uri expectedContentUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedContentUri);
    expected.setSelection(
        "calendar_id = ? AND _sync_id = ?",
        new String[] {Long.toString(CALENDAR_ID), recurringKey});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void insertExceptionEvent_replica_deletesRecurringInstance() {
    EventContentDelegate delegate = createEventContentDelegate(REPLICA);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);
    String exceptionKey = EventContentDelegate.createExceptionKey(ID, BEGIN_TIME);
    Event exceptionEvent = createTestEvent().toBuilder().setKey(exceptionKey).build();

    delegate.insert(CALENDAR_ID, exceptionEvent);

    ProviderCall deleteCall = testCalendarProvider.getCalls().get(0);
    Uri expectedDeleteUri = addSyncAdapterParameters(Events.CONTENT_URI);
    ProviderCall expectedDeleteCall = new ProviderCall(MethodType.DELETE, expectedDeleteUri);
    expectedDeleteCall.setSelection(
        "calendar_id = ? AND _sync_id = ?",
        new String[] {Long.toString(CALENDAR_ID), recurringKey});
    deleteCall.assertSameArgs(expectedDeleteCall);
    ProviderCall insertCall = testCalendarProvider.getCalls().get(1);
    assertThat(insertCall.getType()).isEqualTo(MethodType.INSERT);
  }

  @Test
  public void insertExceptionEvent_source_throws() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    String exceptionKey = EventContentDelegate.createExceptionKey(ID, BEGIN_TIME);
    Event exceptionEvent = createTestEvent().toBuilder().setKey(exceptionKey).build();

    assertThrows(IllegalStateException.class, () -> delegate.insert(CALENDAR_ID, exceptionEvent));
  }

  @Test
  public void findRecurringEvent_source_createsDuplicateException() {
    EventContentDelegate delegate = createEventContentDelegate(SOURCE);
    String recurringKey = EventContentDelegate.createRecurringKey(ID, BEGIN_TIME);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Object id = delegate.find(CALENDAR_ID, recurringKey);

    // The returned id is the dummy value returned by TestCalendarProvider.insert().
    assertThat(id).isEqualTo(1);
    Uri.Builder expectedInsertUriBuilder = Events.CONTENT_EXCEPTION_URI.buildUpon();
    ContentUris.appendId(expectedInsertUriBuilder, ID);
    Uri expectedInsertUri = expectedInsertUriBuilder.build();
    ProviderCall readCall = testCalendarProvider.getCalls().get(0);
    assertThat(readCall.getType()).isEqualTo(MethodType.QUERY);
    ProviderCall insertCall = testCalendarProvider.getCalls().get(1);
    assertThat(insertCall.getType()).isEqualTo(MethodType.INSERT);
    assertThat(insertCall.getUri()).isEqualTo(expectedInsertUri);
  }

  private Event createTestEvent() {
    return Event.newBuilder()
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
  }

  private ContentValues createExpectedContentValues() {
    ContentValues values = new ContentValues();
    values.put(Attendees.CALENDAR_ID, CALENDAR_ID);
    values.put(Events.STATUS, Events.STATUS_CONFIRMED);
    values.put(Events.TITLE, TITLE);
    values.put(Events.DESCRIPTION, DESCRIPTION);
    values.put(Events.EVENT_LOCATION, LOCATION);
    values.put(Events.DTSTART, BEGIN_TIME.toEpochMilli());
    values.put(Events.DTEND, END_TIME.toEpochMilli());
    values.put(Events.EVENT_TIMEZONE, ZONE_ID.getId());
    values.put(Events.EVENT_END_TIMEZONE, ZONE_ID.getId());
    values.put(Events.ALL_DAY, 0);
    values.put(Events.EVENT_COLOR, COLOR_RGB);
    values.put(Events.ORGANIZER, ORGANIZER);
    return values;
  }

  private Event next(Iterator<Content<Event>> contents) {
    return contents.next().getMessage();
  }

  private void addTestRowWithStatus(int status) {
    testCalendarProvider.addRowWithReplacement(TEST_COLUMN_VALUES, Events.STATUS, status);
  }

  private void assertAllReadFields(Content<Event> content) {
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
}
