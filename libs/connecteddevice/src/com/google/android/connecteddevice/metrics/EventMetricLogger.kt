package com.google.android.connecteddevice.metrics

import android.content.Context
import android.os.Build

/** Logger to report Companion events to statsd */
class EventMetricLogger(private val context: Context) {
  private val uid = context.packageManager.getPackageUid(context.packageName, 0)
  fun onSecureChannelEstablished() {
    // TODO(b/273968556): unable to find related System API on Android Q build.
    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R ) return
    CompanionStatsLog.write(
      CompanionStatsLog.COMPANION_STATUS_CHANGED,
      uid,
      CompanionStatsLog.COMPANION_STATUS_CHANGED__COMPANION_STATUS__SECURE_CHANNEL_ESTABLISHED,
      0
    )
  }
}
