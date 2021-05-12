package com.google.android.connecteddevice.calendarsync.android;

import static com.google.android.connecteddevice.calendarsync.android.BaseContentDelegate.ACCOUNT_NAME;
import static com.google.android.connecteddevice.calendarsync.android.BaseContentDelegate.addSyncAdapterParameters;
import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.REPLICA;
import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.SOURCE;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.connecteddevice.calendarsync.Calendar;
import com.google.android.connecteddevice.calendarsync.Color;
import com.google.android.connecteddevice.calendarsync.TimeZone;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall.MethodType;
import com.google.android.connecteddevice.calendarsync.common.CommonLogger;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CalendarContentDelegateTest {
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final String DEVICE_ID = "a device id";
  private static final long ID = 987654321L;
  private static final String KEY = Long.toString(ID);
  private static final String NAME = "the name";
  private static final String ACCOUNT = "the owner account";
  private static final int COLOR_RGB = 0xBEEFD00D;

  private static final ImmutableMap<String, Object> TEST_COLUMN_VALUES =
      ImmutableMap.<String, Object>builder()
          .put(Calendars._ID, ID)
          .put(Calendars._SYNC_ID, KEY)
          .put(Calendars.CALENDAR_COLOR, COLOR_RGB)
          .put(Calendars.CALENDAR_DISPLAY_NAME, NAME)
          .put(Calendars.OWNER_ACCOUNT, ACCOUNT)
          .put(Calendars.CALENDAR_TIME_ZONE, ZONE_ID.getId())
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

  @Test
  public void read_source_allFields() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(SOURCE);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Content<Calendar> content = delegate.read(DEVICE_ID, Long.toString(ID));

    assertThat(content.getId()).isEqualTo(ID);
    assertThat(content.getMessage().getKey()).isEqualTo(Long.toString(ID));
    assertThat(content.getMessage().getAccountName()).isEqualTo(ACCOUNT);
    assertThat(content.getMessage().getColor().getArgb()).isEqualTo(COLOR_RGB);
    assertThat(content.getMessage().getTimeZone().getName()).isEqualTo(ZONE_ID.getId());
  }

  @Test
  public void read_replica_allFields() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(ContentOwnership.REPLICA);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    Content<Calendar> content = delegate.read(DEVICE_ID, Long.toString(ID));

    assertThat(content.getId()).isEqualTo(ID);
    assertThat(content.getMessage().getKey()).isEqualTo(Long.toString(ID));
    assertThat(content.getMessage().getAccountName()).isEqualTo(ACCOUNT);
    assertThat(content.getMessage().getColor().getArgb()).isEqualTo(COLOR_RGB);
    assertThat(content.getMessage().getTimeZone().getName()).isEqualTo(ZONE_ID.getId());
  }

  @Test
  public void readAll_statusAndType() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(SOURCE);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);
    testCalendarProvider.addRow(TEST_COLUMN_VALUES);

    ImmutableList<Content<Calendar>> contents = delegate.readAll(DEVICE_ID);

    assertThat(contents).hasSize(2);
  }

  @Test
  public void insert_replica() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);

    Calendar calendar =
        Calendar.newBuilder()
            .setKey(KEY)
            .setAccountName(ACCOUNT)
            .setColor(Color.newBuilder().setArgb(COLOR_RGB))
            .setTimeZone(TimeZone.newBuilder().setName(ZONE_ID.getId()))
            .setTitle(NAME)
            .build();

    delegate.insert(DEVICE_ID, calendar);

    Uri expectedUri = addSyncAdapterParameters(Calendars.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.INSERT, expectedUri);
    expected.setValues(createDefaultExpectedContent());
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void insert_source() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(SOURCE);

    Calendar calendar =
        Calendar.newBuilder()
            .setKey(KEY)
            .setAccountName(ACCOUNT)
            .setColor(Color.newBuilder().setArgb(COLOR_RGB))
            .setTimeZone(TimeZone.newBuilder().setName(ZONE_ID.getId()))
            .setTitle(NAME)
            .build();

    delegate.insert(DEVICE_ID, calendar);

    Uri expectedUri = Calendars.CONTENT_URI;
    ProviderCall expected = new ProviderCall(MethodType.INSERT, expectedUri);
    ContentValues values = expected.getValues();
    values.put(Calendars._ID, KEY);
    values.put(Calendars.CALENDAR_DISPLAY_NAME, NAME);
    values.put(Calendars.CALENDAR_COLOR, COLOR_RGB);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void delete() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);

    delegate.delete(DEVICE_ID, KEY);

    Uri expectedUri = addSyncAdapterParameters(Calendars.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    String[] expectedArgs = {DEVICE_ID, KEY, ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL};
    String expectedSelect =
        "cal_sync1 = ? AND _sync_id = ? AND account_name = ? AND account_type = ?";
    expected.setSelection(expectedSelect, expectedArgs);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void deleteAll() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);

    delegate.deleteAll(DEVICE_ID);

    Uri expectedUri = addSyncAdapterParameters(Calendars.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    expected.setSelection("cal_sync1 = ?", new String[] {DEVICE_ID});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void clean() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);

    delegate.clean();

    Uri expectedUri = addSyncAdapterParameters(Calendars.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, expectedUri);
    String[] expectedArgs = {ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL};
    String expectedSelect = "account_name = ? AND account_type = ?";
    expected.setSelection(expectedSelect, expectedArgs);
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void update() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);
    Calendar calendar =
        Calendar.newBuilder()
            .setKey(KEY)
            .setAccountName(ACCOUNT)
            .setColor(Color.newBuilder().setArgb(COLOR_RGB))
            .setTimeZone(TimeZone.newBuilder().setName(ZONE_ID.getId()))
            .setTitle(NAME)
            .build();

    delegate.update(DEVICE_ID, KEY, calendar);

    Uri expectedUri = addSyncAdapterParameters(Calendars.CONTENT_URI);
    ProviderCall expected = new ProviderCall(MethodType.UPDATE, expectedUri);
    String[] expectedArgs = {DEVICE_ID, KEY, ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL};
    String expectedSelect =
        "cal_sync1 = ? AND _sync_id = ? AND account_name = ? AND account_type = ?";
    expected.setSelection(expectedSelect, expectedArgs);
    expected.setValues(createDefaultExpectedContent());
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void find_source() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(SOURCE);

    delegate.find(DEVICE_ID, KEY);

    ProviderCall expected = new ProviderCall(MethodType.QUERY, Calendars.CONTENT_URI);
    expected.setSelection("id = ?", new String[] {KEY});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  @Test
  public void find_replica() {
    CalendarContentDelegate delegate = createCalendarContentDelegate(REPLICA);

    delegate.find(DEVICE_ID, KEY);

    ProviderCall expected =
        new ProviderCall(MethodType.QUERY, addSyncAdapterParameters(Calendars.CONTENT_URI));
    expected.setSelection(
        "cal_sync1 = ? AND _sync_id = ? AND account_name = ? AND account_type = ?",
        new String[] {DEVICE_ID, KEY, ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL});
    ProviderCall call = getOnlyElement(testCalendarProvider.getCalls());
    call.assertSameArgs(expected);
  }

  private CalendarContentDelegate createCalendarContentDelegate(ContentOwnership ownership) {
    Context context = ApplicationProvider.getApplicationContext();
    return new CalendarContentDelegate(
        new CommonLogger.NoOpLoggerFactory(), context.getContentResolver(), ownership);
  }

  private ContentValues createDefaultExpectedContent() {
    ContentValues values = new ContentValues();
    values.put(Calendars._SYNC_ID, KEY);
    values.put(Calendars.OWNER_ACCOUNT, ACCOUNT);
    values.put(Calendars.CALENDAR_TIME_ZONE, ZONE_ID.getId());
    values.put(Calendars.CAL_SYNC1, DEVICE_ID);
    values.put(Calendars.SYNC_EVENTS, 1);
    values.put(Calendars.VISIBLE, 1);
    values.put(Calendars.ACCOUNT_NAME, BaseContentDelegate.ACCOUNT_NAME);
    values.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
    values.put(Calendars.CALENDAR_DISPLAY_NAME, NAME);
    values.put(Calendars.CALENDAR_COLOR, COLOR_RGB);
    values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_EDITOR);
    return values;
  }
}
