package com.google.android.connecteddevice.util;

import androidx.annotation.NonNull;

/** Java 7 alternative to {@code Consumer<T>}. */
public interface SafeConsumer<T> {
  /** Performs this operation on the given argument. */
  void accept(@NonNull T t);
}
