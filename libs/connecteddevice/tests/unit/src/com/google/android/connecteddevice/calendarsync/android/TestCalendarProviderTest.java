package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall.MethodType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TestCalendarProviderTest {

  @Test
  public void queryReturnsCursorWithData() {
    TestCalendarProvider testCalendarProvider = new TestCalendarProvider();
    testCalendarProvider.setColumns(ImmutableList.of("first", "second"));
    testCalendarProvider.addRow(ImmutableMap.of("first", 1, "second", "num2"));

    Cursor cursor = testCalendarProvider.query(Uri.EMPTY, null, null, null, null);

    assertThat(cursor).isNotNull();
    cursor.moveToNext();
    assertThat(cursor.getInt(0)).isEqualTo(1);
    assertThat(cursor.getString(1)).isEqualTo("num2");
  }

  @Test
  public void deleteStoresCall() {
    TestCalendarProvider testCalendarProvider = new TestCalendarProvider();
    String selection = "col1 = ? AND col2 = ?";
    String[] selectionArgs = {"first", "2"};
    Uri uri = Uri.parse("my_uri");

    testCalendarProvider.delete(uri, selection, selectionArgs);

    assertThat(testCalendarProvider.getCalls().get(0).getType()).isEqualTo(MethodType.DELETE);
    assertThat(testCalendarProvider.getCalls().get(0).getUri()).isEqualTo(uri);
    assertThat(testCalendarProvider.getCalls().get(0).getSelection()).isEqualTo(selection);
    assertThat(testCalendarProvider.getCalls().get(0).getSelectionArgs()).isEqualTo(selectionArgs);
  }

  @Test
  public void insertStoresCall() {
    TestCalendarProvider testCalendarProvider = new TestCalendarProvider();
    Uri uri = Uri.parse("my_uri");
    ContentValues values = new ContentValues();
    values.put("first", "one");
    values.put("second", "two");

    testCalendarProvider.insert(uri, values);

    assertThat(testCalendarProvider.getCalls().get(0).getType()).isEqualTo(MethodType.INSERT);
    assertThat(testCalendarProvider.getCalls().get(0).getUri()).isEqualTo(uri);
    assertThat(testCalendarProvider.getCalls().get(0).getValues()).isEqualTo(values);
  }

  @Test
  public void call_differentOrderSelection_assertEquals() {
    ProviderCall call1 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    ProviderCall call2 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    call1.setSelection("col1 = ? AND col2 = ?", new String[] {"1", "2"});
    call2.setSelection("col2 = ? AND col1 = ?", new String[] {"2", "1"});

    call1.assertEquals(call2);
  }

  @Test
  public void call_differentArgs_assertEqualsFails() {
    ProviderCall call1 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    ProviderCall call2 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    call1.setSelection("col1 = ? AND col2 = ?", new String[] {"1", "2"});
    call2.setSelection("col1 = ? AND col2 = ?", new String[] {"1", "3"});

    assertThrows(AssertionError.class, () -> call1.assertEquals(call2));
  }

  @Test
  public void call_differentColumns_assertEqualsFails() {
    ProviderCall call1 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    ProviderCall call2 = new ProviderCall(MethodType.INSERT, Uri.EMPTY);
    call1.setSelection("col1 = ? AND col2 = ?", new String[] {"1", "2"});
    call2.setSelection("col1 = ? AND col3 = ?", new String[] {"1", "2"});

    assertThrows(AssertionError.class, () -> call1.assertEquals(call2));
  }
}
