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

import android.os.ParcelUuid
import androidx.annotation.VisibleForTesting
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.connectionhowitzer.proto.Config
import com.google.android.connecteddevice.connectionhowitzer.proto.HowitzerMessage
import com.google.android.connecteddevice.connectionhowitzer.proto.HowitzerMessage.MessageType
import com.google.android.connecteddevice.connectionhowitzer.proto.Result
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random

/**
 * A companion device feature that sends/receives arbitrary bytes to/from the phone side for
 * bandwidth testing.
 */
class ConnectionHowitzerFeature(private val connector: Connector) : Connector.Callback {

  init {
    connector.apply {
      featureId = FEATURE_ID
      callback = this@ConnectionHowitzerFeature
    }
  }

  private enum class State {
    // Waiting for config from phone.
    PENDING_CONFIG,
    // Send payload to phone.
    COUNTING_PAYLOAD,
    // After sending payloads, waiting for result from phone.
    PENDING_RESULT,
    // After sending result
    // Waiting for result ack from phone.
    PENDING_RESULT_ACK,
  }

  private var counter = 0
  private var state = State.PENDING_CONFIG
  private var testStartTime: Instant? = null
  private var connectedDevice: ConnectedDevice? = null

  @VisibleForTesting internal val payloadTimeInstants = mutableListOf<Instant>()
  @VisibleForTesting internal lateinit var config: HowitzerConfig

  fun start() {
    connector.connect()
  }

  fun stop() {
    connector.disconnect()
  }

  override fun onSecureChannelEstablished(device: ConnectedDevice) {
    logd(TAG, "onSecureChannelEstablished: ${device.deviceId}")
    if (connectedDevice != null) {
      logw(TAG, "Device ${device.deviceId} connected while cached ${connectedDevice?.deviceId}.")
    }
    connectedDevice = device
  }

  override fun onDeviceDisconnected(device: ConnectedDevice) {
    logd(TAG, "onDeviceDisconnected: ${device.deviceId}")
    if (device != connectedDevice) {
      logw(
        TAG,
        "Disconnected device, ${device.deviceId}, does not match the cached device, " +
          "${connectedDevice?.deviceId}."
      )
    }
    reset()
    connectedDevice = null
  }

  override fun onMessageReceived(device: ConnectedDevice, message: ByteArray) {
    when (state) {
      State.PENDING_CONFIG -> handleConfig(message)
      State.COUNTING_PAYLOAD -> handlePayload(message)
      State.PENDING_RESULT -> handleResult(message)
      State.PENDING_RESULT_ACK -> handleResultAck(message)
    }
  }

  private fun handleConfig(message: ByteArray) {
    val howitzerMessage =
      try {
        HowitzerMessage.parseFrom(message)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Could not parse message as Config proto.", e)
        return
      }

    if (howitzerMessage.messageType != MessageType.CONFIG) {
      loge(TAG, "Expected Config but received $howitzerMessage. Ignored.")
      return
    }

    config =
      HowitzerConfig(
        sendPayloadFromIhu = howitzerMessage.config.sendPayloadFromIhu,
        payloadSize = howitzerMessage.config.payloadSize,
        payloadCount = howitzerMessage.config.payloadCount,
        testId = UUID.fromString(howitzerMessage.config.testId),
      )
    logd(TAG, "Received config from the phone: $config.")

    sendAck()

    if (config.sendPayloadFromIhu) {
      logd(TAG, "IHU is the sender; start sending payloads.")
      sendPayload()
    } else {
      logd(TAG, "Phone is the sender; setting state to ${State.COUNTING_PAYLOAD}.")
      counter = 0
      // Test start time is approximated by the time received config.
      testStartTime = Instant.now()
      state = State.COUNTING_PAYLOAD
    }
  }

  private fun sendAck() {
    logd(TAG, "Sending Ack message.")
    val message = HowitzerMessage.newBuilder().setMessageType(MessageType.ACK).build()
    connector.sendMessageSecurely(connectedDevice!!, message.toByteArray())
  }

  private fun sendPayload() {
    for (i in 1..config.payloadCount) {
      val payload = ByteArray(config.payloadSize).apply { Random.nextBytes(this) }
      logd(TAG, "Queueing payload to be sent: #$i.")
      connector.sendMessageSecurely(connectedDevice!!, payload)
    }

    logd(TAG, "All payloads are queued to be sent; setting state to ${State.PENDING_RESULT}.")
    state = State.PENDING_RESULT
  }

  private fun handlePayload(message: ByteArray) {
    check(message.size == config.payloadSize) {
      "Received payload of size ${message.size} while expecting ${config.payloadSize}."
    }

    counter++
    payloadTimeInstants.add(Instant.now())
    logd(TAG, "Received payload #$counter.")

    if (counter == config.payloadCount) {
      logd(TAG, "Payload receive completed. Setting state to ${State.PENDING_RESULT_ACK}")
      sendResult()
      state = State.PENDING_RESULT_ACK
    }
  }

