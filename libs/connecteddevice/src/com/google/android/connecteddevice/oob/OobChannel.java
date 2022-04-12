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

package com.google.android.connecteddevice.oob;

import androidx.annotation.NonNull;

/**
 * An interface for handling out of band data exchange. This interface should be implemented for
 * every out of band channel that is supported in device association.
 *
 * <p>Usage is:
 *
 * <ol>
 *   <li>Call {@link OobChannel#completeOobDataExchange(byte[])}
 *   <li>Provide way to stop the OOB data exchange through {@link OobChannel#interrupt()}
 * </ol>
 */
public interface OobChannel {
  /**
   * Exchange out of band data with a remote device.
   *
   * @param oobData The data that will be sent to remote device via OOB channel
   * @return {@code true} if the data exchange is started successfully, otherwise return {@code
   *     false}
   */
  boolean completeOobDataExchange(@NonNull byte[] oobData);

  /** Interrupt the current data exchange and prevent callbacks from being issued. */
  void interrupt();
}
