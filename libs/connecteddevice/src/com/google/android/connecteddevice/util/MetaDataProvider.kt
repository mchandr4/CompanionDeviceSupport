package com.google.android.connecteddevice.util

/** Interface providing access to the service's meta data. */
interface MetaDataProvider {
  /**
   * Return a string from the service's meta-data, or default value if no meta-data matches the
   * provided name.
   */
  fun getMetaString(name: String, defaultValue: String): String

  /**
   * Return a boolean from the service's meta-data, or default value if no meta-data matches the
   * provided name.
   */
  fun getMetaBoolean(name: String, defaultValue: Boolean): Boolean

  /**
   * Return an integer from the service's meta-data, or default value if no meta-data matches the
   * provided name.
   */
  fun getMetaInt(name: String, defaultValue: Int): Int

  /**
   * Return a string array from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  fun getMetaStringArray(
    name: String,
    defaultValue: Array<String>,
  ): Array<String>
}
