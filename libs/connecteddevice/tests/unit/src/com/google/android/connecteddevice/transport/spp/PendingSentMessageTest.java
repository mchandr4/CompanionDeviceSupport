/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.transport.spp;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    verifyNoMoreInteractions(mockOnSuccessListener);
  }
}
