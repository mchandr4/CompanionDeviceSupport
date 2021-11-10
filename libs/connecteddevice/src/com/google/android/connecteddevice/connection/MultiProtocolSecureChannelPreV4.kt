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

import com.google.android.connecteddevice.oob.OobRunner
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.encryptionrunner.EncryptionRunner
import java.util.zip.Inflater

/** Handles specific handshake logic before security V4. */
open class MultiProtocolSecureChannelPreV4(
  stream: ProtocolStream,
  storage: ConnectedDeviceStorage,
  encryptionRunner: EncryptionRunner,
  oobRunner: OobRunner? = null,
  deviceId: String? = null,
  inflater: Inflater = Inflater(),
) : MultiProtocolSecureChannel(stream, storage, encryptionRunner, oobRunner, deviceId, inflater) {

  override fun processOobVerificationCode(oobCode: ByteArray) {
    invokeVerificationCodeListener()
  }

  override fun processVerificationCodeMessage(message: ByteArray) {
    loge(
      TAG,
      "Verification code message will only be received under security version 4. notify callback" +
        " of failure."
    )
    notifySecureChannelFailure(ChannelError.CHANNEL_ERROR_INVALID_STATE)
  }

  override fun createOobResponse(code: ByteArray): ByteArray {
    // Raw bytes of the OOB code is expected by remote device before V4.
    return code
  }

  override fun processVisualVerificationConfirmed() {
    // Verification confirmation message is not needed before V4.
  }

  companion object {
    private const val TAG = "MultiProtocolSecureChannelPreV4"
  }
}
