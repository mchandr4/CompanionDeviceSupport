package com.google.android.connecteddevice.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemQueryCacheTest {

  private lateinit var cache: SystemQueryCacheImpl

  @Before
  fun setUp() {
    cache = SystemQueryCacheImpl()
  }

  @Test
  fun maybeCacheResponse_createsCacheForNewDevice() {
    val deviceId = UUID.randomUUID()
    val device = createConnectedDevice(deviceId)

    cache.maybeCacheResponse(device, mock())

    assertThat(deviceId in cache.deviceCaches).isTrue()
  }

  companion object {
    private fun createConnectedDevice(deviceId: UUID) =
      ConnectedDevice(
        deviceId.toString(),
        /* deviceName= */ "",
        /* belongsToDriver= */ true,
        true,
      )
  }
}
