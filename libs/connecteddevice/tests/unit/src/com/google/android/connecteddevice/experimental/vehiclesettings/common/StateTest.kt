package com.google.android.connecteddevice.experimental.vehiclesettings.common

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** Unit tests for the State data class. */
class StateTest {

  @Test
  fun instantiatesCorrectlyTest() {
    val propertyId = 7
    val areaId = 12
    val originalPropertyValue = "Off"
    val timeNow = Instant.now()
    val basicState = State(propertyId, areaId, originalPropertyValue, timeNow)
    assertThat(basicState.propertyValue).isEqualTo("Off")
    assertThat(basicState.propertyId).isEqualTo(7)
    assertThat(basicState.areaId).isEqualTo(12)
    assertThat(basicState.timestamp).isEqualTo(timeNow)
  }
}
