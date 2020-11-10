package com.google.android.connecteddevice.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Table entity representing an challenge key for an associated device reconnection advertisement.
 */
@Entity(tableName = "associated_devices_challenge_secrets")
public class AssociatedDeviceChallengeSecretEntity {

  /** Id of the device. */
  @PrimaryKey @NonNull public String id;

  /** Encrypted challenge key. */
  @NonNull public String encryptedChallengeSecret;

  public AssociatedDeviceChallengeSecretEntity(String id, String encryptedChallengeSecret) {
    this.id = id;
    this.encryptedChallengeSecret = encryptedChallengeSecret;
  }
}
