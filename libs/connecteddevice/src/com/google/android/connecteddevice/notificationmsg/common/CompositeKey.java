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
