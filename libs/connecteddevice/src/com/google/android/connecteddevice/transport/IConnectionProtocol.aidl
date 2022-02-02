/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.connecteddevice.transport;

import android.os.ParcelUuid;

import com.google.android.connecteddevice.transport.ConnectChallenge;
import com.google.android.connecteddevice.transport.IDataReceivedListener;
import com.google.android.connecteddevice.transport.IDataSendCallback;
import com.google.android.connecteddevice.transport.IDeviceDisconnectedListener;
import com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener;
import com.google.android.connecteddevice.transport.IDiscoveryCallback;

interface IConnectionProtocol {
  /**
   * Returns true if challenge exchange is required to verify the remote device for establishing a
   * secure channel over this protocol.
   */
  boolean isDeviceVerificationRequired();

  /**
   * Begin the discovery process with the name and identifier for a new device to associate with.
   */
  void startAssociationDiscovery(
      in String name,
      in ParcelUuid identifier,
      in IDiscoveryCallback callback);

  /**
   * Begin the discovery process for a device that will respond to the supplied id and challenge.
   */
  void startConnectionDiscovery(
      in ParcelUuid id,
      in ConnectChallenge challenge,
      in IDiscoveryCallback callback);

  /** Stop an ongoing association discovery. */
  void stopAssociationDiscovery();

  /** Stop an ongoing connection discovery for the provided device. */
  void stopConnectionDiscovery(in ParcelUuid id);

  /** Send data to a device. */
  void sendData(in String protocolId, in byte[] data, in @nullable IDataSendCallback callback);

  /** Disconnect a specific device. */
  void disconnectDevice(in String protocolId);

  /**
   * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
   * neutral state.
   */
  void reset();

  /**
   * Returns the maximum number of bytes that can be written in a single message for the device
   * matching the protocolId.
   */
  int getMaxWriteSize(in String protocolId);

  /** Register a listener to be notified when data has been received on the specified  device. */
  void registerDataReceivedListener(in String protocolId, in IDataReceivedListener listener);

  /** Register a listener to be notified when device has disconnected. */
  void registerDeviceDisconnectedListener(
      in String protocolId,
      in IDeviceDisconnectedListener listener);

  /**
   * Register a listener to be notified when the specified device has negotiated a new maximum
   * data size.
   */
  void registerDeviceMaxDataSizeChangedListener(
    in String protocolId,
    in IDeviceMaxDataSizeChangedListener listener);

  /** Unregister a previously registered listener. */
  void unregisterDataReceivedListener(in String protocolId, in IDataReceivedListener listener);

  /** Unregister a previously registered listener. */
  void unregisterDeviceDisconnectListener(
    in String protocolId,
    in IDeviceDisconnectedListener listener);

  /** Unregister a previously registered listener. */
  void unregisterDeviceMaxDataSizeChangedListener(
      in String protocolId,
      in IDeviceMaxDataSizeChangedListener listener);

  /** Removes registered listeners for the specified device. */
  void removeListeners(in String protocolId);
}
