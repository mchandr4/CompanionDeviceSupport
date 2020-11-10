package com.google.android.connecteddevice.notificationmsg;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg;
import com.google.android.connecteddevice.notificationmsg.proto.NotificationMsg.PhoneToCarMessage;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * An implementation of {@link RemoteFeature} that registers the NotificationMsg feature and handles
 * the import and deletion of text messages sent from connected {@link ConnectedDevice}s.
 *
 * <p>The communication data this feature is handling will be cleared every time an error or
 * disconnect happens, or the feature is stopped.
 */
public class NotificationMsgFeature extends RemoteFeature {
  private static final String TAG = "NotificationMsgFeature";

  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("b2337f58-18ff-4f92-a0cf-4df63ab2c889");

  private final NotificationMsgDelegate notificationMsgDelegate;
  private ConnectedDevice secureDeviceForActiveUser;

  NotificationMsgFeature(
      @NonNull Context context, @NonNull NotificationMsgDelegate notificationMsgDelegate) {
    super(context, FEATURE_ID);
    this.notificationMsgDelegate = notificationMsgDelegate;
  }

  @Override
  public void start() {
    // For safety in case something went wrong and the feature couldn't terminate correctly we
    // clear all notifications and local data on start of the feature.
    notificationMsgDelegate.cleanupMessagesAndNotifications(key -> true);
    super.start();
  }

  @Override
  public void stop() {
    // Erase all the notifications and local data, so that no user data stays on the device
    // after the feature is stopped.
    notificationMsgDelegate.onDestroy();
    super.stop();
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (secureDeviceForActiveUser == null
        && device.hasSecureChannel()
        && device.isAssociatedWithActiveUser()) {
      logw(
          TAG,
          "stored secure device is null, but message was received on a"
              + " secure device!"
              + device);
      secureDeviceForActiveUser = device;
    }
    if (!isSecureDeviceForActiveUser(device.getDeviceId())) {
      logd(TAG, device + ": skipped message from unsecure device");
      return;
    }
    logd(TAG, device + ": received message over secure channel");
    try {
      PhoneToCarMessage phoneToCarMessage =
          NotificationMsg.PhoneToCarMessage.parseFrom(
              message, ExtensionRegistryLite.getEmptyRegistry());
      notificationMsgDelegate.onMessageReceived(device, phoneToCarMessage);
    } catch (InvalidProtocolBufferException e) {
      loge(TAG, device + ": error parsing notification msg protobuf", e);
    }
  }

  @Override
  protected void onSecureChannelEstablished(ConnectedDevice device) {
    logd(TAG, "received secure device: " + device);
    secureDeviceForActiveUser = device;
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    if (!isSecureDeviceForActiveUser(device.getDeviceId())) {
      return;
    }
    loge(TAG, device + ": received device error " + error, null);
  }

  @Override
  protected void onDeviceDisconnected(ConnectedDevice device) {
    if (!isSecureDeviceForActiveUser(device.getDeviceId())) {
      return;
    }
    logw(TAG, device + ": disconnected");
    notificationMsgDelegate.onDeviceDisconnected(device.getDeviceId());
    secureDeviceForActiveUser = null;
  }

  protected void sendData(@NonNull String deviceId, @NonNull byte[] message) {
    if (secureDeviceForActiveUser == null || !isSecureDeviceForActiveUser(deviceId)) {
      logw(TAG, "Could not send message to device: " + deviceId);
      return;
    }

    sendMessageSecurely(deviceId, message);
  }

  @Override
  protected void onMessageFailedToSend(String deviceId, byte[] message, boolean isTransient) {
    // TODO (b/144924164): Notify Delegate action request failed.
  }

  private boolean isSecureDeviceForActiveUser(String deviceId) {
    return (secureDeviceForActiveUser != null)
        && secureDeviceForActiveUser.getDeviceId().equals(deviceId);
  }
}
