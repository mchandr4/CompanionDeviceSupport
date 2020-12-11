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

package com.google.android.connecteddevice.storage;

import androidx.annotation.Nullable;

/** A helper that can encrypt and decrypt arbitrary values. */
interface CryptoHelper {
  /**
   * Encrypts the given {@code value} and returns its encrypted value or {@code null} if encryption
   * fails.
   *
   * <p>Passing in {@code null} as the value will always just return {@code null}.
   */
  @Nullable
  String encrypt(@Nullable byte[] value);

  /**
   * Decrypts the given {@code value} and returns the decrypted value or {@code null} if decryption
   * cannot be performed.
   *
   * <p>Passing in {@code null} as the value will always just return {@code null}.
   */
  @Nullable
  byte[] decrypt(@Nullable String value);
}
