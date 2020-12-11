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

package com.google.android.connecteddevice.notificationmsg.common;

import java.util.Map;
import java.util.Objects;

/**
 * A composite key used for {@link Map} lookups, using two strings for checking equality and
 * hashing.
 */
public abstract class CompositeKey {
  private final String deviceId;
  private final String subKey;

  protected CompositeKey(String deviceId, String subKey) {
    this.deviceId = deviceId;
    this.subKey = subKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof CompositeKey)) {
      return false;
    }

    CompositeKey that = (CompositeKey) o;
    return Objects.equals(deviceId, that.deviceId) && Objects.equals(subKey, that.subKey);
  }

  /**
   * Returns true if the device address of this composite key equals {@code deviceId}.
   *
   * @param deviceId the device address which is compared to this key's device address
   * @return true if the device addresses match
   */
  public boolean matches(String deviceId) {
    return this.deviceId.equals(deviceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceId, subKey);
  }

  @Override
  public String toString() {
    return String.format(
        "%s, deviceId: %s, subKey: %s", getClass().getSimpleName(), deviceId, subKey);
  }

  /** Returns this composite key's device address. */
  public String getDeviceId() {
    return deviceId;
  }

  /** Returns this composite key's sub key. */
  public String getSubKey() {
    return subKey;
  }
}
