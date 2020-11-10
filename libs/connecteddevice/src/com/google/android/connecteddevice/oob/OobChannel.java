package com.google.android.connecteddevice.oob;

import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.OobEligibleDevice;

/**
 * An interface for handling out of band data exchange. This interface should be implemented for
 * every out of band channel that is supported in device association.
 *
 * <p>Usage is:
 *
 * <pre>
 *     1. Define success and failure responses in {@link Callback}
 *     2. Call {@link OobChannel#completeOobDataExchange(OobEligibleDevice, Callback)}
 * </pre>
 */
public interface OobChannel {
  /**
   * Exchange out of band data with a remote device. This must be done prior to the start of the
   * association with that device.
   *
   * @param device The remote device to exchange out of band data with
   */
  void completeOobDataExchange(@NonNull OobEligibleDevice device, @NonNull Callback callback);

  /**
   * Send raw data over the out of band channel
   *
   * @param oobData to be sent
   */
  void sendOobData(@NonNull byte[] oobData);

  /** Interrupt the current data exchange and prevent callbacks from being issued. */
  void interrupt();

  /** Callbacks for {@link OobChannel#completeOobDataExchange(OobEligibleDevice, Callback)} */
  interface Callback {
    /**
     * Called when {@link OobChannel#completeOobDataExchange(OobEligibleDevice, Callback)} finishes
     * successfully.
     */
    void onOobExchangeSuccess();

    /**
     * Called when {@link OobChannel#completeOobDataExchange(OobEligibleDevice, Callback)} fails.
     */
    void onOobExchangeFailure();
  }
}
