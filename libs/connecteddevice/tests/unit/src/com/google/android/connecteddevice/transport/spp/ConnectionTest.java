package com.google.android.connecteddevice.transport.spp;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConnectionTest {

  @Test
  public void testToAndFromParcel() {
    ParcelUuid testServiceUuid = new ParcelUuid(UUID.randomUUID());
    BluetoothDevice testRemoteDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
    String testDeviceName = "testDeviceName";
    Connection inConnection =
        new Connection(testServiceUuid, testRemoteDevice, /* isSecure= */ true, testDeviceName);
    Parcel parcel = Parcel.obtain();
    inConnection.writeToParcel(parcel, 0);

    parcel.setDataPosition(0);
    Connection outConnection = Connection.CREATOR.createFromParcel(parcel);

    assertThat(inConnection).isEqualTo(outConnection);
    assertThat(testServiceUuid).isEqualTo(outConnection.getServiceUuid());
    assertThat(testRemoteDevice).isEqualTo(outConnection.getRemoteDevice());
    assertThat(testDeviceName).isEqualTo(outConnection.getRemoteDeviceName());
    assertThat(outConnection.isSecure()).isTrue();
  }
}
