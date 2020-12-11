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

package com.google.android.connecteddevice.model;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;

/** Definition of errors. */
public class Errors {

  /** Errors that occurred with a device. */
  @Retention(SOURCE)
  @IntDef({
    DEVICE_ERROR_INVALID_HANDSHAKE,
    DEVICE_ERROR_INVALID_MSG,
    DEVICE_ERROR_INVALID_DEVICE_ID,
    DEVICE_ERROR_INVALID_VERIFICATION,
    DEVICE_ERROR_INVALID_CHANNEL_STATE,
    DEVICE_ERROR_INVALID_ENCRYPTION_KEY,
    DEVICE_ERROR_STORAGE_FAILURE,
    DEVICE_ERROR_INVALID_SECURITY_KEY,
    DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED,
    DEVICE_ERROR_UNEXPECTED_DISCONNECTION
  })
  public @interface DeviceError {}

  public static final int DEVICE_ERROR_INVALID_HANDSHAKE = 0;
  public static final int DEVICE_ERROR_INVALID_MSG = 1;
  public static final int DEVICE_ERROR_INVALID_DEVICE_ID = 2;
  public static final int DEVICE_ERROR_INVALID_VERIFICATION = 3;
  public static final int DEVICE_ERROR_INVALID_CHANNEL_STATE = 4;
  public static final int DEVICE_ERROR_INVALID_ENCRYPTION_KEY = 5;
  public static final int DEVICE_ERROR_STORAGE_FAILURE = 6;
  public static final int DEVICE_ERROR_INVALID_SECURITY_KEY = 7;
  public static final int DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED = 8;
  public static final int DEVICE_ERROR_UNEXPECTED_DISCONNECTION = 9;

  private Errors() { }
}
