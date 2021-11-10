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
package com.google.android.connecteddevice.connection

import com.google.android.companionprotos.VerificationCode
import com.google.android.companionprotos.VerificationCodeState
import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.encryptionrunner.EncryptionRunner
import com.google.protobuf.ByteString

/** Handles specific handshake logic for security V4 and plus. */
class MultiProtocolSecureChannelV4(
  stream: ProtocolStream,
  storage: ConnectedDeviceStorage,
  encryptionRunner: EncryptionRunner,
  oobRunner: OobRunner? = null,
  deviceId: String? = null,
) : MultiProtocolSecureChannel(stream, storage, encryptionRunner, oobRunner, deviceId) {
  override fun processVerificationCodeMessage(message: ByteArray) {
    val verificationMessage = VerificationCode.parseFrom(message)
    when (verificationMessage.state) {
      VerificationCodeState.OOB_VERIFICATION ->
        confirmOobVerificationCode(verificationMessage.payload.toByteArray())
      VerificationCodeState.VISUAL_VERIFICATION -> invokeVerificationCodeListener()
      else -> {
        loge(TAG, "Unexpected verification message received, issue error callback.")
        notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
      }
    }
  }

  override fun createOobResponse(code: ByteArray) =
    VerificationCode.newBuilder()
      .run {
        setState(VerificationCodeState.OOB_VERIFICATION)
        setPayload(ByteString.copyFrom(code))
        build()
      }
      .toByteArray()

  override fun processVisualVerificationConfirmed() {
    val confirmationMessage =
      VerificationCode.newBuilder().run {
        setState(VerificationCodeState.VISUAL_CONFIRMATION)
        build()
      }
    sendHandshakeMessage(confirmationMessage.toByteArray())
  }

  override fun processOobVerificationCode(oobCode: ByteArray) {
    this.oobCode = oobCode
    logd(TAG, "Waiting for verification code message.")
  }

  companion object {
    private const val TAG = "MultiProtocolSecureChannelV4"
  }
}
