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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.trust.GrantTrustResult;
import android.service.trust.TrustAgentService;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.R;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Service to provide TrustAgent functionality to the trusted device feature. */
public class TrustedDeviceAgentService extends TrustAgentService {

  private static final String TAG = "TrustedDeviceAgentService";

  /** Keep device in trusted state. */
  private static final long TRUST_DURATION_MS = 0L;

  private static final String RETRY_HANDLER_THREAD_NAME = "trusted_device_retry";

  private static final int MAX_RETRIES = 200;

  private static final Duration RETRY_DURATION = Duration.ofMillis(10);

  @VisibleForTesting protected Duration checkIhuLockScreenDelay;

  private final AtomicBoolean isManagingTrust = new AtomicBoolean(false);

  @VisibleForTesting protected HandlerThread retryThread;

  private Handler retryHandler;

  @VisibleForTesting ITrustedDeviceManager trustedDeviceManager;

  private int retries;

  private UserManager userManager;

  private KeyguardManager keyguardManager;

  private PowerManager powerManager;

  @SuppressLint("UnprotectedReceiver") // Broadcast is protected.
  @Override
  public void onCreate() {
    logd(TAG, "Starting TrustedDeviceAgentService for user" + ActivityManager.getCurrentUser());
    super.onCreate();
    userManager = (UserManager) getSystemService(Context.USER_SERVICE);
    keyguardManager = getSystemService(KeyguardManager.class);
    powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    TrustedDeviceEventLog.onTrustAgentStarted();
    registerReceiver(userUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    retryThread = new HandlerThread(RETRY_HANDLER_THREAD_NAME);
    retryThread.start();
    retryHandler = new Handler(retryThread.getLooper());
    checkIhuLockScreenDelay =
        Duration.ofSeconds(getResources().getInteger(R.integer.lock_screen_check_delay_in_second));
    bindToService();
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Destroying TrustedDeviceAgentService for user" + ActivityManager.getCurrentUser());
    boolean isDeviceSecure = keyguardManager != null && keyguardManager.isDeviceSecure();
    logd(TAG, "Device secure status: " + isDeviceSecure + ".");
    if (trustedDeviceManager != null) {
      try {
        trustedDeviceManager.clearTrustedDeviceAgentDelegate(
            trustedDeviceAgentDelegate,
            isDeviceSecure);
      } catch (RemoteException e) {
        loge(TAG, "Error while disconnecting from TrustedDeviceManager.");
      }
      unbindService(serviceConnection);
    }
    if (retryThread != null) {
      retryThread.quit();
    }
    unregisterReceiver(userUnlockedReceiver);
    unregisterReceiver(screenOnReceiver);
    super.onDestroy();
  }

  @Override
  public void onEscrowTokenAdded(byte[] token, long handle, UserHandle user) {
    super.onEscrowTokenAdded(token, handle, user);
    if (trustedDeviceManager == null) {
      loge(TAG, "Manager is null when escrow token was added. Ignoring.");
      return;
    }
    try {
      trustedDeviceManager.onEscrowTokenAdded(user.getIdentifier(), handle);
    } catch (RemoteException e) {
      loge(TAG, "Error while notifying that an escrow token was added.", e);
    }
  }

  @Override
  public void onEscrowTokenStateReceived(long handle, int tokenState) {
    super.onEscrowTokenStateReceived(handle, tokenState);
    if (trustedDeviceManager == null) {
      loge(TAG, "Manager was null when escrow token was received. Ignoring.");
      return;
    }
    if (tokenState == TrustAgentService.TOKEN_STATE_ACTIVE) {
      try {
        trustedDeviceManager.onEscrowTokenActivated(ActivityManager.getCurrentUser(), handle);
      } catch (RemoteException e) {
        loge(TAG, "Error while notifying that an escrow token was activated.", e);
      }
    }
  }

  @VisibleForTesting
  void maybeDismissLockscreen() {
    // Keyguard will ignore the dismissed request if the device is not in interactive mode, e.g.
    // dozing or asleep.
    if (!powerManager.isInteractive()) {
      logw(TAG, "Screen is not on when try to dismiss lock screen, waiting for screen on.");
      return;
    }

    if (!isManagingTrust.compareAndSet(true, false)) {
      logd(TAG, "User was unlocked before receiving an escrow token.");
      return;
    }

    logd(TAG, "Try dismissing the lockscreen.");
    // To avoid unreliable system lock status check impacting automated test metrics, invoking the
    // user unlocked event before granting trust.
    TrustedDeviceEventLog.onUserUnlocked();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      tryDismissingLockScreenApi33();
    } else {
      tryDismissingLockScreen();
    }
    setManagingTrust(false);
  }

