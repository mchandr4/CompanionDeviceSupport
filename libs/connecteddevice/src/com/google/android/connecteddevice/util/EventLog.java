package com.google.android.connecteddevice.util;

import static com.google.android.connecteddevice.util.SafeLog.logi;

/** Logging class for collecting metrics. */
public class EventLog {

  private static final String TAG = "ConnectedDeviceEvent";

  private EventLog() {}

  /** Mark in log that the service has started. */
  public static void onServiceStarted() {
    logi(TAG, "SERVICE_STARTED");
  }

  /** Mark in log that the ConnectedDeviceManager has started. */
  public static void onConnectedDeviceManagerStarted() {
    logi(TAG, "CONNECTED_DEVICE_MANAGER_STARTED");
  }

  /** Mark in the log that BLE is on. */
  public static void onBleOn() {
    logi(TAG, "BLE_ON");
  }

  /** Mark in the log that a search for the user's device has started. */
  public static void onStartDeviceSearchStarted() {
    logi(TAG, "SEARCHING_FOR_DEVICE");
  }

  /** Mark in the log that a device connected. */
  public static void onDeviceConnected() {
    logi(TAG, "DEVICE_CONNECTED");
  }

  /** Mark in the log that the device has sent its id. */
  public static void onDeviceIdReceived() {
    logi(TAG, "RECEIVED_DEVICE_ID");
  }

  /** Mark in the log that a secure channel has been established with a device. */
  public static void onSecureChannelEstablished() {
    logi(TAG, "SECURE_CHANNEL_ESTABLISHED");
  }
}
