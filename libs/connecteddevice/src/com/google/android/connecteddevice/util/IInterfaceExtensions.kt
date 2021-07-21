package com.google.android.connecteddevice.util

import android.os.IInterface

/** Returns the [IInterface] instance if its binder is currently alive. Returns `null` otherwise. */
fun <T> T.aliveOrNull(): T? where T : IInterface {
  return takeIf { asBinder().isBinderAlive }
}
