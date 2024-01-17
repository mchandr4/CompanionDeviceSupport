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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.connecteddevice.api.CompanionConnector
import com.google.android.connecteddevice.api.Connector.Companion.USER_TYPE_ALL
import com.google.android.connecteddevice.util.SafeLog.logd

/** Container Service for the [ConnectionHowitzerFeature]. */
class ConnectionHowitzerService : Service() {
  private lateinit var feature: ConnectionHowitzerFeature

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    logd(TAG, "Creating ConnectionHowitzer feature.")
    feature =
      ConnectionHowitzerFeature(CompanionConnector(context = this, userType = USER_TYPE_ALL))
        .apply { start() }
  }

  override fun onDestroy() {
    super.onDestroy()
    feature.stop()
  }

  companion object {
    private const val TAG = "ConnectionHowitzerService"
  }
}
