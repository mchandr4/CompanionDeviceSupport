package com.google.android.connecteddevice.api;

import com.google.android.connecteddevice.api.IAssociationCallback;
import com.google.android.connecteddevice.api.IConnectionCallback;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.OobEligibleDevice;

/** Manager of devices associated with the car. */
interface IAssociatedDeviceManager {

    /**
     * Set a callback for association.
     * @param callback {@link IAssociationCallback} to set.
     */
    void setAssociationCallback(in IAssociationCallback callback);

    /** Clear the association callback from manager. */
    void clearAssociationCallback();

    /** Starts the association with a new device. */
    void startAssociation();

    /** Start an out-of-band association with the given device. */
    void startOobAssociation(in OobEligibleDevice eligibleDevice);

    /** Stops the association with current device. */
    void stopAssociation();

    /** Returns a {@link List<AssociatedDevice>} of devices associated with the given user. */
    List<AssociatedDevice> getActiveUserAssociatedDevices();

    /** Confirms the paring code. */
    void acceptVerification();

    /** Remove the associated device of the given identifier for the active user. */
    void removeAssociatedDevice(in String deviceId);

    /**
     * Set a callback for associated device related events.
     *
     * @param callback {@link IDeviceAssociationCallback} to set.
     */
    void setDeviceAssociationCallback(in IDeviceAssociationCallback callback);

    /** Clear the device association callback from manager. */
    void clearDeviceAssociationCallback();

    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    List<ConnectedDevice> getActiveUserConnectedDevices();

    /**
     * Set a callback for connection events for only the currently active user's devices.
     *
     * @param callback {@link IConnectionCallback} to set.
     */
    void setConnectionCallback(in IConnectionCallback callback);

    /** Clear the connection callback from manager. */
    void clearConnectionCallback();

    /** Enable connection on the associated device with the given identifier. */
    void enableAssociatedDeviceConnection(in String deviceId);

    /** Disable connection on the associated device with the given identifier. */
    void disableAssociatedDeviceConnection(in String deviceId);
}
