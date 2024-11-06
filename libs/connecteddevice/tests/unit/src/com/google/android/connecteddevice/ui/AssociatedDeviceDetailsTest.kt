/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState.CONNECTED
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState.NOT_DETECTED
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
final class AssociatedDeviceDetailsTest {
  @Test
  fun testEquality_associatedDeviceEquals() {
    val details1 = AssociatedDeviceDetails(createAssociatedDevice(), CONNECTED)
    val details2 = AssociatedDeviceDetails(createAssociatedDevice(), CONNECTED)

    assertThat(details1).isEqualTo(details2)
    assertThat(details1.hashCode()).isEqualTo(details2.hashCode())
  }

  @Test
  fun testEquality_associatedDeviceEquals_differentConnectionState() {
    val details1 = AssociatedDeviceDetails(createAssociatedDevice(), CONNECTED)
    val details2 = AssociatedDeviceDetails(createAssociatedDevice(), NOT_DETECTED)

    assertThat(details1).isEqualTo(details2)
    assertThat(details1.hashCode()).isEqualTo(details2.hashCode())
  }

  @Test
  fun testEquality_associatedDeviceDifferent() {
    val details1 = AssociatedDeviceDetails(createAssociatedDevice(), CONNECTED)
    val details2 = AssociatedDeviceDetails(createAssociateddetails2(), CONNECTED)

    assertThat(details1).isNotEqualTo(details2)
  }

  @Test
  fun testGetters_matchAssociatedDevice() {
    val associatedDevice = createAssociatedDevice()
    val connectionState = CONNECTED
    val details = AssociatedDeviceDetails(associatedDevice, connectionState)

    assertThat(details.id).isEqualTo(associatedDevice.id)
    assertThat(details.name).isEqualTo(associatedDevice.name)
    assertThat(details.address).isEqualTo(associatedDevice.address)
    assertThat(details.isConnectionEnabled).isEqualTo(associatedDevice.isConnectionEnabled)
    assertThat(details.userId).isEqualTo(associatedDevice.userId)
    assertThat(details.connectionState).isEqualTo(connectionState)
  }

  private companion object {
    const val TEST_ASSOCIATED_DEVICE_ID = "test_device_id"
    const val TEST_ASSOCIATED_DEVICE_NAME = "test_device_name"
    const val TEST_ASSOCIATED_DEVICE_ADDRESS = "test_device_address"

    const val TEST_ASSOCIATED_DEVICE_ID_2 = "test_device_id_2"
    const val TEST_ASSOCIATED_DEVICE_NAME_2 = "test_device_name_2"
    const val TEST_ASSOCIATED_DEVICE_ADDRESS_2 = "test_device_address_2"

    fun createAssociatedDevice(): AssociatedDevice =
      AssociatedDevice(
        TEST_ASSOCIATED_DEVICE_ID,
        TEST_ASSOCIATED_DEVICE_ADDRESS,
        TEST_ASSOCIATED_DEVICE_NAME,
        /* isConnectionEnabled= */ true,
      )

    /**
     * Returns an [AssociatedDevice] that is different from that returned by
     * [createAssociatedDevice].
     */
    fun createAssociateddetails2(): AssociatedDevice =
      AssociatedDevice(
        TEST_ASSOCIATED_DEVICE_ID_2,
        TEST_ASSOCIATED_DEVICE_ADDRESS_2,
        TEST_ASSOCIATED_DEVICE_NAME_2,
        /* isConnectionEnabled= */ true,
      )
  }
}
