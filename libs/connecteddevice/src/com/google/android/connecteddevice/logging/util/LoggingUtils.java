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

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Utils for logging. */
public final class LoggingUtils {
  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

  private LoggingUtils() {}

  /** Returns serialization of the object in a json format byte array. */
  @NonNull
  public static byte[] objectToBytes(@NonNull Object object) {
    return createGson().toJson(object).getBytes(UTF_8);
  }

  /** Returns list deserialization of the json format byte array. */
  @NonNull
  public static <T> List<T> bytesToObjectList(@NonNull byte[] value, Class<T> classOfT) {
    Type listType = TypeToken.getParameterized(ArrayList.class, classOfT).getType();
    return createGson().fromJson(new String(value, UTF_8), listType);
  }

  /** Returns current time string in the given format. */
  // Date APIs are only used for log messages and must be Java 7 compatible for external
  // applications.
  @NonNull
  @SuppressWarnings("JavaUtilDate")
  public static String getCurrentTime(@NonNull String timeFormat) {
    DateFormat dateFormat = new SimpleDateFormat(timeFormat, Locale.US);
    return dateFormat.format(new Date());
  }

  @NonNull
  private static Gson createGson() {
    return new GsonBuilder().setDateFormat(ISO_FORMAT).create();
  }
}
