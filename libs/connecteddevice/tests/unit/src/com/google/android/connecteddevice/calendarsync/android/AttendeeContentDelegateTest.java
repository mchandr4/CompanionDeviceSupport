package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.connecteddevice.calendarsync.Attendee;
import com.google.android.connecteddevice.calendarsync.Attendee.Status;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall.MethodType;
import com.google.android.connecteddevice.calendarsync.common.CommonLogger;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.common.collect.ImmutableMap;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AttendeeContentDelegateTest {
  private static final long EVENT_ID = 123454657879L;
  private static final long ID = 987654321L;
  private static final String KEY = "a key";
  private static final String NAME = "the name";
  private static final String EMAIL = "the email";

  private AttendeeContentDelegate delegate;
  private TestCalendarProvider testCalendarProvider;

  private static final ImmutableMap<String, Object> TEST_COLUMN_VALUES =
      ImmutableMap.<String, Object>builder()
          .put(Attendees._ID, ID)
          .put(Attendees.ATTENDEE_EMAIL, EMAIL)
          .put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED)
          .put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
          .put(Attendees.ATTENDEE_NAME, NAME)
          .build();

  @Before
  public void setUp() {
    ProviderInfo info = new ProviderInfo();
    info.authority = CalendarContract.AUTHORITY;
    testCalendarProvider =
        Robolectric.buildContentProvider(TestCalendarProvider.class).create(info).get();
    testCalendarProvider.setColumns(TEST_COLUMN_VALUES.keySet().asList());

    Context context = ApplicationProvider.getApplicationContext();
    delegate =
        new AttendeeContentDelegate(
            new CommonLogger.NoOpLoggerFactory(), context.getContentResolver());
  }

  @Test
  public void read_allFields() {
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_ACCEPTED);

    Content<Attendee> content = delegate.read(EVENT_ID, KEY);

    assertThat(content.getId()).isEqualTo(ID);
    assertThat(content.getMessage().getEmail()).isEqualTo(EMAIL);
    assertThat(content.getMessage().getName()).isEqualTo(NAME);
    assertThat(content.getMessage().getType()).isEqualTo(Attendee.Type.REQUIRED);
    assertThat(content.getMessage().getStatus()).isEqualTo(Status.ACCEPTED);
  }

  @Test
  public void readAll_statusAndType() {
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_NONE);
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_ACCEPTED);
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_DECLINED);
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_INVITED);
    addTestRowWithStatus(Attendees.ATTENDEE_STATUS_TENTATIVE);

    addTestRowWithType(Attendees.TYPE_NONE);
    addTestRowWithType(Attendees.TYPE_OPTIONAL);
    addTestRowWithType(Attendees.TYPE_REQUIRED);
    addTestRowWithType(Attendees.TYPE_RESOURCE);

    Iterator<Content<Attendee>> contents = delegate.readAll(EVENT_ID).iterator();

    assertThat(next(contents).getStatus()).isEqualTo(Status.NONE_STATUS);
    assertThat(next(contents).getStatus()).isEqualTo(Status.ACCEPTED);
    assertThat(next(contents).getStatus()).isEqualTo(Status.DECLINED);
    assertThat(next(contents).getStatus()).isEqualTo(Status.INVITED);
    assertThat(next(contents).getStatus()).isEqualTo(Status.TENTATIVE);

    assertThat(next(contents).getType()).isEqualTo(Attendee.Type.NONE_TYPE);
    assertThat(next(contents).getType()).isEqualTo(Attendee.Type.OPTIONAL);
    assertThat(next(contents).getType()).isEqualTo(Attendee.Type.REQUIRED);
    assertThat(next(contents).getType()).isEqualTo(Attendee.Type.RESOURCE);
  }

  @Test
  public void insert() {
    Attendee attendee =
        Attendee.newBuilder()
            .setEmail(EMAIL)
            .setName(NAME)
            .setStatus(Status.ACCEPTED)
            .setType(Attendee.Type.REQUIRED)
            .build();

    delegate.insert(EVENT_ID, attendee);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    ProviderCall call = calls.get(0);
    ProviderCall expected = new ProviderCall(MethodType.INSERT, Attendees.CONTENT_URI);
    ContentValues values = expected.getValues();
    values.put(Attendees.EVENT_ID, EVENT_ID);
    values.put(Attendees.ATTENDEE_EMAIL, EMAIL);
    values.put(Attendees.ATTENDEE_NAME, NAME);
    values.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED);
    values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED);

    call.assertEquals(expected);
  }

  @Test
  public void delete() {
    delegate.delete(EVENT_ID, KEY);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    ProviderCall call = calls.get(0);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, Attendees.CONTENT_URI);
    expected.setSelection(
        "attendeeEmail = ? AND event_id = ?", new String[] {KEY, Long.toString(EVENT_ID)});
    call.assertEquals(expected);
  }

  @Test
  public void deleteAll() {
    delegate.deleteAll(EVENT_ID);

    List<ProviderCall> calls = testCalendarProvider.getCalls();
    assertThat(calls).hasSize(1);
    ProviderCall call = calls.get(0);
    ProviderCall expected = new ProviderCall(MethodType.DELETE, Attendees.CONTENT_URI);
    expected.setSelection("event_id = ?", new String[] {Long.toString(EVENT_ID)});
    call.assertEquals(expected);
  }

  private void addTestRowWithType(int type) {
    testCalendarProvider.addRowWithReplacement(TEST_COLUMN_VALUES, Attendees.ATTENDEE_TYPE, type);
  }

  private void addTestRowWithStatus(int status) {
    testCalendarProvider.addRowWithReplacement(
        TEST_COLUMN_VALUES, Attendees.ATTENDEE_STATUS, status);
  }

  private Attendee next(Iterator<Content<Attendee>> contents) {
    return contents.next().getMessage();
  }
}
