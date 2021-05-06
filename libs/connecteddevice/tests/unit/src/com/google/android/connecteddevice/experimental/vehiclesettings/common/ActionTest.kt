package com.google.android.connecteddevice.experimental.vehiclesettings.common

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** Unit tests for the Action data class. */
class ActionTest {

  @Test
  fun instantiatesCorrectlyTest() {
    val timeNow = Instant.now()
    val actionBasic = Action(7, "On", timeNow)
    val anotherAction = Action(12, "Off", timeNow)
    assertThat(actionBasic.propertyId).isEqualTo(7)
    assertThat(actionBasic.newPropertyValue).isEqualTo("On")
    assertThat(actionBasic.timestamp).isEqualTo(timeNow)
    assertThat(actionBasic.actionId).isNotEqualTo(anotherAction.actionId)
  }
}
