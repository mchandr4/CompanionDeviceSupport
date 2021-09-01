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

package com.google.android.connecteddevice.connection.ble;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Class that manages BLE scanning operations. */
public class BleCentralManager {

  private static final String TAG = "BleCentralManager";

  private static final int RETRY_LIMIT = 5;

  private static final int RETRY_INTERVAL_MS = 1000;

  private final Context context;

  private final Handler handler;

  private final AtomicInteger scannerState = new AtomicInteger(STOPPED);

  private final BluetoothManager bluetoothManager;

  private List<ScanFilter> scanFilters;

  private ScanSettings scanSettings;

  private ScanCallback scanCallback;

  private BluetoothLeScanner scanner;

  private int scannerStartCount = 0;


  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STOPPED, STARTED, SCANNING})
  private @interface ScannerState {}

  private static final int STOPPED = 0;
  private static final int STARTED = 1;
  private static final int SCANNING = 2;

  public BleCentralManager(@NonNull Context context) {
    this.context = context;
    handler = new Handler(context.getMainLooper());
    bluetoothManager = context.getSystemService(BluetoothManager.class);
  }

  /**
   * Start the BLE scanning process.
   *
   * @param filters Optional list of {@link ScanFilter}s to apply to scan results.
   * @param settings {@link ScanSettings} to apply to scanner.
   * @param callback {@link ScanCallback} for scan events.
   */
  public void startScanning(
      @Nullable List<ScanFilter> filters,
      @NonNull ScanSettings settings,
      @NonNull ScanCallback callback) {
    if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
      loge(TAG, "Attempted start scanning, but system does not support BLE. Ignoring");
      return;
    }
    logd(TAG, "Request received to start scanning.");
    scannerStartCount = 0;
    scanFilters = filters;
    scanSettings = settings;
    scanCallback = callback;
    updateScannerState(STARTED);
    startScanningInternally();
  }

  /** Stop the scanner */
  public void stopScanning() {
    logd(TAG, "Attempting to stop scanning");
    if (scanner != null) {
      scanner.stopScan(internalScanCallback);
    }
    scanCallback = null;
    updateScannerState(STOPPED);
  }

  /** Returns {@code true} if currently scanning, {@code false} otherwise. */
  public boolean isScanning() {
    return scannerState.get() == SCANNING;
  }

  /** Clean up the scanning process. */
  public void cleanup() {
    if (isScanning()) {
      stopScanning();
    }
  }

  private void startScanningInternally() {
    logd(TAG, "Attempting to start scanning");
    BluetoothAdapter adapter = bluetoothManager.getAdapter();
    if (scanner == null && adapter != null) {
      scanner = adapter.getBluetoothLeScanner();
    }
    if (scanner != null) {
      scanner.startScan(scanFilters, scanSettings, internalScanCallback);
      updateScannerState(SCANNING);
    } else {
      handler.postDelayed(
          () -> {
            // Keep trying
            logd(TAG, "Scanner unavailable. Trying again.");
            startScanningInternally();
          },
          RETRY_INTERVAL_MS);
    }
  }

  private void updateScannerState(@ScannerState int newState) {
    scannerState.set(newState);
  }

  private final ScanCallback internalScanCallback =
      new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
          if (scanCallback != null) {
            scanCallback.onScanResult(callbackType, result);
          }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
          logd(TAG, "Batch scan found " + results.size() + " results.");
          if (scanCallback != null) {
            scanCallback.onBatchScanResults(results);
          }
        }

        @Override
        public void onScanFailed(int errorCode) {
          if (scannerStartCount >= RETRY_LIMIT) {
            loge(TAG, "Cannot start BLE Scanner. Scanning Retry count: " + scannerStartCount);
            if (scanCallback != null) {
              scanCallback.onScanFailed(errorCode);
            }
            return;
          }

          scannerStartCount++;
          logw(
              TAG,
              "BLE Scanner failed to start. Error: " + errorCode + " Retry: " + scannerStartCount);
          switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED:
              // Scanner already started. Do nothing.
              break;
            case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
            case SCAN_FAILED_INTERNAL_ERROR:
              handler.postDelayed(
                  BleCentralManager.this::startScanningInternally, RETRY_INTERVAL_MS);
              break;
            default:
              // Ignore other codes.
          }
        }
      };
}
