package com.google.android.connecteddevice.trust.api;

import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationRequestListener;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.ConnectedDevice;

/**
 * Manager of trusted devices with the car to be used by any service/activity that needs to interact
 * with trusted devices.
 */
interface ITrustedDeviceManager {

    /** Indicate the escrow token has been added for a user and corresponding handle. */
    void onEscrowTokenAdded(in int userId, in long handle);

    /** Indicate the escrow token has been activated for a user and corresponding handle. */
    void onEscrowTokenActivated(in int userId, in long handle);

    /** Indicate the device has been unlocked for current user. */
    void onUserUnlocked();

    /** Register a new callback for trusted device events. */
    void registerTrustedDeviceCallback(in ITrustedDeviceCallback callback);

    /** Remove a previously registered callback. */
    void unregisterTrustedDeviceCallback(in ITrustedDeviceCallback callback);

    /** Register a new callback for enrollment triggered events. */
    void registerTrustedDeviceEnrollmentCallback(in ITrustedDeviceEnrollmentCallback callback);

    /** Remove a previously registered callback. */
    void unregisterTrustedDeviceEnrollmentCallback(in ITrustedDeviceEnrollmentCallback callback);

    /** Register a new listener for trusted device enrollment notification requrest. */
    void registerTrustedDeviceEnrollmentNotificationRequestListener(
             in IOnTrustedDeviceEnrollmentNotificationRequestListener listener);

    /** Remove a new listener for trusted device enrollment notification requrest. */
    void unregisterTrustedDeviceEnrollmentNotificationRequestListener(
             in IOnTrustedDeviceEnrollmentNotificationRequestListener listener);

    /** Set a delegate for TrustAgent operation calls. */
    void setTrustedDeviceAgentDelegate(in ITrustedDeviceAgentDelegate trustAgentDelegate);

    /** Remove a prevoiusly set delegate. */
    void clearTrustedDeviceAgentDelegate(in ITrustedDeviceAgentDelegate trustAgentDelegate);

    /** Returns a list of trusted devices for user. */
    List<TrustedDevice> getTrustedDevicesForActiveUser();

    /** Remove a trusted device and invalidate any credentials associated with it. */
    void removeTrustedDevice(in TrustedDevice trustedDevice);

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getActiveUserConnectedDevices();

    /** Register a new callback for associated device events. */
    void registerAssociatedDeviceCallback(in IDeviceAssociationCallback callback);

    /** Remove a previously registered callback. */
    void unregisterAssociatedDeviceCallback(IDeviceAssociationCallback callback);

    /** Attempts to initiate trusted device enrollment on the phone with the given device id. */
    void initiateEnrollment(in String deviceId);
}
