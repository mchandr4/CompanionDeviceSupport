/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.connecteddevice.util

import android.os.IBinder
import android.os.RemoteException
import com.google.android.connecteddevice.util.SafeLog.logw

object BinderUtils {
  private const val TAG = "BinderUtils"

  /**
   * [listener] will be notified when the remote process where the [IBinder] coming from is dead.
   *
   * Returns `true` if the registration succeeded; `false` otherwise, e.g. the remote process is
   * already dead.
   */
  fun IBinder.registerBinderDiedListener(listener: OnBinderDiedListener): Boolean {
    val binderDeathRecipient: IBinder.DeathRecipient =
      object : IBinder.DeathRecipient {
        override fun binderDied() {
          listener.onBinderDied()
          unlinkToDeath(this, /* flags= */ 0)
        }
      }
    try {
      linkToDeath(binderDeathRecipient, /* flags= */ 0)
    } catch (e: RemoteException) {
      logw(TAG, "Remote process already died. Failed to register IBinder death listener.")
      return false
    }
    return true
  }
}

fun interface OnBinderDiedListener {
  fun onBinderDied()
}
