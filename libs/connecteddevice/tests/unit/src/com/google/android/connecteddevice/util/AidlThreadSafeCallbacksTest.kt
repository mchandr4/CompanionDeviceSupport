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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AidlThreadSafeCallbacksTest {

  private val callbacks = AidlThreadSafeCallbacks<IInterface>()

  @Test
  fun add_callbacksWithDifferentBindersAdded() {
    val binder1: IBinder = mock()
    val binder2: IBinder = mock()
    val callback1: IInterface = mock()
    val callback2: IInterface = mock()
    whenever(callback1.asBinder()).thenReturn(binder1)
    whenever(callback2.asBinder()).thenReturn(binder2)

    callbacks.add(callback1, directExecutor())
    callbacks.add(callback2, directExecutor())

    assertThat(callbacks.size()).isEqualTo(2)
  }

  @Test
  fun add_duplicateBindersResultInOnlyOneRegisteredCallback() {
    val mockBinder: IBinder = mock()
    val callback: IInterface = mock()
    val callbackWithSameBinder: IInterface = mock()
    whenever(callback.asBinder()).thenReturn(mockBinder)
    whenever(callbackWithSameBinder.asBinder()).thenReturn(mockBinder)

    callbacks.add(callback, directExecutor())
    callbacks.add(callbackWithSameBinder, directExecutor())

    assertThat(callbacks.size()).isEqualTo(1)
  }

  @Test
  fun contains_matchesOnBinder() {
    val mockBinder: IBinder = mock()
    val callback: IInterface = mock()
    val callbackWithSameBinder: IInterface = mock()
    whenever(callback.asBinder()).thenReturn(mockBinder)
    whenever(callbackWithSameBinder.asBinder()).thenReturn(mockBinder)

    callbacks.add(callback, directExecutor())

    assertThat(callbacks.contains(callbackWithSameBinder)).isTrue()
  }

  @Test
  fun remove_removesMatchingBinder() {
    val mockBinder: IBinder = mock()
    val callback: IInterface = mock()
    val callbackWithSameBinder: IInterface = mock()
    whenever(callback.asBinder()).thenReturn(mockBinder)
    whenever(callbackWithSameBinder.asBinder()).thenReturn(mockBinder)

    callbacks.add(callback, directExecutor())
    callbacks.remove(callbackWithSameBinder)

    assertThat(callbacks.size()).isEqualTo(0)
  }

  @Test
  fun remove_unrecognizedBinderDoesNotThrow() {
    val mockBinder: IBinder = mock()
    val callback: IInterface = mock()
    whenever(callback.asBinder()).thenReturn(mockBinder)

    callbacks.remove(callback)
  }

  @Test
  fun invoke_invokesCallbackIfBinderIsAlive() {
    val aliveBinder: IBinder = mock()
    val deadBinder: IBinder = mock()
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadBinder.isBinderAlive).thenReturn(false)
    val aliveCallback: TestCallback = mock()
    val deadCallback: TestCallback = mock()
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
    val aliveBinder: IBinder = mock()
    val deadBinder: IBinder = mock()
    whenever(aliveBinder.isBinderAlive).thenReturn(true)
    whenever(deadBinder.isBinderAlive).thenReturn(false)
    val aliveCallback: TestCallback = mock()
    val deadCallback: TestCallback = mock()
    whenever(aliveCallback.asBinder()).thenReturn(aliveBinder)
    whenever(deadCallback.asBinder()).thenReturn(deadBinder)
    val callbacks = AidlThreadSafeCallbacks<TestCallback>()

    callbacks.add(aliveCallback, directExecutor())
    callbacks.add(deadCallback, directExecutor())
    callbacks.invoke { it.testCallbackMethod() }

    assertThat(callbacks.size()).isEqualTo(1)
  }

  private open class TestCallback() : IInterface {
    override fun asBinder(): IBinder {
      return mock()
    }

    open fun testCallbackMethod() {}
  }
}
