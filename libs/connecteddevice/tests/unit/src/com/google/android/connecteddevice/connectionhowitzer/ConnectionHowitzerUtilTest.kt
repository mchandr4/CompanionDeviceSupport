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

package com.google.android.connecteddevice.connectionhowitzer

import com.google.android.connecteddevice.connectionhowitzer.ConnectionHowitzerFeature.Companion.toInstant
import com.google.testing.junit.testparameterinjector.TestParameters
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValues
import com.google.testing.junit.testparameterinjector.TestParameters.TestParametersValuesProvider
import com.google.thirdparty.robolectric.testparameterinjector.RobolectricTestParameterInjector
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestParameterInjector::class)
@Config(sdk = [29])
class ConnectionHowitzerUtilTest {
  @Test
  @TestParameters(valuesProvider = CalculateBandwidthTestParametersValuesProvider::class)
  fun calculateBandwidth_avgBytesPerSeconds(
    startTime: Instant,
    config: ConnectionHowitzerFeature.HowitzerConfig,
    payloadTimeInstantList: List<Instant>,
    expected: Double,
  ) {
    val actual =
      ConnectionHowitzerFeature.calculateBandwidthBytesPerSecond(
        config,
        startTime,
        payloadTimeInstantList,
      )

    assertEquals(actual, expected)
  }

  @Test
  fun calculateBandwidth_failIfPayloadCountIsZero() {
    val startTime = Instant.now()
    val payloadTimestamps = mutableListOf<Instant>(startTime.plusSeconds(1))
    val config = createConfig(payloadCount = 0, testId = TEST_ID)

    assertFailsWith<IllegalArgumentException> {
      ConnectionHowitzerFeature.calculateBandwidthBytesPerSecond(
        config,
        startTime,
        payloadTimestamps,
      )
    }
  }

  @Test
  fun calculateBandwidth_failIfPayloadSizeIsZero() {
    val startTime = Instant.now()
    val payloadTimestamps = mutableListOf<Instant>(startTime.plusSeconds(1))
    val config = createConfig(payloadSize = 0, testId = TEST_ID)

    assertFailsWith<IllegalArgumentException> {
      ConnectionHowitzerFeature.calculateBandwidthBytesPerSecond(
        config,
        startTime,
        payloadTimestamps,
      )
    }
  }

  @Test
  fun calculateBandwidth_failFromEmptyResultMessage() {
    val config = createConfig(testId = TEST_ID)
    val message = createEmptyResult(config)
    val startTime = message.result.testStartTimestamp.toInstant()
    val payloadTimestamps = mutableListOf<Instant>()
    for (timestamp in message.result.payloadReceivedTimestampsList) {
      payloadTimestamps.add(timestamp.toInstant())
    }

    assertFailsWith<IllegalArgumentException> {
      ConnectionHowitzerFeature.calculateBandwidthBytesPerSecond(
        config,
        startTime,
        payloadTimestamps,
      )
    }
  }

  @Test
  fun calculateBandwidth_failIfDurationIsNotPositive() {
    val startTime = Instant.now()
    val payloadTimestamps = mutableListOf<Instant>(startTime)
    val config = createConfig(testId = TEST_ID)

    assertFailsWith<IllegalArgumentException> {
      ConnectionHowitzerFeature.calculateBandwidthBytesPerSecond(
        config,
        startTime,
        payloadTimestamps,
      )
    }
  }

  companion object {
    private val TEST_ID = UUID.randomUUID()
  }

  class CalculateBandwidthTestParametersValuesProvider : TestParametersValuesProvider {
    override fun provideValues(): List<TestParametersValues> {
      val startTime = Instant.now()

      val config1 =
        ConnectionHowitzerFeature.HowitzerConfig(
          sendPayloadFromIhu = true,
          payloadSize = 10,
          payloadCount = 1,
          testId = TEST_ID,
        )
      val payloadTimestampList1 = listOf(startTime.plusMillis(1000))
      val expected1 = 10.0

      val config2 =
        ConnectionHowitzerFeature.HowitzerConfig(
          sendPayloadFromIhu = false,
          payloadSize = 17,
          payloadCount = 3,
          testId = TEST_ID,
        )
      val payloadTimestampList2 = listOf(startTime.plusMillis(2500))
      val expected2 = 20.4

      val config3 =
        ConnectionHowitzerFeature.HowitzerConfig(
          sendPayloadFromIhu = true,
          payloadSize = 23,
          payloadCount = 9,
          testId = TEST_ID,
        )
      val payloadTimestampList3 = listOf(startTime.plusMillis(5000))
      val expected3 = 41.4

      return listOf(
        testValue("Bandwidth config 1", startTime, config1, payloadTimestampList1, expected1),
        testValue("Bandwidth config 2", startTime, config2, payloadTimestampList2, expected2),
        testValue("Bandwidth config 3", startTime, config3, payloadTimestampList3, expected3),
      )
    }

    companion object {
      private fun testValue(
        name: String,
        startTime: Instant,
        config: ConnectionHowitzerFeature.HowitzerConfig,
        payloadTimeInstantList: List<Instant>,
        expected: Double,
      ) =
        TestParametersValues.builder()
          .name(name)
          .addParameter("startTime", startTime)
          .addParameter("config", config)
          .addParameter("payloadTimeInstantList", payloadTimeInstantList)
          .addParameter("expected", expected)
          .build()
    }
  }
}