  private fun sendResult() {
    val startTime =
      testStartTime
        ?: run {
          loge(TAG, "No recorded testStartTime found.")
          reset()
          return
        }

    val result =
      Result.newBuilder().run {
        isValid = true
        for (instant in payloadTimeInstants) {
          addPayloadReceivedTimestamps(instant.toTimestamp())
        }
        testStartTimestamp = startTime.toTimestamp()
        build()
      }
    val message =
      HowitzerMessage.newBuilder()
        .setMessageType(MessageType.RESULT)
        .setConfig(config.toConfigProto())
        .setResult(result)
        .build()
    connector.sendMessageSecurely(connectedDevice!!, message.toByteArray())
  }

  private fun handleResult(message: ByteArray) {
    val howitzerMessage =
      try {
        HowitzerMessage.parseFrom(message)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Waiting for Result. Failed to parse the message.", e)
        reset()
        return
      }

    when {
      howitzerMessage.messageType != MessageType.RESULT -> {
        loge(TAG, "Expected Result but received $howitzerMessage.")
        reset()
      }
      !howitzerMessage.result.isValid -> {
        loge(TAG, "Received test result is not valid.")
        reset()
      }
      howitzerMessage.config != config.toConfigProto() -> {
        loge(TAG, "Received result ${howitzerMessage.config} does not match internal $config.")
        reset()
      }
      else -> {
        logd(TAG, "Received results from the phone.")
        recordResults(howitzerMessage.result)
        if (recordBandwidth()) {
          sendAck()
        }
        reset()
      }
    }
  }

  private fun handleResultAck(message: ByteArray) {
    logd(TAG, "Received Result Ack from Phone.")

    val howitzerMessage =
      try {
        HowitzerMessage.parseFrom(message)
      } catch (e: InvalidProtocolBufferException) {
        loge(TAG, "Waiting for Result Ack. Failed to parse the message.", e)
        reset()
        return
      }

    if (howitzerMessage.messageType != MessageType.ACK) {
      loge(TAG, "Expected Result Ack but received $howitzerMessage.")
      reset()
      return
    }

    recordBandwidth()
    reset()
  }

  private fun recordResults(result: Result) {
    testStartTime = result.testStartTimestamp.toInstant()
    payloadTimeInstants += result.payloadReceivedTimestampsList.map { it.toInstant() }
  }

  @CanIgnoreReturnValue
  private fun recordBandwidth(): Boolean {
    val startTime =
      testStartTime
        ?: run {
          loge(TAG, "No recorded testStartTime found.")
          return false
        }

    val bandwidth =
      try {
        calculateBandwidthBytesPerSecond(config, startTime, payloadTimeInstants)
      } catch (e: IllegalArgumentException) {
        loge(TAG, "Failed to calculate bandwidth.", e)
        return false
      }

    logd(TAG, "Test finished. Bandwidth is ${bandwidth}B/s.")
    return true
  }

  private fun reset() {
    counter = 0
    testStartTime = null
    payloadTimeInstants.clear()
    state = State.PENDING_CONFIG
  }

  /** Data class that matches the Config fields of [HowitzerMessage]. */
  data class HowitzerConfig(
    val sendPayloadFromIhu: Boolean,
    val payloadSize: Int,
    val payloadCount: Int,
    val testId: UUID,
  ) {
    internal fun toConfigProto(): Config {
      return Config.newBuilder()
        .setTestId(testId.toString())
        .setSendPayloadFromIhu(sendPayloadFromIhu)
        .setPayloadSize(payloadSize)
        .setPayloadCount(payloadCount)
        .build()
    }
  }

  companion object {
    private const val TAG = "ConnectionHowitzer"
    private val FEATURE_ID = ParcelUuid.fromString("b75d6a81-635b-4560-bd8d-9cdf83f32ae7")

    /** This function throws IllegalArgumentException when inputs are problematic. */
    internal fun calculateBandwidthBytesPerSecond(
      config: HowitzerConfig,
      starTime: Instant,
      payloadTimestamps: List<Instant>
    ): Double {
      require(config.payloadSize > 0 && config.payloadCount > 0) {
        "Cannot calculate bandwidth: both payloadSize and payloadCount must be positive; " +
          "payloadSize=${config.payloadSize}, payloadCount=${config.payloadCount}."
      }

      require(payloadTimestamps.isNotEmpty()) {
        "Cannot calculate bandwidth: payloadTimestamps is empty."
      }

      val timeElapsed = Duration.between(starTime, payloadTimestamps.last())
      require(!timeElapsed.isNegative && !timeElapsed.isZero) {
        "Cannot calculate bandwidth: test start time must be earlier than " +
          "last payload received time."
      }

      return (config.payloadCount * config.payloadSize / timeElapsed.convertToSeconds())
    }

    // We need duration accuracy down to milliseconds and B/s as bandwidth unit.
    // toSeconds() is introduced in API 31 which is higher than our minimum required API level.
    internal fun Duration.convertToSeconds() = toNanos() * 10.0.pow(-9.0)

    internal fun Timestamp.toInstant() = Instant.ofEpochSecond(seconds, nanos.toLong())

    internal fun Instant.toTimestamp() =
      Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()
  }
}
