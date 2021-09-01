package com.google.android.connecteddevice.calendarsync.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.connecteddevice.calendarsync.Attendee;
import com.google.android.connecteddevice.calendarsync.common.PlatformContentDelegate.Content;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AttendeeManagerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Object EVENT_ID = "an event id";
  private static final String ATTENDEE_EMAIL = "a attendee email";

  @Mock private CommonLogger.Factory mockLoggerFactory;
  @Mock private PlatformContentDelegate<Attendee> mockAttendeeContent;

  private AttendeeManager attendeeManager;

  @Before
  public void setUp() {
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(CommonLogger.class));

    Attendee attendee = Attendee.newBuilder().setEmail(ATTENDEE_EMAIL).build();
    Content<Attendee> content = new Content<>(attendee, 1L);
    when(mockAttendeeContent.read(EVENT_ID, ATTENDEE_EMAIL)).thenReturn(content);
    when(mockAttendeeContent.readAll(EVENT_ID)).thenReturn(ImmutableList.of(content));

    attendeeManager = new AttendeeManager(mockLoggerFactory, mockAttendeeContent);
  }

  @Test
  public void read_returnsAttendee() {
    Attendee attendee = attendeeManager.read(EVENT_ID, ATTENDEE_EMAIL);

    assertThat(attendee).isNotNull();
  }

  @Test
  public void readAll_returnsAttendees() {
    ImmutableList<Attendee> attendees = attendeeManager.readAll(EVENT_ID);

    assertThat(attendees).hasSize(1);
  }
}
