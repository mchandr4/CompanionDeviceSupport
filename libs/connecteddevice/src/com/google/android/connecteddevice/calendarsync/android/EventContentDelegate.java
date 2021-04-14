package com.google.android.connecteddevice.calendarsync.android;

import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.REPLICA;
import static com.google.android.connecteddevice.calendarsync.android.ContentOwnership.SOURCE;
import static com.google.android.connecteddevice.calendarsync.android.FieldTranslator.createBooleanField;
import static com.google.android.connecteddevice.calendarsync.android.FieldTranslator.createInstantField;
import static com.google.android.connecteddevice.calendarsync.android.FieldTranslator.createIntegerField;
import static com.google.android.connecteddevice.calendarsync.android.FieldTranslator.createLongField;
import static com.google.android.connecteddevice.calendarsync.android.FieldTranslator.createStringField;
import static com.google.android.connecteddevice.calendarsync.common.TimeProtoUtil.toInstant;
import static com.google.android.connecteddevice.calendarsync.common.TimeProtoUtil.toTimestamp;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.calendarsync.Color;
import com.google.android.connecteddevice.calendarsync.Event;
import com.google.android.connecteddevice.calendarsync.Event.Status;
import com.google.android.connecteddevice.calendarsync.TimeZone;
import com.google.android.connecteddevice.calendarsync.android.FieldTranslator.ColumnFieldTranslator;
import com.google.android.connecteddevice.calendarsync.common.CommonLogger;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.protobuf.MessageLite;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.Nullable;

/**
 * Android specific access to the event content items.
 *
 * <p>When reading {@link Event}s the {@link android.provider.CalendarContract.Instances} content is
 * used so each recurring event can appear more than once e.g. a regular team meeting.
 */
final class EventContentDelegate extends BaseContentDelegate<Event> {
  private static final String TAG = "EventContentDelegate";

  /**
   * A factory for {@link PlatformContentDelegate<Event>}s that allows {@link
   * com.google.android.connecteddevice.calendarsync.common.EventManager} to read events in a given
   * time range without all the dependencies required by {@link EventContentDelegate}.
   */
  static class Factory implements EventContentDelegateFactory {

    private final CommonLogger.Factory commonLoggerFactory;
    private final ContentResolver resolver;
    private final ContentOwnership ownership;

    Factory(
        CommonLogger.Factory commonLoggerFactory,
        ContentResolver resolver,
        ContentOwnership ownership) {
      this.commonLoggerFactory = commonLoggerFactory;
      this.resolver = resolver;
      this.ownership = ownership;
    }

    /**
     * Creates a {@link PlatformContentDelegate<Event>} with a given time range.
     *
     * <p>If the time range is {@code null} then the calendar will be write only and reading will
     * through an exception.
     */
    @Override
    public PlatformContentDelegate<Event> create(@Nullable Range<Instant> timeRange) {
      return new EventContentDelegate(commonLoggerFactory, resolver, ownership, timeRange);
    }
  }

  /** The set of fields that are used when writing an event. */
  private final ImmutableSet<FieldTranslator<?>> writeEventFields;

  private final Uri writeContentUri;

  EventContentDelegate(
      CommonLogger.Factory commonLoggerFactory,
      ContentResolver resolver,
      ContentOwnership ownership,
      Range<Instant> timeRange) {
    super(
        commonLoggerFactory.create(TAG),
        timeRange != null
            ? createReadInstanceUri(timeRange.lowerEndpoint(), timeRange.upperEndpoint(), ownership)
            : null, // No read uri on replica when source sends no time range.
        createKeyField(ownership),
        createReadInstanceFields(ownership),
        resolver,
        /* idColumn= */ Instances.EVENT_ID,
        /* parentIdColumn= */ Instances.CALENDAR_ID);
    writeEventFields = createWriteEventFields(ownership);
    writeContentUri =
        ownership == SOURCE ? Events.CONTENT_URI : addSyncAdapterParameters(Events.CONTENT_URI);
  }

  @Override
  protected ContentValues createContentValues(
      Event message, Object parentId, Collection<FieldTranslator<?>> fields) {
    // Use write fields when creating content which differ from read fields.
    return super.createContentValues(message, parentId, writeEventFields);
  }

  /** Writes to the Events content rather that Instances from where data is read. */
  @Override
  protected Uri getWriteContentUri() {
    return writeContentUri;
  }

  @Override
  protected MessageLite.Builder createMessageBuilder() {
    return Event.newBuilder();
  }

  /**
   * Creates a content {@link Uri} to {@link android.provider.CalendarContract.Instances} for the
   * given time range.
   */
  private static Uri createReadInstanceUri(Instant start, Instant end, ContentOwnership ownership) {
    Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
    ContentUris.appendId(builder, start.toEpochMilli());
    ContentUris.appendId(builder, end.toEpochMilli());
    Uri contentUri = builder.build();
    if (ownership == REPLICA) {
      contentUri = addSyncAdapterParameters(contentUri);
    }
    return contentUri;
  }

