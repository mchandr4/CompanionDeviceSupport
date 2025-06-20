/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.logging.util;

import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoggingUtilsTest {

  @Test
  public void getCurrentTime_testStringFormat() {
    String testTimeFormat = "MM/DD/YYYY HH:mm";
    DateFormat dateFormat = new SimpleDateFormat(testTimeFormat);
    String timeString = LoggingUtils.getCurrentTime(testTimeFormat);

    assertDoesNotThrow(() -> dateFormat.parse(timeString));
  }

  private static void assertDoesNotThrow(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
      fail("Should not have thrown " + e);
    }
  }
}
