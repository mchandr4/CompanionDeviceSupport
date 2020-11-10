package com.google.android.connecteddevice.transport.spp;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import com.google.android.connecteddevice.transport.spp.Connection;
import com.google.android.connecteddevice.transport.spp.ISppCallback;

interface IConnectedDeviceSppDelegate {

  void setCallback(in ISppCallback callback);
  void clearCallback();
  void notifyConnected(in int pendingConnectionId,
                       in BluetoothDevice remoteDevice,
                       in String deviceName);
  void notifyMessageReceived(in Connection connection, in byte[] message);
  void notifyConnectAttemptFailed(in int pendingConnectionId);
  void notifyError(in Connection connection);
}
