package com.google.android.connecteddevice.util;

/** Java 7 alternative to {@code Consumer<T>}. */
public interface SafeConsumer<T> {
  /** Performs this operation on the given argument. */
  void accept(T t);
}
