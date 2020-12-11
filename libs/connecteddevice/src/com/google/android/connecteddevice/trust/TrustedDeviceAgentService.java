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

package com.google.android.connecteddevice.trust;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.trust.TrustAgentService;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import java.time.Duration;

/** Service to provide TrustAgent functionality to the trusted device feature. */
public class TrustedDeviceAgentService extends TrustAgentService {

  private static final String TAG = "TrustedDeviceAgentService";

  /** Keep device in trusted state. */
  private static final long TRUST_DURATION_MS = 0L;

  private static final String RETRY_HANDLER_THREAD_NAME = "trusted_device_retry";

  private static final int MAX_RETRIES = 200;

  private static final Duration RETRY_DURATION = Duration.ofMillis(10);

  private HandlerThread retryThread;

  private Handler retryHandler;

  private ITrustedDeviceManager trustedDeviceManager;

  private int retries;

  @Override
  public void onCreate() {
    super.onCreate();
    logd(TAG, "Starting trust agent service.");
    TrustedDeviceEventLog.onTrustAgentStarted();
    Intent intent = new Intent(this, TrustedDeviceManagerService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    retryThread = new HandlerThread(RETRY_HANDLER_THREAD_NAME);
    retryThread.start();
    retryHandler = new Handler(retryThread.getLooper());
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Destroying trust agent service.");
    try {
      trustedDeviceManager.clearTrustedDeviceAgentDelegate(trustedDeviceAgentDelegate);
    } catch (RemoteException e) {
      loge(TAG, "Error while disconnecting from TrustedDeviceManager.");
    }
    unbindService(serviceConnection);
    if (retryThread != null) {
      retryThread.quit();
    }
    super.onDestroy();
  }

  @Override
  public void onEscrowTokenAdded(byte[] token, long handle, UserHandle user) {
    super.onEscrowTokenAdded(token, handle, user);
    try {
      trustedDeviceManager.onEscrowTokenAdded(user.getIdentifier(), handle);
    } catch (RemoteException e) {
      loge(TAG, "Error while notifying that an escrow token was added.", e);
    }
  }

  @Override
  public void onEscrowTokenStateReceived(long handle, int tokenState) {
    super.onEscrowTokenStateReceived(handle, tokenState);
    if (tokenState == TrustAgentService.TOKEN_STATE_ACTIVE) {
      try {
        trustedDeviceManager.onEscrowTokenActivated(ActivityManager.getCurrentUser(), handle);
      } catch (RemoteException e) {
        loge(TAG, "Error while notifying that an escrow token was activated.", e);
      }
    }
  }

  @Override
  public void onDeviceUnlocked() {
    super.onDeviceUnlocked();
    TrustedDeviceEventLog.onUserUnlocked();
    try {
      trustedDeviceManager.onUserUnlocked();
    } catch (RemoteException e) {
      loge(TAG, "Error while notifying that the device was unlocked.", e);
    }
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          try {
            trustedDeviceManager.setTrustedDeviceAgentDelegate(trustedDeviceAgentDelegate);
            logd(TAG, "Successfully connected to TrustedDeviceManager.");
          } catch (RemoteException e) {
            loge(TAG, "Error while establishing connection to TrustedDeviceManager.", e);
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
      };

  private final ITrustedDeviceAgentDelegate trustedDeviceAgentDelegate =
      new ITrustedDeviceAgentDelegate.Stub() {

        @Override
        public void addEscrowToken(byte[] token, int userId) {
          logd(TAG, "Adding new escrow token for user " + userId + ".");
          try {
            TrustedDeviceAgentService.this.addEscrowToken(token, UserHandle.of(userId));
            return;
          } catch (IllegalStateException e) {
            if (++retries > MAX_RETRIES) {
              loge(
                  TAG,
                  "Maximum number of retries for adding an escrow token has "
                      + "been exceeded. Aborting.",
                  e);
              return;
            }
          }

          logw(TAG, "Trust agent has not been bound to yet. Retry #" + retries + ".");
          retryHandler.postDelayed(() -> addEscrowToken(token, userId), RETRY_DURATION.toMillis());
        }

        @Override
        public void unlockUserWithToken(byte[] token, long handle, int userId) {
          setManagingTrust(true);
          TrustedDeviceAgentService.this.unlockUserWithToken(handle, token, UserHandle.of(userId));
          grantTrust(
              "Granting trust from escrow token for user " + userId + ".",
              TRUST_DURATION_MS,
              FLAG_GRANT_TRUST_DISMISS_KEYGUARD);
        }

        @Override
        public void removeEscrowToken(long handle, int userId) {
          logd(TAG, "Removing escrow token for user " + userId + ".");
          TrustedDeviceAgentService.this.removeEscrowToken(handle, UserHandle.of(userId));
        }
      };
}
