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

package com.google.android.connecteddevice.util;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.os.IBinder;
import android.os.RemoteException;

/**
 * Class that holds the binder of a remote callback and an action to be executed when this binder
 * dies. It registers for death notification of the {@link #callbackBinder} and executes {@link
 * #onDiedConsumer} when {@link #callbackBinder} dies.
 */
public class RemoteCallbackBinder implements IBinder.DeathRecipient {
  private static final String TAG = "BinderClient";
  private final IBinder callbackBinder;
  private final SafeConsumer<IBinder> onDiedConsumer;

  public RemoteCallbackBinder(IBinder binder, SafeConsumer<IBinder> onBinderDied) {
    callbackBinder = binder;
    onDiedConsumer = onBinderDied;
    try {
      binder.linkToDeath(this, 0);
    } catch (RemoteException e) {
      logd(TAG, "Cannot link death recipient to binder " + callbackBinder + ", " + e);
    }
  }

  @Override
  public void binderDied() {
    logd(TAG, "Binder died " + callbackBinder);
    onDiedConsumer.accept(callbackBinder);
    cleanUp();
  }

  /** Clean up the client. */
  public void cleanUp() {
    callbackBinder.unlinkToDeath(this, 0);
  }

  /** Get the callback binder of the client. */
  public IBinder getCallbackBinder() {
    return callbackBinder;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof RemoteCallbackBinder)) {
      return false;
    }
    RemoteCallbackBinder remoteCallbackBinder = (RemoteCallbackBinder) obj;
    return callbackBinder.equals(remoteCallbackBinder.callbackBinder);
  }

  @Override
  public int hashCode() {
    return callbackBinder.hashCode();
  }
}
