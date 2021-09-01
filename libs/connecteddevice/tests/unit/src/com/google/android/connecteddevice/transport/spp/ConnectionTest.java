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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.os.Parcel;
import android.os.ParcelUuid;
import androidx.test.core.app.ApplicationProvider;
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
        ApplicationProvider.getApplicationContext()
            .getSystemService(BluetoothManager.class)
            .getAdapter()
            .getRemoteDevice("00:11:22:33:44:55");
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
