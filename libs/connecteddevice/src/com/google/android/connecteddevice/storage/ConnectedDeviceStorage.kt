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

package com.google.android.connecteddevice.storage

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.companionprotos.DeviceOS
import com.google.android.connecteddevice.model.AssociatedDevice
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.android.connecteddevice.util.SafeLog.logw
import com.google.android.connecteddevice.util.ThreadSafeCallbacks
import java.security.InvalidKeyException
import java.security.InvalidParameterException
import java.security.NoSuchAlgorithmException
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Storage for connected devices in a car. */
open class ConnectedDeviceStorage(
  private val context: Context,
  private val cryptoHelper: CryptoHelper,
  private val associatedDeviceDatabase: AssociatedDeviceDao,
  private val callbackExecutor: Executor,
) {
  /** Callback for association device related events. */
  interface AssociatedDeviceCallback {
    fun onAssociatedDeviceAdded(device: AssociatedDevice)

    fun onAssociatedDeviceRemoved(device: AssociatedDevice)

    fun onAssociatedDeviceUpdated(device: AssociatedDevice)
  }

  /** Listener for retrieving devices associated with the active user. */
  interface OnAssociatedDevicesRetrievedListener {
    /** Triggered when the devices associated with the active user are retrieved. */
    fun onAssociatedDevicesRetrieved(devices: List<AssociatedDevice>)
  }

  private val sharedPreferences: SharedPreferences by lazy {
    // This should be called only after user 0 is unlocked.
    context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
  }
  private val callbacks = ThreadSafeCallbacks<AssociatedDeviceCallback>()

  constructor(
    context: Context
  ) : this(
    context,
    KeyStoreCryptoHelper(),
    Room.databaseBuilder(context, ConnectedDeviceDatabase::class.java, DATABASE_NAME)
      .addMigrations(MIGRATION_2_3)
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()
      .associatedDeviceDao(),
    Executors.newSingleThreadExecutor(),
  )

  open val uniqueId: UUID by lazy {
    var uuid: UUID? = sharedPreferences.getString(UNIQUE_ID_KEY, null)?.let { UUID.fromString(it) }

    if (uuid == null) {
      uuid = UUID.randomUUID()
      sharedPreferences.edit().putString(UNIQUE_ID_KEY, uuid.toString()).apply()
      logd(TAG, "Generated new trusted unique id: $uuid")
    }

    uuid
  }

  /** Registers an [AssociatedDeviceCallback] for associated device updates. */
  open fun registerAssociatedDeviceCallback(callback: AssociatedDeviceCallback) {
    callbacks.add(callback, callbackExecutor)
  }

  /** Unregisters an [AssociatedDeviceCallback] from associated device updates. */
  open fun unregisterAssociatedDeviceCallback(callback: AssociatedDeviceCallback) {
    callbacks.remove(callback)
  }

  /** Returns the encryption key for [deviceId]; `null` if not recognized. */
  open suspend fun getEncryptionKey(deviceId: String): ByteArray? {
    val entity = associatedDeviceDatabase.getAssociatedDeviceKey(deviceId)
    if (entity == null) {
      logd(TAG, "Encryption key not found!")
      return null
    }
    return cryptoHelper.decrypt(entity.encryptedKey)
  }

  /** Saves the encryption key for the given deviceId. */
  open suspend fun saveEncryptionKey(deviceId: String, encryptionKey: ByteArray) {
    val encryptedKey = cryptoHelper.encrypt(encryptionKey)
    val entity = AssociatedDeviceKeyEntity(deviceId, encryptedKey)

    associatedDeviceDatabase.addOrReplaceAssociatedDeviceKey(entity)
    logd(TAG, "Successfully wrote encryption key for $deviceId.")
  }

  /**
   * Saves the challenge secret for the given deviceId.
   *
   * @param secret Secret associated with this device. Note: must be [CHALLENGE_SECRET_BYTES] bytes
   *   in length or an [InvalidParameterException] will be thrown.
   */
  open suspend fun saveChallengeSecret(deviceId: String, secret: ByteArray) {
    if (secret.size != CHALLENGE_SECRET_BYTES) {
      throw InvalidParameterException("Secrets must be $CHALLENGE_SECRET_BYTES bytes in length.")
    }
    val encryptedKey = cryptoHelper.encrypt(secret)
    val entity = AssociatedDeviceChallengeSecretEntity(deviceId, encryptedKey)

    associatedDeviceDatabase.addOrReplaceAssociatedDeviceChallengeSecret(entity)
    logd(TAG, "Successfully wrote challenge secret for $deviceId.")
  }

  /** Returns the challenge secret associated with the deviceId; `null` if not recognized. */
  suspend fun getChallengeSecret(deviceId: String): ByteArray? {
    val entity = associatedDeviceDatabase.getAssociatedDeviceChallengeSecret(deviceId)
    if (entity == null) {
      logd(TAG, "Challenge secret not found!")
      return null
    }

    return cryptoHelper.decrypt(entity.encryptedChallengeSecret)
  }

  /**
   * Hashes the [value] with device's challenge secret and returns result. Returns [null] if
   * unsuccessful.
   */
  open suspend fun hashWithChallengeSecret(deviceId: String, value: ByteArray): ByteArray? {
    val challengeSecret = getChallengeSecret(deviceId)
    if (challengeSecret == null) {
      loge(TAG, "Unable to find challenge secret for device $deviceId.")
      return null
    }

    val mac =
      try {
        Mac.getInstance(CHALLENGE_HASHING_ALGORITHM)
      } catch (e: NoSuchAlgorithmException) {
        loge(TAG, "Unable to find hashing algorithm $CHALLENGE_HASHING_ALGORITHM", e)
        return null
      }

    val keySpec = SecretKeySpec(challengeSecret, CHALLENGE_HASHING_ALGORITHM)
    try {
      mac.init(keySpec)
    } catch (e: InvalidKeyException) {
      loge(TAG, "Exception while initializing HMAC.", e)
      return null
    }

    return mac.doFinal(value)
  }

  /** Gets a list of all the associated devices. */
  open suspend fun getAllAssociatedDevices(): List<AssociatedDevice> {
    val entities = associatedDeviceDatabase.getAllAssociatedDevices() ?: emptyList()

    return entities.map { it.toAssociatedDevice() }
  }

  /** Gets a list of associated devices for the given user. */
  suspend fun getAssociatedDevicesForUser(userId: Int): List<AssociatedDevice> {
    val entities = associatedDeviceDatabase.getAssociatedDevicesForUser(userId) ?: emptyList()

    return entities.map { it.toAssociatedDevice() }
  }

  /** Gets a list of associated devices for the current driver. */
  open suspend fun getDriverAssociatedDevices(): List<AssociatedDevice> {
    return getAssociatedDevicesForUser(ActivityManager.getCurrentUser())
  }

  /** Gets a list of associated devices for all passengers. */
  open suspend fun getPassengerAssociatedDevices(): List<AssociatedDevice> {
    return getAssociatedDevicesNotBelongingToUser(ActivityManager.getCurrentUser())
  }

  // Used by test.
  internal suspend fun getAssociatedDevicesNotBelongingToUser(userId: Int): List<AssociatedDevice> {
    val entities = associatedDeviceDatabase.getAllAssociatedDevices() ?: emptyList()

    return entities.filter { it.userId != userId }.map { it.toAssociatedDevice() }
  }

  /** Returns a list of device ids of associated devices for the given userId. */
  open suspend fun getAssociatedDeviceIdsForUser(userId: Int): List<String> {
    val userDevices = getAssociatedDevicesForUser(userId)

    return userDevices.map { it.id }
  }

  /** Returns a list of device ids of associated devices for the current driver. */
  suspend fun getDriverAssociatedDeviceIds(): List<String> {
    return getAssociatedDeviceIdsForUser(ActivityManager.getCurrentUser())
  }

  /** Returns a list of device ids of associated devices for all passengers. */
  suspend fun getPassengerAssociatedDeviceIds(): List<String> {
    return getAssociatedDeviceIdsNotBelongingToUser(ActivityManager.getCurrentUser())
  }

  internal suspend fun getAssociatedDeviceIdsNotBelongingToUser(userId: Int): List<String> {
    val entities = associatedDeviceDatabase.getAllAssociatedDevices() ?: emptyList()

    return entities.filter { it.userId != userId }.map { it.id }
  }

  /** Adds the associated device of the given deviceId for the current driver. */
  open suspend fun addAssociatedDeviceForDriver(device: AssociatedDevice) {
    addAssociatedDeviceForUser(ActivityManager.getCurrentUser(), device)
  }

  /** Adds the associated device for the given user. */
  open suspend fun addAssociatedDeviceForUser(userId: Int, device: AssociatedDevice) {
    val entity = AssociatedDeviceEntity(userId, device, /* isConnectionEnabled= */ true)
    addOrReplaceAssociatedDevice(entity)
    callbacks.invoke { it.onAssociatedDeviceAdded(device) }
  }

  /** Updates the name for an associated device. */
  open suspend fun updateAssociatedDeviceName(deviceId: String, name: String) {
    if (name.isEmpty()) {
      logw(TAG, "Attempted to update the device name to an empty string. Ignoring.")
      return
    }
    updateAssociatedDevice(deviceId) { it.name = name }
  }

  /**
   * Sets the name for an associated device only if it does not already have a name populated.
   *
   * Empty names are ignored.
   */
  suspend fun setAssociatedDeviceName(deviceId: String, name: String) {
    if (name.isEmpty()) {
      logw(TAG, "Attempted to set the device name to an empty string. Ignoring.")
      return
    }
    updateAssociatedDevice(deviceId) { entity ->
      if (entity.name != null) {
        logd(TAG, "Name was already set for device $deviceId. No further action taken.")
        return@updateAssociatedDevice
      }
      entity.name = name
    }
  }

  open suspend fun updateAssociatedDeviceOs(deviceId: String, deviceOs: DeviceOS) {
    updateAssociatedDevice(deviceId) { it.os = deviceOs }
  }

  open suspend fun updateAssociatedDeviceOsVersion(deviceId: String, deviceOsVersion: String) {
    updateAssociatedDevice(deviceId) { it.osVersion = deviceOsVersion }
  }

  open suspend fun updateAssociatedDeviceCompanionSdkVersion(deviceId: String, sdkVersion: String) {
    updateAssociatedDevice(deviceId) { it.companionSdkVersion = sdkVersion }
  }

  /** Removes the associated device of the given deviceId. */
  open suspend fun removeAssociatedDevice(deviceId: String) {
    val entity = associatedDeviceDatabase.getAssociatedDevice(deviceId)
    if (entity == null) {
      return
    }
    associatedDeviceDatabase.removeAssociatedDevice(entity)
    callbacks.invoke { it.onAssociatedDeviceRemoved(entity.toAssociatedDevice()) }
  }

  /** Sets if connection is enabled for an associated device. */
  open suspend fun updateAssociatedDeviceConnectionEnabled(
    deviceId: String,
    isConnectionEnabled: Boolean,
  ) {
    updateAssociatedDevice(deviceId) { it.isConnectionEnabled = isConnectionEnabled }
  }

  /** Returns the associated device with the given deviceId. */
  open suspend fun getAssociatedDevice(deviceId: String): AssociatedDevice? {
    val entity = associatedDeviceDatabase.getAssociatedDevice(deviceId)
    if (entity == null) {
      logw(TAG, "No device has been associated with device id $deviceId. Returning null.")
      return null
    }
    return entity.toAssociatedDevice()
  }

  /** Updates the identified associated device to be claimed by the current user. */
  open suspend fun claimAssociatedDevice(deviceId: String) {
    logd(TAG, "Claiming device $deviceId for the current user.")
    updateAssociatedDevice(deviceId) { it.userId = ActivityManager.getCurrentUser() }
  }

  /** Removes the claim on the identified associated device leaving it in an unclaimed state. */
  open suspend fun removeAssociatedDeviceClaim(deviceId: String) {
    logd(TAG, "Removing the user claim for device $deviceId.")
    updateAssociatedDevice(deviceId) { it.userId = AssociatedDevice.UNCLAIMED_USER_ID }
  }

  private suspend fun updateAssociatedDevice(
    deviceId: String,
    update: (entity: AssociatedDeviceEntity) -> Unit,
  ) {
    val entity = associatedDeviceDatabase.getAssociatedDevice(deviceId)
    if (entity == null) {
      logw(TAG, "Could not retrieve device with $deviceId. Ignoring.")
      return
    }

    update(entity)

    addOrReplaceAssociatedDevice(entity)
    callbacks.invoke { it.onAssociatedDeviceUpdated(entity.toAssociatedDevice()) }
  }

  private suspend fun addOrReplaceAssociatedDevice(entity: AssociatedDeviceEntity) {
    // Needed for database 2_3 migration.
    if (entity.os == null) {
      entity.os = DeviceOS.DEVICE_OS_UNKNOWN
    }
    associatedDeviceDatabase.addOrReplaceAssociatedDevice(entity)
  }

  companion object {
    private const val TAG = "CompanionStorage"

    private const val SHARED_PREFS_NAME = "com.google.android.connecteddevice"
    private const val UNIQUE_ID_KEY = "CTABM_unique_id"
    private const val DATABASE_NAME = "connected-device-database"

    private const val CHALLENGE_HASHING_ALGORITHM = "HmacSHA256"

    const val CHALLENGE_SECRET_BYTES = 32

    // Database migration from version 2 to 3.
    // This migration adds the os, osVersion, and companionSdkVersion columns to the
    // associated_devices table.
    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
          database.execSQL(
            "ALTER TABLE associated_devices ADD os TEXT NOT NULL DEFAULT 'DEVICE_OS_UNKNOWN';"
          )
          database.execSQL("ALTER TABLE associated_devices ADD osVersion TEXT;")
          database.execSQL("ALTER TABLE associated_devices ADD companionSdkVersion TEXT;")
        }
      }
  }
}
