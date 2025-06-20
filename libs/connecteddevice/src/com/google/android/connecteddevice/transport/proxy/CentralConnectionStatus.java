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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logi;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Central;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.NotifyCentralSubscriptionMessage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Maintains the connection status by tracking the characteristic subscriptions of a GATT central.
 *
 * <p>A central may unsubscribe for several milliseconds before resubscribing, so we need to wait
 * after subscription is removed before declaring that the central disconnected.
 *
 * <p>Assumes each unsubscription message has a matching prior subscription message.
 *
 * <p>The subscribed GATT characteristic is not tracked - only the central UUID is used.
 */
class CentralConnectionStatus {
  private static final String TAG = "CentralConnectionStatus";
  private static final long NO_SUBSCRIPTION_TIMEOUT = 2000;

  private final Object lock = new Object();
  private final ScheduledExecutorService scheduler;
  private Central central;

  @GuardedBy("lock")
  private ScheduledFuture<?> disconnectFuture;

  @GuardedBy("lock")
  private int subscriptionCounter = 0;
  // isConnected is not equivalent to subscriptionCounter being 0. A connected central may briefly
  // unsubscribe from a characteristic before re-subscribing to it. isConnected is used to maintain
  // the connected status until the scheduled disconnect task is executed.
  @GuardedBy("lock")
  private boolean isConnected = false;

  private Callback callback;

  public CentralConnectionStatus(
      @NonNull Central central, @NonNull ScheduledExecutorService scheduler) {
    this.central = central;
    this.scheduler = scheduler;
  }

  /**
   * Tracks the subscribed central.
   *
   * @return true if the subscription is properly handled; false otherwise.
   */
  public boolean handleSubscriptionEvent(@NonNull NotifyCentralSubscriptionMessage subscription) {
    if (!subscription.getCentral().getIdentifier().equals(central.getIdentifier())) {
      loge(
          TAG,
          "Unmatched centrals. Internal: "
              + central.getIdentifier()
              + "; subscribring: "
              + subscription.getCentral().getIdentifier()
              + ". Ignored");
      return false;
    }
    switch (subscription.getEvent()) {
      case SUBSCRIBED:
        incrementSubscription();
        return true;
      case UNSUBSCRIBED:
        decrementSubscription();
        return true;
      default:
        loge(TAG, "Unrecognized subscription event: " + subscription.getEvent().getNumber());
        return false;
    }
  }

  private void incrementSubscription() {
    synchronized (lock) {
      subscriptionCounter += 1;
      logd(
          TAG,
          "Incremented subscription to "
              + subscriptionCounter
              + " for central "
              + central.getIdentifier());

      cancelDisconnectTaskLocked();

      if (!isConnected) {
        logi(TAG, "CentralConnectionStatus: central connected: " + central.getIdentifier());
        isConnected = true;
        if (callback != null) {
          callback.onCentralConnected(central);
        }
      }
    }
  }

  private void decrementSubscription() {
    synchronized (lock) {
      if (!isConnected) {
        loge(TAG, "Received unsubscription message while not connected. Ignored.");
        return;
      }
      subscriptionCounter -= 1;
      logi(
          TAG,
          "Decremented subscription to: "
              + subscriptionCounter
              + " for central "
              + central.getIdentifier());

      if (subscriptionCounter == 0) {
        cancelDisconnectTaskLocked();
        disconnectFuture =
            scheduler.schedule(disconnectTask, NO_SUBSCRIPTION_TIMEOUT, MILLISECONDS);
      }
    }
  }

  @GuardedBy("lock")
  private void cancelDisconnectTaskLocked() {
    if (disconnectFuture == null) {
      logi(TAG, "Cached disconnectFuture is null. Nothing to cancel.");
      return;
    }

    if (!disconnectFuture.cancel(/* mayInterruptIfRunning= */ true)) {
      loge(TAG, "Could not reschedule disconnect task. Reset.");
      resetLocked();
      return;
    }
    disconnectFuture = null;
  }

  private final Runnable disconnectTask =
      () -> {
        synchronized (lock) {
          logd(
              TAG,
              "Disconnecting. isConnected: " + isConnected + "; counter: " + subscriptionCounter);
          if (isConnected && subscriptionCounter == 0) {
            isConnected = false;
            if (callback != null) {
              logi(
                  TAG, "CentralConnectionStatus: central disconnected: " + central.getIdentifier());
              callback.onCentralDisconnected(central);
            }
            resetLocked();
          }
        }
      };

  @GuardedBy("lock")
  private void resetLocked() {
    logi(TAG, "Resetting connection status for: " + central.getIdentifier());
    isConnected = false;
    subscriptionCounter = 0;
    disconnectFuture = null;
  }

  public boolean isConnected() {
    synchronized (lock) {
      return isConnected;
    }
  }

  public void setCallback(@NonNull Callback callback) {
    this.callback = callback;
  }

  public void clearCallback() {
    callback = null;
  }

  interface Callback {
    void onCentralConnected(Central central);

    void onCentralDisconnected(Central central);
  }
}
