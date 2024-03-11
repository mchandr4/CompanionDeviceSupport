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

import com.google.android.connecteddevice.connectionhowitzer.proto.Config
import com.google.android.connecteddevice.connectionhowitzer.proto.HowitzerMessage
import com.google.android.connecteddevice.connectionhowitzer.proto.Result
import com.google.protobuf.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

internal fun createConfig(
  sendPayloadFromIhu: Boolean = true,
  payloadSize: Int = 10,
  payloadCount: Int = 1,
  testId: UUID,
) = ConnectionHowitzerFeature.HowitzerConfig(sendPayloadFromIhu, payloadSize, payloadCount, testId)

internal fun createConfigMessage(
  config: ConnectionHowitzerFeature.HowitzerConfig
): HowitzerMessage {
  val configMessage =
    Config.newBuilder().run {
      sendPayloadFromIhu = config.sendPayloadFromIhu
      payloadCount = config.payloadCount
      payloadSize = config.payloadSize
      testId = config.testId.toString()
      build()
    }
  return HowitzerMessage.newBuilder()
    .setMessageType(HowitzerMessage.MessageType.CONFIG)
    .setConfig(configMessage)
    .build()
}

internal fun createAckMessage() =
  HowitzerMessage.newBuilder().setMessageType(HowitzerMessage.MessageType.ACK).build()

internal fun createPayload(size: Int): ByteArray = ByteArray(size).apply { Random.nextBytes(this) }

internal fun createEmptyResult(config: ConnectionHowitzerFeature.HowitzerConfig): HowitzerMessage {
  val result = Result.newBuilder().run { build() }
  return HowitzerMessage.newBuilder()
    .setMessageType(HowitzerMessage.MessageType.RESULT)
    .setConfig(config.toConfigProto())
    .setResult(result)
    .build()
}

internal fun createResult(
  config: ConnectionHowitzerFeature.HowitzerConfig,
  valid: Boolean = true,
): HowitzerMessage {
  val startTime = Instant.now()
  val receivedLastPayloadTime = startTime.plusSeconds(1)
  val result =
    Result.newBuilder().run {
      isValid = valid
      addPayloadReceivedTimestamps(
        Timestamp.newBuilder().setSeconds(receivedLastPayloadTime.epochSecond).build()
      )
      testStartTimestamp = Timestamp.newBuilder().setSeconds(startTime.epochSecond).build()
      build()
    }
  return HowitzerMessage.newBuilder()
    .setMessageType(HowitzerMessage.MessageType.RESULT)
    .setConfig(config.toConfigProto())
    .setResult(result)
    .build()
}
