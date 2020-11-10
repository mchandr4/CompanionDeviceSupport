package com.google.android.connecteddevice.trust;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Constants for trusted device feature. */
public class TrustedDeviceConstants {

  private TrustedDeviceConstants() {}

  /** Intent extra key for a boolean signalling a new escrow token is being enrolled. */
  public static final String INTENT_EXTRA_ENROLL_NEW_TOKEN = "trusted.device.enrolling.token";

  /** Intent action used to start an activity for Trusted Device. */
  public static final String INTENT_ACTION_TRUSTED_DEVICE_SETTING =
      "com.google.android.connecteddevice.trust.TRUSTED_DEVICE_ACTIVITY";

  /** Errors that may happen in trusted device enrollment. */
  @IntDef(
      value = {
        TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN,
        TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED,
        TRUSTED_DEVICE_ERROR_UNKNOWN
      })
  @Retention(RetentionPolicy.SOURCE)
  public @interface TrustedDeviceError {}

  public static final int TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN = 0;
  public static final int TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED = 1;
  public static final int TRUSTED_DEVICE_ERROR_UNKNOWN = 2;
}
