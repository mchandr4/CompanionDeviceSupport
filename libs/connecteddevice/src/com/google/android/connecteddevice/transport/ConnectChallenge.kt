package com.google.android.connecteddevice.transport

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Container class to hold the connect challenge the salt that generated the challenge. */
@Parcelize
data class ConnectChallenge(val challenge: ByteArray, val salt: ByteArray) : Parcelable {
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is ConnectChallenge) {
      return false
    }

    return challenge.contentEquals(other.challenge) && salt.contentEquals(other.salt)
  }

  override fun hashCode(): Int {
    var result = challenge.contentHashCode()
    result = 31 * result + salt.contentHashCode()
    return result
  }
}
