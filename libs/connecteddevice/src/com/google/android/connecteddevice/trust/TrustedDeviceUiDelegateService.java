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

import static com.google.android.connecteddevice.trust.TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.connecteddevice.service.MetaDataService;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationRequestListener;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;

/** Service to provide UI functionality to the {@link TrustedDeviceManagerService}. */
public class TrustedDeviceUiDelegateService extends MetaDataService {
  private static final String TAG = "TrustedDeviceDelegateService";
  private static final String CHANNEL_ID = "trusteddevice_notification_channel";
  private static final int ENROLLMENT_NOTIFICATION_ID = 0;

  /** {@code String} Name for the notification channel. */
  private static final String META_NOTIFICATION_CHANNEL_NAME =
      "com.google.android.connecteddevice.trust.notification_channel_name";
  /** {@code Drawable} Icon for the enrollment notification. */
  private static final String META_ENROLLMENT_NOTIFICATION_ICON =
      "com.google.android.connecteddevice.trust.enrollment_notification_icon";
  /** {@code String} Title for the enrollment notification. */
  private static final String META_ENROLLMENT_NOTIFICATION_TITLE =
      "com.google.android.connecteddevice.trust.enrollment_notification_title";
  /** {@code String} Content for the enrollment notification. */
  private static final String META_ENROLLMENT_NOTIFICATION_CONTENT =
      "com.google.android.connecteddevice.trust.enrollment_notification_content";
  /** {@code Color} Color for the enrollment notification. */
  private static final String META_ENROLLMENT_NOTIFICATION_COLOR =
      "com.google.android.connecteddevice.trust.enrollment_notification_color";

  private NotificationManager notificationManager;
  private ITrustedDeviceManager trustedDeviceManager;

  @Override
  public void onCreate() {
    super.onCreate();
    Intent intent = new Intent(this, TrustedDeviceManagerService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    notificationManager =
        (NotificationManager) getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
    String channelName = requireMetaString(META_NOTIFICATION_CHANNEL_NAME);
    NotificationChannel channel =
        new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
    notificationManager.createNotificationChannel(channel);
    logd(TAG, "Service created.");
  }

  @Override
  public void onDestroy() {
    logd(TAG, "Service was destroyed.");
    unregisterCallbacks();
    unbindService(serviceConnection);
    super.onDestroy();
  }

  private void showEnrollmentNotification() {
    if (notificationManager == null) {
      loge(TAG, "Failed to start enrollment notification, notification manager is null.");
      return;
    }
    Intent enrollmentIntent =
        new Intent()
            .setAction(INTENT_ACTION_TRUSTED_DEVICE_SETTING)
            .putExtra(TrustedDeviceConstants.INTENT_EXTRA_ENROLL_NEW_TOKEN, true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    @SuppressWarnings("PendingIntentMutability") // SaferPendingIntent is not available in AOSP
    PendingIntent pendingIntent =
        PendingIntent.getActivity(
            this, /* requestCode = */ 0, enrollmentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                | PendingIntent.FLAG_ONE_SHOT);
    Notification notification =
        new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(requireMetaResourceId(META_ENROLLMENT_NOTIFICATION_ICON))
            .setColor(
                ContextCompat.getColor(
                    getBaseContext(), requireMetaResourceId(META_ENROLLMENT_NOTIFICATION_COLOR)))
            .setContentTitle(requireMetaString(META_ENROLLMENT_NOTIFICATION_TITLE))
            .setContentText(requireMetaString(META_ENROLLMENT_NOTIFICATION_CONTENT))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
    notificationManager.notify(/* tag = */ null, ENROLLMENT_NOTIFICATION_ID, notification);
  }

  private void registerCallbacks() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Failed to register callbacks, service not connected.");
      return;
    }
    try {
      trustedDeviceManager.registerTrustedDeviceEnrollmentNotificationRequestListener(
          enrollmentNotificationListener);
    } catch (RemoteException e) {
      loge(TAG, "Failed to register enrollment callback.", e);
    }
  }

  private void unregisterCallbacks() {
    if (trustedDeviceManager == null) {
      loge(TAG, "Failed to unregister callbacks, service not connected.");
      return;
    }
    try {
      trustedDeviceManager.unregisterTrustedDeviceEnrollmentNotificationRequestListener(
          enrollmentNotificationListener);
    } catch (RemoteException e) {
      loge(TAG, "Failed to unregister callbacks.", e);
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          registerCallbacks();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          trustedDeviceManager = null;
        }
      };

  private final IOnTrustedDeviceEnrollmentNotificationRequestListener
      enrollmentNotificationListener =
          new IOnTrustedDeviceEnrollmentNotificationRequestListener.Stub() {
            @Override
            public void onTrustedDeviceEnrollmentNotificationRequest() {
              showEnrollmentNotification();
            }
          };
}
