package com.google.android.connecteddevice.core.util

import android.os.IBinder
import android.os.IInterface
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever

/** Returns a mock of the [IInterface] `T` with a binder that is alive. */
inline fun <reified T> mockToBeAlive(): T where T : IInterface {
  val mockInterface: T = mock()
  val mockBinder: IBinder = mock()
  whenever(mockInterface.asBinder()).thenReturn(mockBinder)
  whenever(mockBinder.isBinderAlive).thenReturn(true)
  return mockInterface
}
