package com.google.android.connecteddevice.transport.proxy;

import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.protobuf.ByteString;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Characteristic;
import com.google.protos.aae.bleproxy.BlePeripheralMessage.Service;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ProtoConverterTest {

  // Tests that verify Android API to proto.
  @Test
  public void toCharacteristic_uuid() {
    UUID testUuid = UUID.randomUUID();
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(testUuid, 0, 0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getIdentifier()).isEqualTo(testUuid.toString());
    assertThat(characteristic.getPropertiesList()).isEmpty();
    assertThat(characteristic.getPermissionsList()).isEmpty();
    assertThat(characteristic.getValue()).isEmpty();
  }

  @Test
  public void toCharacteristic_value() {
    byte[] value = new byte[] {(byte) 0xbe, (byte) 0xef};
    UUID testUuid = UUID.randomUUID();
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(testUuid, 0, 0);
    gattCharacteristic.setValue(value);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(Arrays.equals(characteristic.getValue().toByteArray(), value)).isTrue();
    assertThat(characteristic.getIdentifier()).isEqualTo(testUuid.toString());
    assertThat(characteristic.getPropertiesList()).isEmpty();
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_propertyRead() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_READ, 0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList()).containsExactly(Characteristic.Property.READ);
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_propertyWrite() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_WRITE, 0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList()).containsExactly(Characteristic.Property.WRITE);
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_propertyWriteNoResponse() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList())
        .containsExactly(Characteristic.Property.WRITE_WITHOUT_RESPONSE);
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_propertyNotify() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList()).containsExactly(Characteristic.Property.NOTIFY);
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_propertyCombined() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_READ
                | BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            0);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList())
        .containsExactly(
            Characteristic.Property.NOTIFY,
            Characteristic.Property.READ,
            Characteristic.Property.WRITE,
            Characteristic.Property.WRITE_WITHOUT_RESPONSE);
    assertThat(characteristic.getPermissionsList()).isEmpty();
  }

  @Test
  public void toCharacteristic_permissionRead() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), 0, BluetoothGattCharacteristic.PERMISSION_READ);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList()).isEmpty();
    assertThat(characteristic.getPermissionsList())
        .containsExactly(Characteristic.Permission.READABLE);
  }

  @Test
  public void toCharacteristic_permissionWrite() {
    BluetoothGattCharacteristic gattCharacteristic =
        new BluetoothGattCharacteristic(
            UUID.randomUUID(), 0, BluetoothGattCharacteristic.PERMISSION_WRITE);

    Characteristic characteristic = ProtoConverter.toCharacteristic(gattCharacteristic);

    assertThat(characteristic.getPropertiesList()).isEmpty();
    assertThat(characteristic.getPermissionsList())
        .containsExactly(Characteristic.Permission.WRITEABLE);
  }

  @Test
  public void toService_uuid() {
    UUID testUuid = UUID.randomUUID();
    BluetoothGattService gattService = new BluetoothGattService(testUuid, SERVICE_TYPE_PRIMARY);

    Service service = ProtoConverter.toService(gattService, null);

    assertThat(service.getIdentifier()).isEqualTo(testUuid.toString());
    assertThat(service.getCharacteristicsList()).isEmpty();
  }

  @Test
  public void toService_characteristic() {
    UUID testUuid = UUID.randomUUID();
    BluetoothGattService gattService =
        new BluetoothGattService(UUID.randomUUID(), SERVICE_TYPE_PRIMARY);
    gattService.addCharacteristic(new BluetoothGattCharacteristic(testUuid, 0, 0));

    Service service = ProtoConverter.toService(gattService, null);

    assertThat(service.getCharacteristicsList()).hasSize(1);
    assertThat(service.getCharacteristics(0).getIdentifier()).isEqualTo(testUuid.toString());
  }

  // Tests that verify proto to Android API.
  @Test
  public void toGattCharacteristic_uuid() {
    UUID testUuid = UUID.randomUUID();
    Characteristic characteristic =
        Characteristic.newBuilder().setIdentifier(testUuid.toString()).build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getUuid()).isEqualTo(testUuid);
    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties()).isEqualTo(0);
    assertThat(gattCharacteristic.getValue()).isNull();
  }

  @Test
  public void toGattCharacteristic_value() {
    byte[] data = new byte[] {(byte) 0xbe, (byte) 0xef};
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .setValue(ByteString.copyFrom(data))
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties()).isEqualTo(0);
    assertThat(Arrays.equals(gattCharacteristic.getValue(), data)).isTrue();
  }

  @Test
  public void toGattCharacteristic_propertyRead() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addProperties(Characteristic.Property.READ)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties())
        .isEqualTo(BluetoothGattCharacteristic.PROPERTY_READ);
  }

  @Test
  public void toGattCharacteristic_propertyWrite() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addProperties(Characteristic.Property.WRITE)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties())
        .isEqualTo(BluetoothGattCharacteristic.PROPERTY_WRITE);
  }

  @Test
  public void toGattCharacteristic_propertyWriteNoResponse() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addProperties(Characteristic.Property.WRITE_WITHOUT_RESPONSE)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties())
        .isEqualTo(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
  }

  @Test
  public void toGattCharacteristic_propertyNotify() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addProperties(Characteristic.Property.NOTIFY)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties())
        .isEqualTo(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
  }

  @Test
  public void toGattCharacteristic_propertyCombined() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addProperties(Characteristic.Property.READ)
            .addProperties(Characteristic.Property.NOTIFY)
            .addProperties(Characteristic.Property.WRITE)
            .addProperties(Characteristic.Property.WRITE_WITHOUT_RESPONSE)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions()).isEqualTo(0);
    assertThat(gattCharacteristic.getProperties())
        .isEqualTo(
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_READ
                | BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
  }

  @Test
  public void toGattCharacteristic_permissionRead() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addPermissions(Characteristic.Permission.READABLE)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions())
        .isEqualTo(BluetoothGattCharacteristic.PERMISSION_READ);
    assertThat(gattCharacteristic.getProperties()).isEqualTo(0);
  }

  @Test
  public void toGattCharacteristic_permissionWrite() {
    Characteristic characteristic =
        Characteristic.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addPermissions(Characteristic.Permission.WRITEABLE)
            .build();

    BluetoothGattCharacteristic gattCharacteristic =
        ProtoConverter.toGattCharacteristic(characteristic);

    assertThat(gattCharacteristic.getPermissions())
        .isEqualTo(BluetoothGattCharacteristic.PERMISSION_WRITE);
    assertThat(gattCharacteristic.getProperties()).isEqualTo(0);
  }

  @Test
  public void toGattService_uuid() {
    UUID testUuid = UUID.randomUUID();
    Service service = Service.newBuilder().setIdentifier(testUuid.toString()).build();

    BluetoothGattService gattService = ProtoConverter.toGattService(service);

    assertThat(gattService.getUuid()).isEqualTo(testUuid);
    assertThat(gattService.getCharacteristics()).isEmpty();
  }

  @Test
  public void toGattService_characteristic() {
    UUID testUuid = UUID.randomUUID();
    Characteristic characteristic =
        Characteristic.newBuilder().setIdentifier(testUuid.toString()).build();
    Service service =
        Service.newBuilder()
            .setIdentifier(UUID.randomUUID().toString())
            .addCharacteristics(characteristic)
            .build();

    BluetoothGattService gattService = ProtoConverter.toGattService(service);

    assertThat(gattService.getCharacteristic(testUuid)).isNotNull();
  }
}
