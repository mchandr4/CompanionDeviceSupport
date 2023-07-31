package com.google.android.connecteddevice.core.util

import android.os.IBinder
import android.os.IInterface
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Returns a mock of the [IInterface] `T` with a binder that is alive. */
inline fun <reified T> mockToBeAlive(): T where T : IInterface {
  val mockInterface: T = mock()
  val mockBinder: IBinder = mock()
  whenever(mockInterface.asBinder()).thenReturn(mockBinder)
  whenever(mockBinder.isBinderAlive).thenReturn(true)
  return mockInterface
}

/** Returns a mock of the [IInterface] `T` with a binder that is dead. */
inline fun <reified T> mockToBeDead(): T where T : IInterface {
  val mockInterface: T = mock()
  val mockBinder: IBinder = mock()
  whenever(mockInterface.asBinder()).thenReturn(mockBinder)
  whenever(mockBinder.isBinderAlive).thenReturn(false)
  return mockInterface
}