  private void tryDismissingLockScreenApi33() {
    logd(TAG, "Grant trust with result callback.");
    grantTrust(
        "Granting trust from escrow token for user.",
        TRUST_DURATION_MS,
        FLAG_GRANT_TRUST_DISMISS_KEYGUARD,
        (result) -> {
          logd(TAG, "GrantTrust return with Result: " + result.getStatus() + ".");
          if (result.getStatus() == GrantTrustResult.STATUS_UNLOCKED_BY_GRANT) {
            notifyLockScreenDismissed();
          }
        });
  }

  private void tryDismissingLockScreen() {
    logd(TAG, "Grant trust without result callback.");
    if (isIhuUnlocked(false)) {
      logd(TAG, "No need to dismiss lock screen because none is present.");
      return;
    }
    grantTrust(
        "Granting trust from escrow token for user.",
        TRUST_DURATION_MS,
        FLAG_GRANT_TRUST_DISMISS_KEYGUARD);
    if (!isIhuUnlocked(true)) {
      logd(TAG, "Double check IHU locking status after " + checkIhuLockScreenDelay);
      retryHandler.postDelayed(
          () -> {
            if (!isIhuUnlocked(true)) {
              loge(TAG, "Failed to dismiss IHU lock screen.");
            }
          },
          checkIhuLockScreenDelay.toMillis());
    }
  }

  private boolean isIhuUnlocked(boolean notifyMobile) {
    if (keyguardManager != null && !keyguardManager.isDeviceLocked()) {
      logd(TAG, "IHU lock screen dismissed.");
      if (notifyMobile) {
        notifyLockScreenDismissed();
      }
      return true;
    }
    return false;
  }

  private void notifyLockScreenDismissed() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Manager was null when device was unlocked. Ignoring.");
      return;
    }
    try {
      trustedDeviceManager.onUserUnlocked();
    } catch (RemoteException e) {
      loge(TAG, "Error while notifying that the device was unlocked.", e);
    }
  }

  @VisibleForTesting
  boolean isUserUnlocked(int userId) {
    return userManager.isUserUnlocked(UserHandle.of(userId));
  }

  @VisibleForTesting
  void bindToService() {
    Intent intent = new Intent(this, TrustedDeviceManagerService.class);
    bindService(intent, serviceConnection, /* flags= */ 0);
  }

  @VisibleForTesting
  final void setupManager() {
    try {
      trustedDeviceManager.setTrustedDeviceAgentDelegate(trustedDeviceAgentDelegate);
      logd(TAG, "Successfully connected to TrustedDeviceManager.");
    } catch (RemoteException e) {
      loge(TAG, "Error while establishing connection to TrustedDeviceManager.", e);
    }
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          setupManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          trustedDeviceManager = null;
          bindToService();
        }
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
          logd(TAG, "Received an escrow token for user " + userId + ".");
          isManagingTrust.set(true);
          setManagingTrust(true);
          if (isUserUnlocked(userId)) {
            logd(TAG, "User was already unlocked when token was received.");
            maybeDismissLockscreen();
            return;
          }
          logd(TAG, "Unlocking user with token.");
          TrustedDeviceAgentService.this.unlockUserWithToken(handle, token, UserHandle.of(userId));
        }

        @Override
        public void removeEscrowToken(long handle, int userId) {
          logd(TAG, "Removing escrow token for user " + userId + ".");
          TrustedDeviceAgentService.this.removeEscrowToken(handle, UserHandle.of(userId));
        }
      };

  private final BroadcastReceiver userUnlockedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          logd(TAG, "User unlocked; try to dismiss the lock screen.");
          maybeDismissLockscreen();
        }
      };

  /**
   * Indicating the lock screen is ready to be dismissed.
   *
   * <p>Dismissing lock screen without screen on will fail silently.
   */
  private final BroadcastReceiver screenOnReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          logd(TAG, "Screen on; device entered interactive mode.");
          if (isManagingTrust.get()) {
            continueDismissLockScreen();
          } else {
            sendUnlockRequest();
          }
        }
      };

  private void continueDismissLockScreen() {
    if (!isUserUnlocked(ActivityManager.getCurrentUser())) {
      logd(TAG, "User hasn't been unlocked. Ignored.");
      return;
    }
    maybeDismissLockscreen();
  }

  private void sendUnlockRequest() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Trusted Device manager is null, cannot send unlock request.");
      return;
    }
    try {
      trustedDeviceManager.sendUnlockRequest();
      logd(TAG, "Successfully initiated sending unlock request by TrustedDeviceManager.");
    } catch (RemoteException e) {
      loge(TAG, "Error while establishing connection to TrustedDeviceManager.", e);
    }
  }
}
