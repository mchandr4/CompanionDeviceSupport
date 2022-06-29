package com.google.android.connecteddevice.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.google.android.connecteddevice.util.SafeLog.logd

/** Provides util functions to retrieve meta data from [context]'s bundle. */
class MetaDataParser(private val context: Context) : MetaDataProvider {
  private val bundle: Bundle

  init {
    logd(TAG, "Retrieving meta-data from context.")
    val service = ComponentName(context, context.javaClass)
    bundle =
      try {
        context.packageManager.getServiceInfo(service, PackageManager.GET_META_DATA).metaData
      } catch (e: PackageManager.NameNotFoundException) {
        throw IllegalStateException("Unable to read meta-data.", e)
      }
  }

  override fun getMetaString(name: String, defaultValue: String): String {
    return if (bundle.containsKey(name)) {
      context.getString(bundle.getInt(name))
    } else {
      defaultValue
    }
  }

  override fun getMetaBoolean(name: String, defaultValue: Boolean): Boolean {
    return if (bundle.containsKey(name)) {
      context.resources.getBoolean(bundle.getInt(name))
    } else {
      defaultValue
    }
  }

  override fun getMetaInt(name: String, defaultValue: Int): Int {
    return if (bundle.containsKey(name)) {
      context.resources.getInteger(bundle.getInt(name))
    } else {
      defaultValue
    }
  }

  override fun getMetaStringArray(
    name: String,
    defaultValue: Array<String>,
  ): Array<String> {
    return if (bundle.containsKey(name)) {
      context.resources.getStringArray(bundle.getInt(name))
    } else {
      defaultValue
    }
  }

  companion object {
    private const val TAG = "MetaDataParser"
  }
}
