package com.google.android.connecteddevice.calendarsync.common;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CalendarSyncTest {

  @Mock private Logger.Factory mockLoggerFactory;

  @SuppressWarnings("unused")
  private CalendarSync sync;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockLoggerFactory.create(anyString())).thenReturn(mock(Logger.class));
    sync = new CalendarSync(mockLoggerFactory);
  }

  @Test
  public void testCreate_createsLogger() {
    verify(mockLoggerFactory).create(anyString());
  }
}
