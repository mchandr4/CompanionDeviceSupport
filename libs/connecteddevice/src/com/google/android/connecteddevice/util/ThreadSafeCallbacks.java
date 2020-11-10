package com.google.android.connecteddevice.util;

import androidx.annotation.NonNull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Class for invoking thread-safe callbacks.
 *
 * @param <T> Callback type.
 */
public class ThreadSafeCallbacks<T> {

  private final ConcurrentHashMap<T, Executor> callbacks = new ConcurrentHashMap<>();

  /** Add a callback to be notified on its executor. */
  public void add(@NonNull T callback, @NonNull Executor executor) {
    callbacks.put(callback, executor);
  }

  /** Remove a callback from the collection. */
  public void remove(@NonNull T callback) {
    callbacks.remove(callback);
  }

  /** Clear all callbacks from the collection. */
  public void clear() {
    callbacks.clear();
  }

  /** Return the number of callbacks in collection. */
  public int size() {
    return callbacks.size();
  }

  /** Return {@code true} if the callback is in the collection. */
  public boolean contains(@NonNull T callback) {
    return callbacks.containsKey(callback);
  }

  /** Invoke notification on all callbacks with their supplied {@link Executor}. */
  public void invoke(Consumer<T> notification) {
    Set<Map.Entry<T, Executor>> entries = callbacks.entrySet();
    for (Map.Entry<T, Executor> entry : entries) {
      T callback = entry.getKey();
      Executor executor = entry.getValue();
      executor.execute(() -> notification.accept(callback));
    }
  }
}
