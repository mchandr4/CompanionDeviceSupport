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

package com.google.android.connecteddevice.service;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;

/** Service with convenience methods for using meta-data configuration. */
public abstract class MetaDataService extends LifecycleService {

  private static final String TAG = "MetaDataService";

  private Bundle bundle;

  @Override
  public void onCreate() {
    super.onCreate();
    bundle = retrieveMetaDataBundle();
  }

  /** Return the meta-data bundle defined in the manifest for this service. */
  @NonNull
  protected Bundle retrieveMetaDataBundle() {
    logd(TAG, "Retrieving meta-data from service definition.");
    ComponentName service = new ComponentName(this, this.getClass());
    try {
      return getPackageManager().getServiceInfo(service, PackageManager.GET_META_DATA).metaData;
    } catch (NameNotFoundException e) {
      throw new IllegalStateException("Unable to read meta-data for service.", e);
    }
  }

  /**
   * Return a string from the service's meta-data, or default value if no meta-data matches the
   * provided name.
   */
  protected final String getMetaString(@NonNull String name, @NonNull String defaultValue) {
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
  protected final String[] getMetaStringArray(
      @NonNull String name, @NonNull String[] defaultValue) {
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

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    IBinder unused = super.onBind(intent);
    return null;
  }
}
