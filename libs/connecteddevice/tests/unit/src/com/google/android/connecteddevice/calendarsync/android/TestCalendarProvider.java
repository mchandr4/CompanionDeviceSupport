package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.calendarsync.android.TestCalendarProvider.ProviderCall.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A test content provider that returns a {@link MatrixCursor} from {@link #query(Uri, String[],
 * String, String[], String)}.
 *
 * <p>It stored invocations to certain other methods that can be retrieved and asserted.
 */
public class TestCalendarProvider extends ContentProvider {
  // Workaround null not being allowed in an ImmutableMap.
  public static final Object NULL_VALUE = new Object();

  private MatrixCursor cursor;
  private final List<ProviderCall> calls = new ArrayList<>();

  /** Hold details of a single call to a method. */
  public static class ProviderCall {

    /** The type of method call that was made. */
    public enum MethodType {
      DELETE,
      INSERT,
    }

    // Column names must start with a lowercase letter to distinguish from operators like AND.
    private static final Pattern COLUMN_PATTERN = Pattern.compile("[a-z][a-zA-Z_0-9]+");

    public ProviderCall(MethodType type, Uri uri) {
      this.type = type;
      this.uri = uri;
    }

    private final MethodType type;
    private final Uri uri;
    private ContentValues values = new ContentValues();
    private String selection;
    private String[] selectionArgs;
    private Map<String, String> columnToArg;

    public String getSelection() {
      return selection;
    }

    public void setSelection(String selection, String[] selectionArgs) {
      this.selectionArgs = selectionArgs;
      this.selection = selection;
      List<String> columns = parseSelectColumns(selection);
      columnToArg = new HashMap<>();
      for (int i = 0; i < columns.size(); i++) {
        columnToArg.put(columns.get(i), selectionArgs[i]);
      }
    }

    public MethodType getType() {
      return type;
    }

    public Uri getUri() {
      return uri;
    }

    public ContentValues getValues() {
      return values;
    }

    public void setValues(ContentValues values) {
      this.values = values;
    }

    public String[] getSelectionArgs() {
      return selectionArgs;
    }

    public void assertEquals(ProviderCall expected) {
      assertThat(type).isEqualTo(expected.type);
      assertThat(uri).isEqualTo(expected.uri);
      assertThat(values).isEqualTo(expected.values);

      // Do not compare the select and selectArgs directly as the order can vary.
      assertThat(columnToArg).isEqualTo(expected.columnToArg);
    }

    private List<String> parseSelectColumns(@Nullable String selection) {
      List<String> results = new ArrayList<>();
      if (selection != null) {
        Matcher matcher = COLUMN_PATTERN.matcher(selection);
        while (matcher.find()) {
          results.add(matcher.group());
        }
      }
      return results;
    }
  }

  /**
   * Sets the columns names to use in the {@link Cursor} returned by {@link #query(Uri, String[],
   * String, String[], String)}.
   */
  public void setColumns(List<String> columns) {
    cursor = new MatrixCursor(columns.toArray(new String[0]));
  }

  /**
   * Adds a row of data to the {@link Cursor} returned by {@link #query(Uri, String[], String,
   * String[], String)}.
   */
  public void addRow(Map<String, Object> columnToValue) {
    RowBuilder builder = cursor.newRow();
    for (String column : columnToValue.keySet()) {
      Object value = columnToValue.get(column);
      if (value != null && value != NULL_VALUE) {
        builder.add(column, value);
      }
    }
  }

  /**
   * Adds a row of data to the {@link Cursor} returned by {@link #query(Uri, String[], String,
   * String[], String)}.
   *
   * <p>Replaces a single value from {@code columnToValue} with {@code column} and {@code value}.
   */
  public void addRowWithReplacement(
      Map<String, Object> columnToValue, String column, Object value) {
    Map<String, Object> replaced = new HashMap<>(columnToValue);
    replaced.put(column, value);
    addRow(replaced);
  }

  /** Gets the method calls made. */
  public List<ProviderCall> getCalls() {
    return calls;
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    if (cursor == null) {
      throw new IllegalStateException("Must call #setCursor");
    }
    return cursor;
  }

  @Override
  public boolean onCreate() {
    return false;
  }

  @Nullable
  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(Uri uri, ContentValues values) {
    ProviderCall call = new ProviderCall(MethodType.INSERT, uri);
    call.values = values;
    calls.add(call);
    return ContentUris.withAppendedId(Calendars.CONTENT_URI, 1);
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    ProviderCall call = new ProviderCall(MethodType.DELETE, uri);
    call.setSelection(selection, selectionArgs);
    calls.add(call);
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }
}