  /** Fields to read from {@link android.provider.CalendarContract.Instances}. */
  private static ImmutableSet<FieldTranslator<?>> createReadInstanceFields(
      ContentOwnership source) {
    return ImmutableSet.of(
        createKeyField(source),
        TITLE,
        DESCRIPTION,
        LOCATION,
        ALL_DAY,
        BEGIN_READ,
        TIME_ZONE,
        createFinishTimeField(/* read= */ true),
        END_TIMEZONE,
        COLOR,
        ORGANIZER,
        STATUS);
  }

  /** Creates fields to write to {@link android.provider.CalendarContract.Events}. */
  private ImmutableSet<FieldTranslator<?>> createWriteEventFields(ContentOwnership ownership) {
    ImmutableSet.Builder<FieldTranslator<?>> builder = ImmutableSet.builder();
    builder.add(
        TITLE,
        DESCRIPTION,
        LOCATION,
        ALL_DAY,
        createBeginTimeField(/* read= */ false),
        TIME_ZONE,
        createFinishTimeField(/* read= */ false),
        END_TIMEZONE,
        COLOR,
        ORGANIZER,
        STATUS);

    // Only write the key on the replica which is used to find the original event on the source.
    if (ownership == REPLICA) {
      builder.add(keyField);
    }
    return builder.build();
  }

  /**
   * The event instance id is not stable so the event id and start time need to be used. Create a
   * field for the event identifier that combines eventId and start time.
   */
  private static FieldTranslator<String> createKeyField(ContentOwnership ownership) {
    return new FieldTranslator<String>(
        Event::getKey,
        Event.Builder::setKey,
        ownership == SOURCE
            ? ImmutableSet.of(
                Instances.EVENT_ID,
                Instances.BEGIN,
                Instances.RRULE,
                Instances.RDATE,
                Instances.ORIGINAL_ID,
                Instances.ORIGINAL_INSTANCE_TIME)
            : ImmutableSet.of(Events._SYNC_ID)) {
      @Override
      public String get(Cursor cursor) {
        if (ownership == SOURCE) {
          // Send all the data needed to find the same event if it is changed on the replica.
          return createInstanceKeyText(cursor);
        } else {
          // On the replica, store the source id in a sync adapter field.
          return cursor.getString(getColumnIndex(cursor, Events._SYNC_ID));
        }
      }

      @Override
      public void set(String value, ContentValues values) {
        // Set the exact same values that were used above to find the original event instance.
        if (ownership == SOURCE) {
          addKeyContentValues(value, values);
        } else {
          values.put(Events._SYNC_ID, value);
        }
      }

      private String createInstanceKeyText(Cursor cursor) {
        if (RRULE.has(cursor) || RDATE.has(cursor)) {
          return createRecurringKey(ID.get(cursor), BEGIN_READ.get(cursor));
        } else if (ORIGINAL_ID.has(cursor)) {
          return createExceptionKey(ORIGINAL_ID.get(cursor), ORIGINAL_BEGIN.get(cursor));
        } else {
          return createSingleKey(ID.get(cursor));
        }
      }

      private void addKeyContentValues(String value, ContentValues values) {
        Iterator<String> parts = KEY_SPLITTER.split(value).iterator();
        switch (RecurrenceType.fromCode(parts.next())) {
          case SINGLE:
            ID.set(Long.parseLong(parts.next()), values);
            break;
          case EXCEPTION:
            ORIGINAL_ID.set(Long.parseLong(parts.next()), values);
            ORIGINAL_BEGIN.set(Instant.ofEpochMilli(Long.parseLong(parts.next())), values);
            break;
          case RECURRING:
            ID.set(Long.parseLong(parts.next()), values);
            BEGIN_READ.set(Instant.ofEpochMilli(Long.parseLong(parts.next())), values);
            break;
        }
      }
    };
  }

  private static final FieldTranslator<String> DESCRIPTION =
      createStringField(Events.DESCRIPTION, Event::getDescription, Event.Builder::setDescription);

  private static final FieldTranslator<String> TITLE =
      createStringField(Events.TITLE, Event::getTitle, Event.Builder::setTitle);

  private static final FieldTranslator<String> LOCATION =
      createStringField(Events.EVENT_LOCATION, Event::getLocation, Event.Builder::setLocation);

  private static final FieldTranslator<String> ORGANIZER =
      createStringField(Events.ORGANIZER, Event::getOrganizer, Event.Builder::setOrganizer);

  private static final FieldTranslator<Boolean> ALL_DAY =
      createBooleanField(Events.ALL_DAY, Event::getIsAllDay, Event.Builder::setIsAllDay);

  // Message uses seconds but provider uses millis.
  private static FieldTranslator<Instant> createBeginTimeField(boolean read) {
    return createInstantField(
        read ? Instances.BEGIN : Events.DTSTART,
        (Event message) -> message.hasBeginTime() ? toInstant(message.getBeginTime()) : null,
        (Event.Builder builder, Instant value) -> builder.setBeginTime(toTimestamp(value)));
  }

  private static final FieldTranslator<Instant> BEGIN_READ = createBeginTimeField(/* read= */ true);

