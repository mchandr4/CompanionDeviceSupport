package com.google.android.connecteddevice.trust;

import android.content.Context;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;

/** Feature wrapper for trusted device. */
class TrustedDeviceFeature extends RemoteFeature {

  private static final String TAG = "TrustedDeviceFeature";

  private static final ParcelUuid FEATURE_ID =
      ParcelUuid.fromString("85dff28b-3036-4662-bb22-baa7f898dc47");

  private Callback callback;

  private AssociatedDeviceCallback associatedDeviceCallback;

  TrustedDeviceFeature(@NonNull Context context) {
    super(context, FEATURE_ID);
  }

  /** Set a {@link Callback} for events from the device. Set {@code null} to clear. */
  void setCallback(@Nullable Callback callback) {
    this.callback = callback;
  }

  /** Set an {@link AssociatedDeviceCallback} for associated device events. */
  void setAssociatedDeviceCallback(@NonNull AssociatedDeviceCallback callback) {
    associatedDeviceCallback = callback;
  }

  /** Clear the callback fo associated device events. */
  void clearAssociatedDeviceCallback() {
    associatedDeviceCallback = null;
  }

  @Override
  protected void onMessageReceived(ConnectedDevice device, byte[] message) {
    if (callback != null) {
      callback.onMessageReceived(device, message);
    }
  }

  @Override
  protected void onDeviceError(ConnectedDevice device, int error) {
    if (callback != null) {
      callback.onDeviceError(device, error);
    }
  }

  @Override
  protected void onAssociatedDeviceAdded(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceAdded(device);
    }
  }

  @Override
  protected void onAssociatedDeviceRemoved(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceRemoved(device);
    }
  }

  @Override
  protected void onAssociatedDeviceUpdated(AssociatedDevice device) {
    if (associatedDeviceCallback != null) {
      associatedDeviceCallback.onAssociatedDeviceUpdated(device);
    }
  }

  interface Callback {
    /** Called when a new {@link byte[]} message is received for this feature. */
    void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message);

    /** Called when an error has occurred with the connection. */
    void onDeviceError(@NonNull ConnectedDevice device, int error);
  }

  interface AssociatedDeviceCallback {
    /** Called when a new {@link AssociatedDevice} is added for the given user. */
    void onAssociatedDeviceAdded(@NonNull AssociatedDevice device);

    /** Called when an {@link AssociatedDevice} is removed for the given user. */
    void onAssociatedDeviceRemoved(AssociatedDevice device);

    /** Called when an {@link AssociatedDevice} is updated for the given user. */
    void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device);
  }
}
