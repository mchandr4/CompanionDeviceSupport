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

package com.google.android.connecteddevice.calendarsync;

import android.content.Context;
import androidx.annotation.NonNull;

/** A manager class handling the {@link CalendarSyncFeature}. */
final class CalendarSyncManager {
  private final CalendarSyncFeature feature;

  CalendarSyncManager(@NonNull Context context) {
    feature = new CalendarSyncFeature(context);
    feature.start();
  }

  public void cleanup() {
    feature.stop();
  }
}
