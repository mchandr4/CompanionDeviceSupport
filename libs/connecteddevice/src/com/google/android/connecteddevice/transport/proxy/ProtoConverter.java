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

package com.google.android.connecteddevice.transport.proxy;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseData;
import com.google.protobuf.ByteString;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Characteristic;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Service;
import java.util.UUID;

/** A collection of methods that converts between proxy proto messages and platform APIs. */
final class ProtoConverter {
  private static final String TAG = "BlePeripheralManager";

  static Characteristic toCharacteristic(BluetoothGattCharacteristic gattCharacteristic) {
    Characteristic.Builder builder =
        Characteristic.newBuilder().setIdentifier(gattCharacteristic.getUuid().toString());

    // Characteristic properties.
    int properties = gattCharacteristic.getProperties();
    if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
      builder.addProperties(Characteristic.Property.READ);
    }
    if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
      builder.addProperties(Characteristic.Property.WRITE);
    }
    if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
      builder.addProperties(Characteristic.Property.WRITE_WITHOUT_RESPONSE);
    }
    if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
      builder.addProperties(Characteristic.Property.NOTIFY);
    }

    // Characteristic permissions.
    int permissions = gattCharacteristic.getPermissions();
    if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ) != 0) {
      builder.addPermissions(Characteristic.Permission.READABLE);
    }
    if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0) {
      builder.addPermissions(Characteristic.Permission.WRITEABLE);
    }

    final byte[] rawValue = gattCharacteristic.getValue();
    if (rawValue != null) {
      ByteString value = ByteString.copyFrom(rawValue);
      builder.setValue(value);
    }

    return builder.build();
  }

  static BluetoothGattCharacteristic toGattCharacteristic(Characteristic characteristic) {
    final String identifier = characteristic.getIdentifier();
    final UUID uuid = UUID.fromString(identifier);

    int properties = 0;
    for (Characteristic.Property property : characteristic.getPropertiesList()) {
      switch (property) {
        case READ:
          properties |= BluetoothGattCharacteristic.PROPERTY_READ;
          break;
        case WRITE:
          properties |= BluetoothGattCharacteristic.PROPERTY_WRITE;
          break;
        case WRITE_WITHOUT_RESPONSE:
          properties |= BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
          break;
        case NOTIFY:
          properties |= BluetoothGattCharacteristic.PROPERTY_NOTIFY;
          break;
        case UNRECOGNIZED:
        case PROPERTY_UNSPECIFIED:
          loge(TAG, "Read unrecognized characteristic property.");
          break;
      }
    }

    int permissions = 0;
    for (Characteristic.Permission permission : characteristic.getPermissionsList()) {
      switch (permission) {
        case READABLE:
          permissions |= BluetoothGattCharacteristic.PERMISSION_READ;
          break;
        case WRITEABLE:
          permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE;
          break;
        case UNRECOGNIZED:
        case PERMISSION_UNSPECIFIED:
          loge(TAG, "Read unrecognized characteristic permission.");
          break;
      }
    }

    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(uuid, properties, permissions);
    ByteString value = characteristic.getValue();
    if (value.size() > 0) {
      gattCharacteristic.setValue(value.toByteArray());
    }

    return gattCharacteristic;
  }

  static Service toService(BluetoothGattService gattService, AdvertiseData advertiseData) {
    Service.Builder builder = Service.newBuilder().setIdentifier(gattService.getUuid().toString());

    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
      builder.addCharacteristics(toCharacteristic(gattCharacteristic));
    }

    return builder.build();
  }

  static BluetoothGattService toGattService(Service service) {
    String identifier = service.getIdentifier();
    UUID uuid = UUID.fromString(identifier);

    BluetoothGattService gattService =
        new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    for (Characteristic characteristic : service.getCharacteristicsList()) {
      final BluetoothGattCharacteristic gattCharacteristic = toGattCharacteristic(characteristic);
      gattService.addCharacteristic(gattCharacteristic);
    }

    return gattService;
  }

  // No instantiation.
  private ProtoConverter() {}
}
