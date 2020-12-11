/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.connecteddevice.transport.spp;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.Objects;
import java.util.UUID;

/**
 * An object that represents a connection that has been requested, but has not yet been connected.
 *
 * <p>This is returned by {@link ConnectedDeviceSppDelegateBinder#connectAsClient} or {@link
 * ConnectedDeviceSppDelegateBinder#connectAsServer}, and clients can add {@link
 * OnConnectedListener} and {@link OnConnectionErrorListener} to be notified when a connection is
 * established or a connection error occurs.
 */
public class PendingConnection {
  private final UUID serviceUuid;
  private final boolean isSecure;
  private final int id;

  private OnConnectedListener onConnectedListener;
  private OnConnectionErrorListener onConnectionErrorListener;

  public PendingConnection(UUID serviceUuid, boolean isSecure) {
    this(serviceUuid, null, isSecure);
  }

  public PendingConnection(
      UUID serviceUuid, @Nullable BluetoothDevice remoteDevice, boolean isSecure) {
    this.serviceUuid = serviceUuid;
    this.isSecure = isSecure;
    id = Objects.hash(serviceUuid, Objects.hashCode(remoteDevice), isSecure);
  }

  public PendingConnection setOnConnectedListener(
      @Nullable OnConnectedListener onConnectedListener) {
    this.onConnectedListener = onConnectedListener;
    return this;
  }

  public PendingConnection setOnConnectionErrorListener(
      @Nullable OnConnectionErrorListener onConnectionErrorListener) {
    this.onConnectionErrorListener = onConnectionErrorListener;
    return this;
  }

  /**
   * Returns an ID that can be used to uniquely identify a {@link PendingConnection} request. If
   * it's a client connection, the ID is based on the {@code remoteDevice} it is trying to connect
   * to, the service {@code serviceUuid} of the RFCOMM channel, and {@code isSecure}. For a server
   * connection, where the socket is listening for incoming connections, {@code remoteDevice} is
   * null and not used to calculate the ID.
   */
  public int getId() {
    return id;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public void notifyConnected(@NonNull BluetoothDevice remoteDevice, String deviceName) {
    if (onConnectedListener != null) {
      onConnectedListener.onConnected(serviceUuid, remoteDevice, isSecure, deviceName);
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public void notifyConnectionError() {
    if (onConnectionErrorListener != null) {
      onConnectionErrorListener.onConnectionError();
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public Connection toConnection(BluetoothDevice remoteDevice) {
    return new Connection(
        new ParcelUuid(serviceUuid), remoteDevice, isSecure, remoteDevice.getName());
  }

  /**
   * Listens for a {@link PendingConnection} to be connected.
   *
   * <p>Note: This is a local callback only-- it's not sent through the IPC binder.
   */
  public interface OnConnectedListener {
    void onConnected(UUID uuid, BluetoothDevice remoteDevice, boolean isSecure, String deviceName);
  }

  /**
   * Listens for a failure when a {@link Connection} is in the process of connecting.
   *
   * <p>Note: This is a local callback only-- it's not sent through the IPC binder.
   */
  public interface OnConnectionErrorListener {
    void onConnectionError();
  }
}
