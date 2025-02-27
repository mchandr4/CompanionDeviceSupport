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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Queries for associated device table. */
@Dao
public interface AssociatedDeviceDao {

  /** Gets an associated device based on device id. */
  @Query("SELECT * FROM associated_devices WHERE id LIKE :deviceId LIMIT 1")
  suspend fun getAssociatedDevice(deviceId: String): AssociatedDeviceEntity?

  /** Gets all AssociatedDeviceEntities associated with a user. */
  @Query("SELECT * FROM associated_devices WHERE userId LIKE :userId")
  suspend fun getAssociatedDevicesForUser(userId: Int): List<AssociatedDeviceEntity>

  /** Gets all AssociatedDeviceEntities. */
  @Query("SELECT * FROM associated_devices")
  suspend fun getAllAssociatedDevices(): List<AssociatedDeviceEntity>

  /**
   * Adds an AssociatedDeviceEntity. Replaces if a device already exists with the same device id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addOrReplaceAssociatedDevice(associatedDevice: AssociatedDeviceEntity)

  /** Removes the AssociatedDeviceEntity. */
  @Delete suspend fun removeAssociatedDevice(connectedDevice: AssociatedDeviceEntity)

  /** Gets the key associated with a device id. */
  @Query("SELECT * FROM associated_device_keys WHERE id LIKE :deviceId LIMIT 1")
  suspend fun getAssociatedDeviceKey(deviceId: String): AssociatedDeviceKeyEntity?

  /**
   * Adds an AssociatedDeviceKeyEntity. Replaces if a device key already exists with the same device
   * id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addOrReplaceAssociatedDeviceKey(keyEntity: AssociatedDeviceKeyEntity)

  /** Removes the AssociatedDeviceKeyEntity. */
  @Delete suspend fun removeAssociatedDeviceKey(keyEntity: AssociatedDeviceKeyEntity)

  /** Gets the challenge secret associated with a device id. */
  @Query("SELECT * FROM associated_devices_challenge_secrets WHERE id LIKE :deviceId LIMIT 1")
  suspend fun getAssociatedDeviceChallengeSecret(
    deviceId: String
  ): AssociatedDeviceChallengeSecretEntity?

  /**
   * Adds an AssociatedDeviceChallengeSecretEntity. Replaces if a secret already exists with the
   * same device id.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addOrReplaceAssociatedDeviceChallengeSecret(
    challengeSecretEntity: AssociatedDeviceChallengeSecretEntity
  )

  /** Removes the AssociatedDeviceChallengeSecretEntity. */
  @Delete
  suspend fun removeAssociatedDeviceChallengeSecret(
    challengeSecretEntity: AssociatedDeviceChallengeSecretEntity
  )
}
