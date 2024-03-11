/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.KeyguardManager.WeakEscrowTokenRemovedListener;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandle;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabase;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceDatabaseProvider;
import com.google.android.connecteddevice.trust.storage.TrustedDeviceEntity;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manager for the feature of unlocking the head unit with a user's trusted device for T+.
 *
 * <p>This manager uses weak escrow token associated with the a user's trusted device to unlock the
 * head unit.
 */
public class TrustedDeviceManagerApi33 extends TrustedDeviceManager {
  private static final String TAG = "TrustedDeviceManagerApi33";

  private final WeakEscrowTokenRemovedListener listener = this::onWeakEscrowTokenRemoved;
  private final KeyguardManager keyguardManager;

  TrustedDeviceManagerApi33(@NonNull Context context) {
    this(
        context.getSystemService(KeyguardManager.class),
        TrustedDeviceDatabaseProvider.get(context),
        new TrustedDeviceFeature(context),
        /* databaseExecutor= */ Executors.newSingleThreadExecutor(),
        /* remoteCallbackExecutor= */ Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  TrustedDeviceManagerApi33(
      @NonNull KeyguardManager keyguardManager,
      @NonNull TrustedDeviceDatabase database,
      @NonNull TrustedDeviceFeature trustedDeviceFeature,
      @NonNull Executor databaseExecutor,
      @NonNull Executor remoteCallbackExecutor) {
    super(database, trustedDeviceFeature, databaseExecutor, remoteCallbackExecutor);
    this.keyguardManager = keyguardManager;
    this.keyguardManager.registerWeakEscrowTokenRemovedListener(databaseExecutor, listener);
  }

  @Override
  public void processEnrollment(boolean isDeviceSecure) {
    PendingToken token = getPendingToken();
    if (token == null) {
      loge(TAG, "No pending token can be added.");
      return;
    }
    if (!isDeviceSecure) {
      logd(TAG, "Processing enrollment on insecure device.");
      // For API 33+, instead of using escrow token, we use weak escrow token which will be
      // activated instantly if the device is not secure. Therefore in the enrolling insecure device
      // flow, no credential confirmation page will be shown because weak escrow token is added and
      // activated before the user sets up their credential.
      isCredentialVerified = true;
      addWeakEscrowToken(token, /* isDeviceSecure= */ false);
      return;
    }
    logd(TAG, "Processing enrollment on secure device.");
    if (isWaitingForCredentialSetUp.getAndSet(false)) {
      if (pendingHandle == null) {
        loge(TAG, "No pending handle can be added.");
        return;
      }
      onWeakEscrowTokenActivated(
          pendingHandle.userId, pendingHandle.handle, /* isDeviceSecure= */ true);
      return;
    }
    addWeakEscrowToken(token, /* isDeviceSecure= */ true);
  }

  @Override
  void setTrustedDeviceAgentDelegateInternal(ITrustedDeviceAgentDelegate trustedAgentDelegate) {
    logd(TAG, "setTrustedDeviceAgentDelegateInternal");
    this.trustAgentDelegate = trustedAgentDelegate;
    int userId = ActivityManager.getCurrentUser();
    cleanUpInvalidTrustedDevices(userId);
    maybeResumeUnlocking();
  }

  @Override
  protected boolean removeEscrowToken(long handle, int userId) {
    logd(TAG, "Removing weak token for user " + userId + ".");
    return keyguardManager.removeWeakEscrowToken(handle, UserHandle.of(userId));
  }

  @Override
  void cleanup() {
    keyguardManager.unregisterWeakEscrowTokenRemovedListener(listener);
    super.cleanup();
  }

  @WorkerThread
  private void onWeakEscrowTokenRemoved(long handle, UserHandle user) {
    int userId = user.getIdentifier();
    logd(TAG, "A weak escrow token has been removed for user " + user + ".");
    List<TrustedDeviceEntity> entities =
        getTrustedDeviceDatabase().getValidTrustedDevicesForUser(userId);
    for (TrustedDeviceEntity entity : entities) {
      if (entity.handle == handle) {
        invalidateTrustedDevice(entity);
        getTrustedDeviceDatabase().removeTrustedDevice(entity);
        break;
      }
    }
  }

  private void addWeakEscrowToken(@NonNull PendingToken token, boolean isDeviceSecure) {
    if (token.userId != ActivityManager.getCurrentUser()) {
      loge(TAG, "Received token from backgrounded user. Abort enrollment.");
      notifyEnrollmentError(TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN);
      return;
    }
    logd(TAG, "Adding weak escrow token.");
    long addedHandle =
        keyguardManager.addWeakEscrowToken(
            token.escrowToken,
            UserHandle.of(token.userId),
            Runnable::run,
            (handle, user) ->
                onWeakEscrowTokenActivated(user.getIdentifier(), handle, isDeviceSecure));
    pendingHandle = new PendingHandle(token.userId, addedHandle);
    if (!isDeviceSecure) {
      return;
    }
    notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onValidateCredentialsRequest();
          } catch (RemoteException e) {
            loge(TAG, "Error while requesting credential validation.", e);
          }
        });
  }

  private void onWeakEscrowTokenActivated(int userId, long handle, boolean isDeviceSecure) {
    logd(TAG, "Weak Escrow token has been successfully added.");
    if (isDeviceSecure) {
      onEscrowTokenActivated(userId, handle);
      return;
    }
    isWaitingForCredentialSetUp.set(true);
    notifyRemoteEnrollmentCallbacks(
        callback -> {
          try {
            callback.onSecureDeviceRequest();
          } catch (RemoteException e) {
            loge(TAG, "Error while requesting secured device.", e);
          }
        });
  }
}
