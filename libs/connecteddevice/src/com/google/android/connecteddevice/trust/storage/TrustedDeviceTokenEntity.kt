package com.google.android.connecteddevice.trust.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Table entity representing the hashed escrow token for a trusted device. */
@Entity(tableName = "trusted_device_tokens")
data class TrustedDeviceTokenEntity(
  /** Device id of the trusted device. */
  @PrimaryKey val id: String,
  /** Base64 encoding of hashed token. */
  @ColumnInfo(name = "hashed_token") val hashedToken: String
)
