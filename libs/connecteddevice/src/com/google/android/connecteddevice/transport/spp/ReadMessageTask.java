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

package com.google.android.connecteddevice.transport.spp;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This task runs during a connection with a remote device. It handles the read of all incoming
 * data.
 */
class ReadMessageTask implements Runnable {
  private static final String TAG = "ReadMessageTask";
  private final InputStream inputStream;
  private final Callback callback;
  private final Executor callbackExecutor;
  private final AtomicBoolean isCanceled = new AtomicBoolean(false);
  private final byte[] sizeBuffer = new byte[SppManager.LENGTH_BYTES_SIZE];

  ReadMessageTask(
      @NonNull InputStream inputStream,
      @NonNull Callback callback,
      @NonNull Executor callbackExecutor) {
    this.inputStream = inputStream;
    this.callback = callback;
    this.callbackExecutor = callbackExecutor;
  }

  @Override
  public void run() {
    logi(TAG, "Begin task: started listening to incoming messages.");
    // Keep listening to the InputStream when task started.
    while (!isCanceled.get()) {
      if (!readData(inputStream, sizeBuffer)) {
        cancel();
        callbackExecutor.execute(callback::onMessageReadError);
        break;
      }
      int messageLength = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
      byte[] dataBuffer = new byte[messageLength];
      if (!readData(inputStream, dataBuffer)) {
        cancel();
        callbackExecutor.execute(callback::onMessageReadError);
        break;
      }

      logd(TAG, "Received raw bytes from remote device of length: " + messageLength);
      callbackExecutor.execute(() -> callback.onMessageReceived(dataBuffer));
    }
  }

  /**
   * Read data from {@code inputStream} until the {@code buffer} is full and returns {@code true} if
   * the operation succeeded.
   */
  @VisibleForTesting
  boolean readData(InputStream inputStream, byte[] buffer) {
    int offset = 0;
    int bytesToRead = buffer.length;
    int bytesRead;
    while (bytesToRead > 0) {
      try {
        bytesRead = inputStream.read(buffer, offset, bytesToRead);
      } catch (IOException e) {
        logw(TAG, "Encountered an exception when listening for incoming message.");
        return false;
      }
      if (bytesRead == -1) {
        loge(TAG, "EOF when reading data from input stream.");
        return false;
      }
      offset += bytesRead;
      bytesToRead -= bytesRead;
    }
    return true;
  }

  public void cancel() {
    isCanceled.set(true);
  }

  /** Interface to be called when there are {@code ReadMessageTask} related events. */
  interface Callback {

    /**
     * Triggered when a completed message is received from input stream.
     *
     * @param message Message received from remote device.
     */
    void onMessageReceived(@NonNull byte[] message);

    /** Triggered when failed to read message from remote device. */
    void onMessageReadError();
  }
}
