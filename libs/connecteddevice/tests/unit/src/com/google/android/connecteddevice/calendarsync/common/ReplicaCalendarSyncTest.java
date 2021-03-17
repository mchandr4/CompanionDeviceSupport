package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.connecteddevice.calendarsync.Calendar;
import com.google.android.connecteddevice.calendarsync.UpdateAction;
import com.google.android.connecteddevice.calendarsync.UpdateCalendars;
import com.google.android.connecteddevice.calendarsync.common.CalendarsObservable.CalendarsObserver;
import com.google.android.connecteddevice.calendarsync.common.CalendarsObservable.ObservationHandle;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.Invocation;
import org.mockito.stubbing.Answer;

/** {@link SourceCalendarSync} is a superset of functionality in {@link ReplicaCalendarSync}. */
@RunWith(JUnit4.class)
public class ReplicaCalendarSyncTest {
  private static final String DEVICE_ID = "The device id";

  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private RemoteSender mockRemoteSender;
  @Mock private CalendarStore mockCalendarStore;
  @Mock private Scheduler mockScheduler;
  @Mock private CalendarsObservable mockCalendarsObservable;
  @Mock private ContentCleanerDelegate mockContentCleanerDelegate;
  @Mock private CalendarManager mockCalendarManager;

  private ReplicaCalendarSync sync;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));

    CalendarManagerFactory mockCalendarManagerFactory = mock(CalendarManagerFactory.class);
    when(mockCalendarManagerFactory.create(any(), any())).thenReturn(mockCalendarManager);

    when(mockCalendarsObservable.observe(any())).thenReturn(mock(ObservationHandle.class));
    sync =
        new ReplicaCalendarSync(
            mockLoggerFactory,
            mockRemoteSender,
            mockCalendarStore,
            mockScheduler,
            mockCalendarsObservable,
            mockContentCleanerDelegate,
            mockCalendarManagerFactory);
  }

  @Test
  public void constructor_createsLogger() {
    verify(mockLoggerFactory).create(anyString());
  }

  @Test
  public void receiveCreateMessage_appliesUpdate() {
    String calendarKey = "key 1";
    UpdateCalendars message =
        UpdateCalendars.newBuilder()
            .addCalendars(Calendar.newBuilder().setKey(calendarKey).setAction(UpdateAction.CREATE))
            .build();

    sync.start();
    sync.receive(DEVICE_ID, message.toByteArray());

    verify(mockCalendarManager).applyUpdateMessages(DEVICE_ID, message.getCalendarsList());
  }

  @Test
  public void receive_v1_sendsNoReply() throws InvalidProtocolBufferException {
    UpdateCalendars update =
        UpdateCalendars.newBuilder()
            .addCalendars(Calendar.newBuilder().setKey("key 1").setAction(UpdateAction.CREATE))
            .build();

    sync.start();
    sync.receive(DEVICE_ID, update.toByteArray());

    // There should be no update message because the source device is not capable of receiving.
    List<UpdateCalendars> sentUpdateMessages = getSentUpdateMessages();
    assertThat(sentUpdateMessages).isEmpty();
  }

  @Test
  public void receive_v2_sendsAckReply() throws InvalidProtocolBufferException {
    UpdateCalendars update =
        UpdateCalendars.newBuilder()
            .addCalendars(Calendar.newBuilder().setKey("key 1").setAction(UpdateAction.CREATE))
            .setVersion(1)
            .build();

    sync.start();
    sync.receive(DEVICE_ID, update.toByteArray());

    // There should be one update ack message.
    List<UpdateCalendars> sentUpdateMessages = getSentUpdateMessages();
    assertThat(sentUpdateMessages).hasSize(1);
    assertThat(sentUpdateMessages.get(0).getType()).isEqualTo(UpdateCalendars.Type.ACKNOWLEDGE);
  }

  @Test
  public void onCalendarsChange_doesNotSendUpdateMessage() throws InvalidProtocolBufferException {
    // Remember the observer when calendar is observed so we can trigger it later.
    CalendarsObserver[] observer = new CalendarsObserver[1];
    when(mockCalendarsObservable.observe(any()))
        .thenAnswer(
            (Answer<ObservationHandle>)
                invocation -> {
                  observer[0] = invocation.getArgument(0);
                  return mock(ObservationHandle.class);
                });
    when(mockCalendarManager.read(eq(null), any())).thenReturn(Calendar.getDefaultInstance());
    String calendarKey = "key 1";
    Calendar createCalendarMessage =
        Calendar.newBuilder().setKey(calendarKey).setAction(UpdateAction.CREATE).build();
    byte[] message =
        UpdateCalendars.newBuilder().addCalendars(createCalendarMessage).build().toByteArray();

    // Prepare the replica sync by starting and receiving some calendars.
    sync.start();
    sync.receive(DEVICE_ID, message);

    // Signal that the calendars changed.
    observer[0].onCalendarsChanged();

    // There should be no update message because the source device is not capable of receiving.
    List<UpdateCalendars> sentUpdateMessages = getSentUpdateMessages();
    assertThat(sentUpdateMessages).isEmpty();
  }

  @Test
  public void clear_cleansContent() {
    byte[] message =
        UpdateCalendars.newBuilder()
            .addCalendars(Calendar.newBuilder().setKey("key 1").setAction(UpdateAction.CREATE))
            .build()
            .toByteArray();
    sync.start();
    sync.receive(DEVICE_ID, message);

    sync.clear(DEVICE_ID);

    verify(mockCalendarManager).deleteAll(DEVICE_ID);
  }

  @Test
  public void receiveDisableMessage_cleansContent() {
    UpdateCalendars create =
        UpdateCalendars.newBuilder()
            .addCalendars(Calendar.newBuilder().setKey("key 1").setAction(UpdateAction.CREATE))
            .build();
    sync.start();
    sync.receive(DEVICE_ID, create.toByteArray());

    UpdateCalendars disable =
        UpdateCalendars.newBuilder().setType(UpdateCalendars.Type.DISABLE).build();
    sync.receive(DEVICE_ID, disable.toByteArray());

    verify(mockCalendarManager).deleteAll(DEVICE_ID);
  }

  @Test
  public void stop_cleansContent() {
    sync.stop();

    verify(mockContentCleanerDelegate).clean();
  }

  @Test
  public void start_cleansContent() {
    sync.start();

    verify(mockContentCleanerDelegate).clean();
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
}