  private static final FieldTranslator<String> TIME_ZONE =
      createStringField(
          Events.EVENT_TIMEZONE,
          (Event message) -> message.hasTimeZone() ? message.getTimeZone().getName() : null,
          (Event.Builder builder, String value) ->
              builder.setTimeZone(TimeZone.newBuilder().setName(value)));

  private static FieldTranslator<Instant> createFinishTimeField(boolean read) {
    return createInstantField(
        read ? Instances.END : Events.DTEND,
        (Event message) -> message.hasEndTime() ? toInstant(message.getEndTime()) : null,
        (Event.Builder builder, Instant value) -> builder.setEndTime(toTimestamp(value)));
  }

  private static final FieldTranslator<Integer> COLOR =
      createIntegerField(
          Events.EVENT_COLOR,
          (Event message) -> message.hasColor() ? message.getColor().getArgb() : null,
          (Event.Builder builder, Integer value) ->
              builder.setColor(Color.newBuilder().setArgb(value)));

  private static final FieldTranslator<String> END_TIMEZONE =
      createStringField(
          Events.EVENT_END_TIMEZONE,
          (Event message) -> message.hasEndTimeZone() ? message.getEndTimeZone().getName() : null,
          (Event.Builder builder, String value) ->
              builder.setEndTimeZone(TimeZone.newBuilder().setName(value)));

  private static final FieldTranslator<Integer> STATUS =
      createIntegerField(
          Events.STATUS,
          (Event message) -> statusToInt(message.getStatus()),
          (Event.Builder builder, Integer value) -> builder.setStatus(intToStatus(value)));

  private static final ColumnFieldTranslator<Long> ID =
      createLongField(Instances.EVENT_ID, null, null);
  private static final ColumnFieldTranslator<Long> ORIGINAL_ID =
      createLongField(Instances.ORIGINAL_ID, null, null);
  private static final ColumnFieldTranslator<Instant> ORIGINAL_BEGIN =
      createInstantField(Instances.ORIGINAL_INSTANCE_TIME, null, null);
  private static final ColumnFieldTranslator<String> RRULE =
      createStringField(Instances.RRULE, null, null);
  private static final ColumnFieldTranslator<String> RDATE =
      createStringField(Instances.RDATE, null, null);

  /** The type of event regarding recurrence. */
  private enum RecurrenceType {

    /** An event that only occurs once. */
    SINGLE("S"),

    /** An event that recurs and can have multiple instances with different begin times. */
    RECURRING("R"),

    /**
     * An exception to a recurring event is any change to a recurring event such as a change to a
     * regular meeting.
     */
    EXCEPTION("X");

    /** The code to include in the key. */
    final String code;

    RecurrenceType(String code) {
      this.code = code;
    }

    /**
     * Find the {@link RecurrenceType} with the given code or throw an exception if it is invalid.
     */
    public static RecurrenceType fromCode(String code) {
      for (RecurrenceType candidate : RecurrenceType.values()) {
        if (candidate.code.equals(code)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("Unknown recurrence code: " + code);
    }
  }

  private static Status intToStatus(int status) {
    switch (status) {
      case Instances.STATUS_CANCELED:
        return Status.CANCELED;
      case Instances.STATUS_CONFIRMED:
        return Status.CONFIRMED;
      case Instances.STATUS_TENTATIVE:
        return Status.TENTATIVE;
      default:
        return Status.UNSPECIFIED_STATUS;
    }
  }

  @Nullable
  private static Integer statusToInt(Status status) {
    switch (status) {
      case CANCELED:
        return Instances.STATUS_CANCELED;
      case CONFIRMED:
        return Instances.STATUS_CONFIRMED;
      case TENTATIVE:
        return Instances.STATUS_TENTATIVE;
      default:
        // A null value will not be set in the Cursor as there is no valid "unspecified" value.
        return null;
    }
  }

  private static final String KEY_SEPARATOR = ":";
  private static final Joiner KEY_JOINER = Joiner.on(KEY_SEPARATOR);
  private static final Splitter KEY_SPLITTER = Splitter.on(KEY_SEPARATOR);

  /** Create a key for a non-recurring event which only requires the event id to identify it. */
  @VisibleForTesting
  static String createSingleKey(long id) {
    return KEY_JOINER.join(RecurrenceType.SINGLE.code, id);
  }

  /**
   * Create a key for a recurring event instance using the begin time. The time is required to
   * differentiate this occurrence from others.
   */
  @VisibleForTesting
  static String createRecurringKey(long id, Instant beginTime) {
    return KEY_JOINER.join(RecurrenceType.RECURRING.code, id, beginTime.toEpochMilli());
  }

  /**
   * Create a key for an exception to a recurring event using the original event begin time and id.
   * They are required for creating a new exception to replace this one if it is modified.
   */
  @VisibleForTesting
  static String createExceptionKey(long originalId, Instant originalBeginTime) {
    return KEY_JOINER.join(
        RecurrenceType.EXCEPTION.code, originalId, originalBeginTime.toEpochMilli());
  }
}
