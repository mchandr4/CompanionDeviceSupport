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

import androidx.annotation.NonNull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Class for invoking thread-safe callbacks.
 *
 * @param <T> Callback type.
 */
public class ThreadSafeCallbacks<T> {

  protected final ConcurrentHashMap<T, Executor> callbacks = new ConcurrentHashMap<>();

  /**
   * Add a callback to be notified on its {@link Executor}. Results in a no-op if the callback was
   * already registered.
   */
  public void add(@NonNull T callback, @NonNull Executor executor) {
    if (!contains(callback)) {
      callbacks.put(callback, executor);
    }
  }

  /** Remove a callback from the collection. */
  public void remove(@NonNull T callback) {
    callbacks.remove(callback);
  }

  /** Clear all callbacks from the collection. */
  public void clear() {
    callbacks.clear();
  }

  /** Return the number of callbacks in the collection. */
  public int size() {
    return callbacks.size();
  }

  /**
   * Returns {@code true} if the size of the collection is zero. Returns {@code false} otherwise.
   */
  public boolean isEmpty() {
    return size() == 0;
  }

  /** Returns {@code true} if the callback is in the collection. {@code false} otherwise. */
  public boolean contains(@NonNull T callback) {
    return callbacks.containsKey(callback);
  }

  /** Invoke the provided notification on all callbacks with their supplied {@link Executor}. */
  public void invoke(@NonNull SafeConsumer<T> notification) {
    Set<Map.Entry<T, Executor>> entries = callbacks.entrySet();
    for (Map.Entry<T, Executor> entry : entries) {
      T callback = entry.getKey();
      Executor executor = entry.getValue();
      executor.execute(() -> notification.accept(callback));
    }
  }
}
