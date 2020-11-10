package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logi;

/** Logging class for collecting metrics related to the Trusted Device feature. */
class TrustedDeviceEventLog {

  private static final String TAG = "TrustedDeviceEvent";

  private TrustedDeviceEventLog() {}

  /** Mark in the log that the {@link TrustedDeviceManagerService} has started. */
  static void onTrustedDeviceServiceStarted() {
    logi(TAG, "SERVICE_STARTED");
  }

  /** Mark in the log that the {@link TrustedDeviceAgentService} has started. */
  static void onTrustAgentStarted() {
    logi(TAG, "TRUST_AGENT_STARTED");
  }

  /** Mark in the log that credentials were received from the device. */
  static void onCredentialsReceived() {
    logi(TAG, "CREDENTIALS_RECEIVED");
  }

  /** Mark in the log that the user successfully unlocked. */
  static void onUserUnlocked() {
    logi(TAG, "USER_UNLOCKED");
  }
}
