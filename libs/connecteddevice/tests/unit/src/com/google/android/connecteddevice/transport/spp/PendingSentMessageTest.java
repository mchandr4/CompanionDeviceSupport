package com.google.android.connecteddevice.transport.spp;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoTestRule;

@RunWith(AndroidJUnit4.class)
public class PendingSentMessageTest {
  @Rule public MockitoTestRule mockitoTestRule = MockitoJUnit.testRule(this);
  @Mock private PendingSentMessage.OnSuccessListener mockOnSuccessListener;

  @Test
  public void notifyMessageSent_onSuccessCalled() {
    PendingSentMessage pendingSentMessage = new PendingSentMessage();

    pendingSentMessage.setOnSuccessListener(mockOnSuccessListener);

    pendingSentMessage.notifyMessageSent();
    verify(mockOnSuccessListener).onSuccess();
  }

  @Test
  public void notifyMessageSent_nullListener_onSuccessNotCalled() {
    PendingSentMessage pendingSentMessage = new PendingSentMessage();

    pendingSentMessage.setOnSuccessListener(null);

    pendingSentMessage.notifyMessageSent();
    // This test is primarily meant to verify there are no crashes when the listener is null
    verifyZeroInteractions(mockOnSuccessListener);
  }
}
