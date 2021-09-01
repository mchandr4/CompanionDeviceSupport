package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.connecteddevice.calendarsync.Calendar;
import com.google.android.connecteddevice.calendarsync.UpdateAction;
import com.google.android.connecteddevice.calendarsync.UpdateCalendars;
import com.google.android.connecteddevice.calendarsync.common.CalendarsObservable.CalendarsObserver;
import com.google.android.connecteddevice.calendarsync.common.CalendarsObservable.ObservationHandle;
import com.google.android.connecteddevice.calendarsync.common.Scheduler.ScheduledTaskHandle;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

/** {@link SourceCalendarSync} is a superset of functionality in {@link ReplicaCalendarSync}. */
@SuppressWarnings("ProtoParseWithRegistry")
@RunWith(JUnit4.class)
public class SourceCalendarSyncTest {
  private static final String DEVICE_ID = "The device id";
  private static final ImmutableSet<String> CALENDAR_IDS = ImmutableSet.of("id1", "id2", "id3");
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2020, 3, 4, 5, 6, 7, 8);
  private static final ZonedDateTime ZONED_DATE_TIME = LOCAL_DATE_TIME.atZone(ZONE_ID);
  private static final Instant FROM_INSTANT = ZONED_DATE_TIME.truncatedTo(DAYS).toInstant();
  private static final Instant REFRESH_INSTANT = FROM_INSTANT.plus(1, DAYS);
  private static final Instant TO_INSTANT = FROM_INSTANT.plus(2, DAYS);
  private static final TimeWindow TIME_WINDOW =
      TimeWindow.create(FROM_INSTANT, TO_INSTANT, REFRESH_INSTANT);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  // Suppress use of parameter "days" as it does not make sense to pass a Duration here.
  @SuppressWarnings("GoodTime")
  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private RemoteSender mockRemoteSender;
  @Mock private CalendarStore mockCalendarStore;
  @Mock private CalendarsObservable mockCalendarsObservable;
  @Mock private CalendarManager mockCalendarManager;
  @Mock private Scheduler mockScheduler;

  private SourceCalendarSync sync;

  @Before
  public void setUp() {
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));

    CalendarManagerFactory mockCalendarManagerFactory = mock(CalendarManagerFactory.class);
    when(mockCalendarManagerFactory.create(any(), any())).thenReturn(mockCalendarManager);
    Supplier<TimeWindow> timeWindowSupplier = () -> TIME_WINDOW;

    when(mockCalendarManager.createReplaceMessages(any(), any()))
        .thenReturn(
            ImmutableSet.of(
                Calendar.newBuilder()
                    .setAction(UpdateAction.REPLACE)
                    .setKey("key to replace")
                    .build()));
    when(mockCalendarStore.fetchAll(DEVICE_ID)).thenReturn(ImmutableSet.of());
    when(mockCalendarsObservable.observe(any())).thenReturn(mock(ObservationHandle.class));
    sync =
        new SourceCalendarSync(
            mockLoggerFactory,
            mockRemoteSender,
            mockCalendarStore,
            mockCalendarsObservable,
            mockCalendarManagerFactory,
            timeWindowSupplier,
            mockScheduler);
  }

  @Test
  public void constructor_createsLogger() {
    verify(mockLoggerFactory).create(anyString());
  }

  @Test
  public void sync_withoutStart_throws() {
    assertThrows(IllegalStateException.class, () -> sync.sync(DEVICE_ID, CALENDAR_IDS));
  }

  @Test
  public void start_schedulesUpdateForRefresh() {
    sync.start();

    verify(mockScheduler).schedule(eq(REFRESH_INSTANT), any());
  }

  @Test
  public void stop_cancelsScheduledUpdate() {
    ScheduledTaskHandle mockScheduledTaskHandle = mock(ScheduledTaskHandle.class);
    when(mockScheduler.schedule(any(), any())).thenReturn(mockScheduledTaskHandle);

    sync.start();
    sync.stop();

    verify(mockScheduledTaskHandle).cancel();
  }

  @Test
  public void start_executeScheduledTask_sendsMessage() {
    sync.start();
    sync.sync(DEVICE_ID, CALENDAR_IDS);
    Runnable task = (Runnable) getInvocationArguments(mockScheduler)[1];
    task.run();

    // The second message sent is due to the scheduled update.
    verify(mockRemoteSender, times(2)).send(eq(DEVICE_ID), any());
  }

  @Test
  public void sync_sendsAddUpdateMessage() throws InvalidProtocolBufferException {
    String calendarKey = "The calendar id";
    when(mockCalendarManager.read(null, calendarKey))
        .thenReturn(Calendar.newBuilder().setKey(calendarKey).build());

    sync.start();
    sync.sync(DEVICE_ID, ImmutableSet.of(calendarKey));

    List<UpdateCalendars> updateCalendars = getSentUpdateMessages();
    assertThat(updateCalendars).hasSize(1);
  }

  @Test
  public void disable_sendsDisableMessage() throws InvalidProtocolBufferException {
    // Clearing and then syncing no calendars should not send again.
    sync.start();
    sync.disable(DEVICE_ID);

    // Only one message was sent for the initial sync.
    ArgumentCaptor<byte[]> bytesArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(mockRemoteSender).send(eq(DEVICE_ID), bytesArgumentCaptor.capture());
    UpdateCalendars update = UpdateCalendars.parseFrom(bytesArgumentCaptor.getValue());
    assertThat(update.getType()).isEqualTo(UpdateCalendars.Type.DISABLE);
  }

  @Test
  public void onCalendarsChange_sendsUpdateMessage() throws InvalidProtocolBufferException {
    // Remember the observer when calendar is observed.
    CalendarsObserver[] observer = new CalendarsObserver[1];
    when(mockCalendarsObservable.observe(any()))
        .thenAnswer(
            (Answer<ObservationHandle>)
                invocation -> {
                  observer[0] = invocation.getArgument(0);
                  return mock(ObservationHandle.class);
                });
    when(mockCalendarManager.read(eq(null), any())).thenReturn(Calendar.getDefaultInstance());

    // Signal that the calendars changed
    sync.start();
    sync.sync(DEVICE_ID, CALENDAR_IDS);
    observer[0].onCalendarsChanged();

    // A task will be posted to run after a delay.
    ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(mockScheduler).delay(any(), runnableArgumentCaptor.capture());
    Runnable task = runnableArgumentCaptor.getValue();
    task.run();

    // Expect 2 calendar update messages because #sync sends one before the change is fired.
    List<UpdateCalendars> sentUpdateMessages = getSentUpdateMessages();
    assertThat(sentUpdateMessages).hasSize(2);
  }

  @Test
  public void stop_callsUnregister() {
    ObservationHandle mockObservationHandle = mock(ObservationHandle.class);
    when(mockCalendarsObservable.observe(any()))
        .thenAnswer((Answer<ObservationHandle>) invocation -> mockObservationHandle);

    // Signal that the calendars changed.
    sync.start();
    sync.stop();

    verify(mockObservationHandle).unregister();
  }

  private List<UpdateCalendars> getSentUpdateMessages() throws InvalidProtocolBufferException {
    Iterable<Invocation> invocations = mockingDetails(mockRemoteSender).getInvocations();
    List<UpdateCalendars> allUpdateCalendars = new ArrayList<>();
    for (Invocation invocation : invocations) {
      byte[] message = (byte[]) invocation.getRawArguments()[1];
      UpdateCalendars updateCalendars = UpdateCalendars.parseFrom(message);
      allUpdateCalendars.add(updateCalendars);
    }
    return allUpdateCalendars;
  }

  private java.lang.Object[] getInvocationArguments(Object mock) {
    return mockingDetails(mock).getInvocations().iterator().next().getRawArguments();
  }
}
