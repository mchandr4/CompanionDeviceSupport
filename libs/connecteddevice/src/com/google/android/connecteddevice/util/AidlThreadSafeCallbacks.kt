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

package com.google.android.connecteddevice.util

import android.os.DeadObjectException
import android.os.IBinder
import android.os.IInterface
import com.google.android.connecteddevice.util.SafeLog.logw

/**
 * An AIDL-specific implementation of [ThreadSafeCallbacks] that handles [IBinder]
 * lifecycles and guards against those pesky [DeadObjectException]s.
 */
class AidlThreadSafeCallbacks<T> : ThreadSafeCallbacks<T>() where T : IInterface {

  override fun contains(callback: T): Boolean {
    return callbacks.any { callback.asBinder() == it.key.asBinder() }
  }

  override fun remove(callback: T) {
    val registeredCallback =
      callbacks.keys.firstOrNull { callback.asBinder() == it.asBinder() } ?: run {
        logw(TAG, "Unable to find a matching binder to the callback. Ignoring request to remove.")
        return
      }

    callbacks.remove(registeredCallback)
  }

  override fun invoke(notification: SafeConsumer<T>) {
    for ((callback, executor) in callbacks.entries) {
      if (!callback.asBinder().isBinderAlive) {
        logw(TAG, "A binder has died. Removing from the registered callbacks.")
        callbacks.remove(callback)
        continue
      }
      executor.execute { notification.accept(callback) }
    }
  }

  companion object {
    private const val TAG = "AidlThreadSafeCallbacks"
  }
}
