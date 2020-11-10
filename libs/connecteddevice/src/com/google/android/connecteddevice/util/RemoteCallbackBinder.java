package com.google.android.connecteddevice.util;

import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.os.IBinder;
import android.os.RemoteException;
import java.util.function.Consumer;

/**
 * Class that holds the binder of a remote callback and an action to be executed when this binder
 * dies. It registers for death notification of the {@link #callbackBinder} and executes {@link
 * #onDiedConsumer} when {@link #callbackBinder} dies.
 */
public class RemoteCallbackBinder implements IBinder.DeathRecipient {
  private static final String TAG = "BinderClient";
  private final IBinder callbackBinder;
  private final Consumer<IBinder> onDiedConsumer;

  public RemoteCallbackBinder(IBinder binder, Consumer<IBinder> onBinderDied) {
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
