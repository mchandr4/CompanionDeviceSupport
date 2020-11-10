package com.google.android.connecteddevice.api;

import com.google.android.connecteddevice.model.AssociatedDevice;

/** Callback for triggered associated device related events. */
oneway interface IDeviceAssociationCallback {
    /** Triggered when an associated device has been added */
    void onAssociatedDeviceAdded(in AssociatedDevice device);

    /** Triggered when an associated device has been removed.  */
    void onAssociatedDeviceRemoved(in AssociatedDevice device);

    /** Triggered when an associated device has been updated. */
    void onAssociatedDeviceUpdated(in AssociatedDevice device);
}
