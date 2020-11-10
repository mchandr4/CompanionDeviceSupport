package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Service;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/** Service with convenience methods for using meta-data configuration. */
public abstract class MetaDataService extends Service {

  private static final String TAG = "MetaDataService";

  @VisibleForTesting
  Bundle bundle;

  @Override
  public void onCreate() {
    super.onCreate();
    ComponentName service = new ComponentName(this, this.getClass());
    try {
      bundle = getPackageManager().getServiceInfo(service, PackageManager.GET_META_DATA).metaData;
    } catch (NameNotFoundException e) {
      throw new IllegalStateException("Unable to read meta-data for service.", e);
    }
  }

  /**
   * Return a string from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  @Nullable
  protected final String getMetaString(@NonNull String name, @Nullable String defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return getResources().getString(bundle.getInt(name));
  }

  /**
   * Return a boolean from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  protected final boolean getMetaBoolean(@NonNull String name, boolean defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return getResources().getBoolean(bundle.getInt(name));
  }

  /**
   * Return an integer from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  protected final int getMetaInt(@NonNull String name, int defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return getResources().getInteger(bundle.getInt(name));
  }

  /**
   * Return a float from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  protected final float getMetaFloat(@NonNull String name, float defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return getResources().getFloat(bundle.getInt(name));
  }

  /**
   * Return a string array from the service's meta-data, or default value if no meta-data matches
   * the provided name.
   */
  @Nullable
  protected final String[] getMetaStringArray(
      @NonNull String name,
      @Nullable String[] defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return getResources().getStringArray(bundle.getInt(name));
  }

  /**
   * Return a resource id from the service's meta-data, or default value if no meta-data matches the
   * provided name.
   */
  protected final int getMetaResourceId(@NonNull String name, int defaultValue) {
    if (!bundle.containsKey(name)) {
      return defaultValue;
    }
    return bundle.getInt(name);
  }

  /**
   * Return a string from the service's meta-data. Throws an {@link IllegalArgumentException} if
   * no meta-data matches the provided name.
   */
  @NonNull
  protected final String requireMetaString(@NonNull String name) {
    requireName(name);
    return getResources().getString(bundle.getInt(name));
  }

  /**
   * Return a boolean from the service's meta-data. Throws an {@link IllegalArgumentException} if
   * no meta-data matches the provided name.
   */
  protected final boolean requireMetaBoolean(@NonNull String name) {
    requireName(name);
    return getResources().getBoolean(bundle.getInt(name));
  }

  /**
   * Return an integer from the service's meta-data. Throws an {@link IllegalArgumentException} if
   * no meta-data matches the provided name.
   */
  protected final int requireMetaInt(@NonNull String name) {
    requireName(name);
    return getResources().getInteger(bundle.getInt(name));
  }

  /**
   * Return a float from the service's meta-data. Throws an {@link IllegalArgumentException} if
   * no meta-data matches the provided name.
   */
  protected final float requireMetaFloat(@NonNull String name) {
    requireName(name);
    return getResources().getFloat(bundle.getInt(name));
  }

  /**
   * Return a string array from the service's meta-data. Throws an {@link IllegalArgumentException}
   * if no meta-data matches the provided name.
   */
  @NonNull
  protected final String[] requireMetaStringArray(@NonNull String name) {
    requireName(name);
    return getResources().getStringArray(bundle.getInt(name));
  }

  /**
   * Return a resource id from the service's meta-data. Throws an {@link IllegalArgumentException}
   * if no meta-data matches the provided name.
   */
  protected final int requireMetaResourceId(@NonNull String name) {
    requireName(name);
    return bundle.getInt(name);
  }

  private void requireName(@NonNull String name) {
    if (bundle.containsKey(name)) {
      return;
    }
    loge(TAG, "Missing required meta-data value " + name + ". Cannot instantiate service.");
    throw new IllegalArgumentException("Missing required meta-data value " + name + ".");
  }
}
