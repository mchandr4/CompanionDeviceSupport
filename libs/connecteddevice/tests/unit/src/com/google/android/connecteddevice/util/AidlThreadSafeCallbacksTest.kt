/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.connecteddevice.util

import android.os.IBinder
import android.os.IInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AidlThreadSafeCallbacksTest {

  private val callbacks = AidlThreadSafeCallbacks<IInterface>()

  @Test
  fun add_callbacksWithDifferentBindersAdded() {
    val binder1 = mock<IBinder>()
    val binder2 = mock<IBinder>()
    val callback1 = mock<IInterface>()
    val callback2 = mock<IInterface>()
    whenever(callback1.asBinder()).thenReturn(binder1)
    whenever(binder1.isBinderAlive).thenReturn(true)
    whenever(callback2.asBinder()).thenReturn(binder2)
    whenever(binder2.isBinderAlive).thenReturn(true)

    callbacks.add(callback1, directExecutor())
    callbacks.add(callback2, directExecutor())

    assertThat(callbacks.size()).isEqualTo(2)
  }

  @Test
  fun add_duplicateBindersResultInOnlyOneRegisteredCallback() {
    val mockBinder = mock<IBinder>()
    val callback = mock<IInterface>()
    val callbackWithSameBinder = mock<IInterface>()
    whenever(callback.asBinder()).thenReturn(mockBinder)
    whenever(callbackWithSameBinder.asBinder()).thenReturn(mockBinder)
    whenever(mockBinder.isBinderAlive).thenReturn(true)

    callbacks.add(callback, directExecutor())
    callbacks.add(callbackWithSameBinder, directExecutor())

    assertThat(callbacks.size()).isEqualTo(1)
  }

  @Test
  fun contains_matchesOnAliveBinder() {
    val aliveBinder = mock<IBinder>()
    val aliveCallback = mock<IInterface>()
    val callbackWithSameAliveBinder = mock<IInterface>()
    val deadBinder = mock<IBinder>()
    val deadCallback = mock<IInterface>()
    val callbackWithSameDeadBinder = mock<IInterface>()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(callbackWithSameAliveBinder.asBinder()).thenReturn(aliveBinder)
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    whenever(callbackWithSameDeadBinder.asBinder()).thenReturn(deadBinder)
    whenever(deadBinder.isBinderAlive).thenReturn(false)

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())

    assertThat(callbacks.contains(callbackWithSameAliveBinder)).isTrue()
    assertThat(callbacks.contains(callbackWithSameDeadBinder)).isFalse()
  }

  @Test
  fun contains_removeDeadBinders() {
    val aliveBinder = mock<IBinder>()
    val aliveCallback = mock<IInterface>()
    val callbackWithSameAliveBinder = mock<IInterface>()
    val deadBinder = mock<IBinder>()
    val deadCallback = mock<IInterface>()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(callbackWithSameAliveBinder.asBinder()).thenReturn(aliveBinder)
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    whenever(deadBinder.isBinderAlive).thenReturn(false)

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())

    assertThat(callbacks.contains(callbackWithSameAliveBinder)).isTrue()
    assertThat(callbacks.callbacks).hasSize(1)
  }

  @Test
  fun size_countCallbackIfBinderIsAlive() {
    val aliveBinder = mock<IBinder>()
    val aliveCallback = mock<IInterface>()
    val deadBinder = mock<IBinder>()
    val deadCallback = mock<IInterface>()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    whenever(deadBinder.isBinderAlive).thenReturn(false)

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())

    assertThat(callbacks.size()).isEqualTo(1)
    assertThat(callbacks.callbacks).hasSize(1)
  }

  @Test
  fun remove_removesMatchingBinder() {
    val mockBinder = mock<IBinder>()
    val callback = mock<IInterface>()
    val callbackWithSameBinder = mock<IInterface>()
    whenever(callback.asBinder()).thenReturn(mockBinder)
    whenever(callbackWithSameBinder.asBinder()).thenReturn(mockBinder)

    callbacks.add(callback, directExecutor())
    callbacks.remove(callbackWithSameBinder)

    assertThat(callbacks.size()).isEqualTo(0)
  }

  @Test
  fun remove_unrecognizedBinderDoesNotThrow() {
    val mockBinder = mock<IBinder>()
    val callback = mock<IInterface>()
    whenever(callback.asBinder()).thenReturn(mockBinder)

    callbacks.remove(callback)
  }

  @Test
  fun invoke_invokesCallbackIfBinderIsAlive() {
    val aliveBinder = mock<IBinder>()
    val deadBinder = mock<IBinder>()
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadBinder.isBinderAlive).thenReturn(false)
    val aliveCallback = mock<TestCallback>()
    val deadCallback = mock<TestCallback>()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    val callbacks = AidlThreadSafeCallbacks<TestCallback>()

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())
    callbacks.invoke { it.testCallbackMethod() }

    verify(aliveCallback).testCallbackMethod()
    verify(deadCallback, never()).testCallbackMethod()
  }

  @Test
  fun invoke_removesDeadBinders() {
    val aliveBinder = mock<IBinder>()
    val deadBinder = mock<IBinder>()
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadBinder.isBinderAlive).thenReturn(false)
    val aliveCallback = mock<TestCallback>()
    val deadCallback = mock<TestCallback>()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    val callbacks = AidlThreadSafeCallbacks<TestCallback>()

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())
    callbacks.invoke { it.testCallbackMethod() }

    assertThat(callbacks.callbacks).hasSize(1)
  }

  private open class TestCallback() : IInterface {
    override fun asBinder(): IBinder {
      return mock()
    }

    open fun testCallbackMethod() {}
  }
}
