/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.connecteddevice.beacon

import android.Manifest.permission
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.BeaconMessageProto.BeaconMessage
import com.google.android.companionprotos.BeaconMessageProto.BeaconMessage.MessageType
import com.google.android.connecteddevice.api.FakeConnector
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Shadows
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothAdapter
import org.robolectric.shadows.ShadowContextWrapper
import org.robolectric.shadows.ShadowPackageManager

@RunWith(AndroidJUnit4::class)
class BeaconFeatureTest {
  private lateinit var feature: BeaconFeature

  private lateinit var context: Context
  private val fakeConnector = spy(FakeConnector())
  private val device =
    ConnectedDevice(
      UUID.randomUUID().toString(),
      /* deviceName= */ null,
      /* belongsToDriver= */ true,
      /* hasSecureChannel= */ true,
    )

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext<Context>()
    feature = BeaconFeature(context, UUID.randomUUID(), fakeConnector)

    grantPermission()
    enableBluetoothRequisite()
  }

  private fun grantPermission() {
    val shadowContextWrapper: ShadowContextWrapper = Shadow.extract(context)
    shadowContextWrapper.grantPermissions(permission.BLUETOOTH_ADVERTISE, permission.BLUETOOTH)
  }

  private fun enableBluetoothRequisite() {
    val shadowBluetoothAdapter: ShadowBluetoothAdapter =
      Shadows.shadowOf(
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).getAdapter()
      )
    shadowBluetoothAdapter.setIsMultipleAdvertisementSupported(true)

    val shadowPackageManager: ShadowPackageManager = Shadows.shadowOf(context.getPackageManager())
    shadowPackageManager.setSystemFeature(FEATURE_BLUETOOTH_LE, true)
  }

  @Test
  fun onStart_connectsConnector() {
    feature.start()

    verify(fakeConnector).connect()
  }

  @Test
  fun onStart_startBroadcastingBeacon() {
    feature.start()

    val advertiser = Shadows.shadowOf(context.getBluetoothLeAdvertiser())
    assertThat(advertiser.getAdvertisementRequestCount()).isEqualTo(1)
  }

  @Test
  fun onStop_disconnectsConnector() {
    feature.stop()

    verify(fakeConnector).disconnect()
  }

  @Test
  fun onStop_stopBroadcastingBeacon() {
    feature.start()
    feature.stop()

    val advertiser = Shadows.shadowOf(context.getBluetoothLeAdvertiser())
    assertThat(advertiser.getAdvertisementRequestCount()).isEqualTo(0)
  }

  @Test
  fun onDisconnected_afterDelay_startBroadcastingBeacon() {
    feature.start()
    fakeConnector.callback!!.onSecureChannelEstablished(device)
    fakeConnector.callback!!.onDeviceDisconnected(device)

    val advertiser = Shadows.shadowOf(context.getBluetoothLeAdvertiser())
    assertThat(advertiser.getAdvertisementRequestCount()).isEqualTo(1)
  }

  @Test
  fun onSecureChannelEstablished_stopBroadcastingBeacon() {
    feature.start()
    fakeConnector.callback!!.onSecureChannelEstablished(device)

    val advertiser = Shadows.shadowOf(context.getBluetoothLeAdvertiser())
    assertThat(advertiser.getAdvertisementRequestCount()).isEqualTo(0)
  }

  @Test
  fun onMessageReceived_receivedQueryMessage_sendAck() {
    feature.start()
    fakeConnector.callback!!.onSecureChannelEstablished(device)
    fakeConnector.callback!!.onMessageReceived(device, createBeaconMessage(MessageType.QUERY))

    val captor = argumentCaptor<ByteArray>()
    verify(fakeConnector).sendMessageSecurely(eq(device), captor.capture())
    val message = BeaconMessage.parseFrom(captor.firstValue)

    assertThat(message.messageType).isEqualTo(MessageType.ACK)
  }

  @Test
  fun onMessageReceived_receivedUnknownMessage_doNothing() {
    feature.start()
    fakeConnector.callback!!.onSecureChannelEstablished(device)
    fakeConnector.callback!!.onMessageReceived(device, createBeaconMessage(MessageType.UNKNOWN))

    verify(fakeConnector, never()).sendMessageSecurely(eq(device), any())
  }

  private fun createBeaconMessage(messageType: MessageType): ByteArray {
    return BeaconMessage.newBuilder().setMessageType(messageType).build().toByteArray()
  }

  private fun Context.getBluetoothLeAdvertiser(): BluetoothLeAdvertiser {
    val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return bluetoothManager.getAdapter().getBluetoothLeAdvertiser()
  }
}
