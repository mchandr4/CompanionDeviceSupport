package com.google.android.connecteddevice.transport.spp;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.PendingSentMessage;

interface ISppCallback {

  /** Returns true if the server starts listening successfully, false otherwise. */
  boolean onStartConnectionAsServerRequested(in ParcelUuid serviceUuid, in boolean isSecure);
  void onStartConnectionAsClientRequested(in ParcelUuid serviceUuid,
    in BluetoothDevice remoteDevice, in boolean isSecure);
  PendingSentMessage onSendMessageRequested(in Connection connection, in byte[] message);
  void onDisconnectRequested(in Connection connection);
  void onCancelConnectionAttemptRequested(in int pendingConnectionId);
}
