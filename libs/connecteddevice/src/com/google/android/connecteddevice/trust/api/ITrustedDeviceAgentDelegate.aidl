package com.google.android.connecteddevice.trust.api;

/** Delegate for TrustAgent operations. */
oneway interface ITrustedDeviceAgentDelegate {

    /** Add escrow token for user. */
    void addEscrowToken(in byte[] token, in int userId);

    /** Unlock user with token and handle. */
    void unlockUserWithToken(in byte[] token, in long handle, in int userId);

    /** Remove the escrow token associated with handle for user. */
    void removeEscrowToken(in long handle, in int userId);
}
