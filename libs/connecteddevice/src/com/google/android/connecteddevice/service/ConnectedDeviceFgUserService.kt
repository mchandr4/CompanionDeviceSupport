/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.connecteddevice.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.UserManager
import com.google.android.connecteddevice.api.CompanionConnector
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.api.IConnectedDeviceManager
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge

/**
 * Service responsible for starting services that must be run in the active foreground user.
 * External features running in the foreground user may bind with `ACTION_BIND_REMOTE_FEATURE` to
 * access the [IConnectedDeviceManager] instance.
 */
class ConnectedDeviceFgUserService : TrunkService() {
  private lateinit var connector: CompanionConnector

  private var receiversRegistered = false

  @SuppressLint("UnprotectedReceiver") // Broadcasts are protected.
  override fun onCreate() {
    super.onCreate()
    connector = CompanionConnector(this)
    connector.callback =
      object : Connector.Callback {
        override fun onDisconnected() {
          loge(TAG, "Lost connection to companion. Stopping service.")
          stopSelf()
        }

        override fun onFailedToConnect() {
          loge(TAG, "Unable to establish connection with companion. Stopping service.")
          stopSelf()
        }
      }
    connector.connect()

    // Listen to the unlock event to know when a new user has come to the foreground and storage is
    // unlocked.
    registerReceiver(userUnlockedReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
    // Listen for user going to the background so we can clean up.
    registerReceiver(userBackgroundReceiver, IntentFilter(Intent.ACTION_USER_BACKGROUND))
    receiversRegistered = true

    val userManager = getSystemService(UserManager::class.java)
    if (userManager.isUserUnlocked) {
      logd(TAG, "User was already unlocked on service start.")
      onUserUnlocked()
    }
  }

  override fun onDestroy() {
    connector.callback = null
    connector.disconnect()
    unregisterReceivers()
    logd(TAG, "Service was destroyed.")
    super.onDestroy()
  }

  override fun onBind(intent: Intent): IBinder? {
    val action = intent.action ?: return null
    logd(TAG, "Service bound. Action: $action")
    return connector.binderForAction(action)
  }

  private fun onUserUnlocked() {
    logd(TAG, "Starting unlock branch services.")
    startBranchServices(META_UNLOCK_SERVICES)
  }

  private fun unregisterReceivers() {
    if (receiversRegistered) {
      receiversRegistered = false
      unregisterReceiver(userUnlockedReceiver)
      unregisterReceiver(userBackgroundReceiver)
    }
  }

  private val userUnlockedReceiver: BroadcastReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        logd(TAG, "User has been unlocked.")
        onUserUnlocked()
      }
    }
  private val userBackgroundReceiver: BroadcastReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        logd(TAG, "User has been placed into the background. Stopping foreground user services.")
        unregisterReceivers()
        stopBranchServices()
        stopSelf()
      }
    }

  companion object {
    private const val TAG = "ConnectedDeviceFgUserService"

    /** `string-array` List of services to start after the user has unlocked. */
    private const val META_UNLOCK_SERVICES = "com.google.android.connecteddevice.unlock_services"
  }
}
