package com.google.android.connecteddevice.metrics

import android.content.Context
import com.google.android.connecteddevice.model.Errors
import android.os.RemoteException
import com.google.android.connecteddevice.util.SafeLog.loge
import java.time.Duration
import java.time.Instant

/** Logger to report Companion events to statsd */
class EventMetricLogger(private val context: Context) {
  private val uid = context.packageManager.getPackageUid(context.packageName, 0)
  /** The time when association or reconnect discovery get started. */
  private var discoveryStartedTime = Instant.EPOCH
  private var connectedTime = Instant.EPOCH
  private val defaultDuration = Duration.ZERO

  /** Pushes the association discovery started event to statsd. */
  fun pushAssociationStartedEvent() {
    pushEventsWithoutException(
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__ASSOCIATION_STARTED,
      defaultDuration
    )
  }

  /** Pushes the reconnection discovery started event to statsd. */
  fun pushDiscoveryStartedEvent() {
    discoveryStartedTime = Instant.now()
    pushEventsWithoutException(
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__DISCOVERY_STARTED,
      defaultDuration
    )
  }

  /** Pushes device get reconnected event to statsd. */
  fun pushConnectedEvent() {
    connectedTime = Instant.now()
    pushEventsWithoutException(
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__CONNECTED,
      Duration.between(discoveryStartedTime, connectedTime)
    )
  }

  /** Pushes the secure channel established event to statsd. */
  fun pushSecureChannelEstablishedEvent() {
    pushEventsWithoutException(
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__SECURE_CHANNEL_ESTABLISHED,
      Duration.between(connectedTime, Instant.now())
    )
  }

  /** Pushes device disconnected event to statsd. */
  fun pushDisconnectedEvent() {
    pushEventsWithoutException(
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__DISCONNECTED,
      defaultDuration
    )
  }

  // TODO(b/298248724): Reports more errors.
  fun pushCompanionErrorEvent(error: Int, duringAssociation: Boolean = false) {
    val errorId =
      when (error) {
        Errors.DEVICE_ERROR_INVALID_HANDSHAKE ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_HANDSHAKE
        Errors.DEVICE_ERROR_INVALID_MSG ->
          CompanionStatsLog.COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_MSG
        Errors.DEVICE_ERROR_INVALID_DEVICE_ID ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_DEVICE_ID
        Errors.DEVICE_ERROR_INVALID_VERIFICATION ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_VERIFICATION
        Errors.DEVICE_ERROR_INVALID_CHANNEL_STATE ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_CHANNEL_STATE
        Errors.DEVICE_ERROR_INVALID_ENCRYPTION_KEY ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_ENCRYPTION_KEY
        Errors.DEVICE_ERROR_STORAGE_FAILURE ->
          CompanionStatsLog.COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_STORAGE_FAILURE
        Errors.DEVICE_ERROR_INVALID_SECURITY_KEY ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INVALID_SECURITY_KEY
        Errors.DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_INSECURE_RECIPIENT_ID_DETECTED
        Errors.DEVICE_ERROR_UNEXPECTED_DISCONNECTION ->
          CompanionStatsLog
            .COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_UNEXPECTED_DISCONNECTION
        else ->
          CompanionStatsLog.COMPANION_ERROR_REPORTED__COMPANION_ERROR__DEVICE_ERROR_UNSPECIFIED
      }
    try {
      CompanionStatsLog.write(
        CompanionStatsLog.COMPANION_ERROR_REPORTED,
        uid,
        errorId,
        duringAssociation
      )
    } catch (e: NoClassDefFoundError) {
      loge(TAG, "Encounter error when pushing Companion error events to statsd; skip.")
    }
  }

  private fun pushEventsWithoutException(eventId: Int, duration: Duration) {
    try {
      CompanionStatsLog.write(
        CompanionStatsLog.COMPANION_STATUS_CHANGED, uid, eventId, duration.toMillis())
    } catch (e: NoClassDefFoundError) {
      loge(TAG, "Encounter error when pushing Companion events to statsd; skip.")
    }
  }

  companion object {
    private const val TAG = "EventMetricLogger"
  }
}
