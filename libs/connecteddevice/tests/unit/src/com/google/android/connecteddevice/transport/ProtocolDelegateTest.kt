package com.google.android.connecteddevice.transport

import android.os.IBinder
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolDelegateTest {

  private val mockCallback = mock<ProtocolDelegate.Callback>()

  private val delegate = ProtocolDelegate().apply { callback = mockCallback }
  private val mockBinder = mock<IBinder>()

  private val mockProtocol = mock<IConnectionProtocol> { on { asBinder() } doReturn mockBinder }

  @Test
  fun addProtocol_registerOnBinderDiedListener() {
    delegate.addProtocol(mockProtocol)

    verify(mockBinder).linkToDeath(any(), any())
  }

  @Test
  fun addProtocol_invokesCallback() {
    delegate.addProtocol(mockProtocol)

    verify(mockCallback).onProtocolAdded(mockProtocol)
  }

  @Test
  fun removeProtocol_invokesCallback() {
    delegate.addProtocol(mockProtocol)

    delegate.removeProtocol(mockProtocol)

    verify(mockCallback).onProtocolRemoved(mockProtocol)
  }

  @Test
  fun addOobProtocol_registerOBinderDiedListener() {
    delegate.addOobProtocol(mockProtocol)

    verify(mockBinder).linkToDeath(any(), any())
  }

  @Test
  fun addOobProtocol_addedToOobList() {
    delegate.addOobProtocol(mockProtocol)

    assertThat(delegate.oobProtocols).containsExactly(mockProtocol)
  }

  @Test
  fun removeOobProtocol_removeFromOobList() {
    delegate.addOobProtocol(mockProtocol)
    delegate.removeOobProtocol(mockProtocol)

    assertThat(delegate.oobProtocols).isEmpty()
  }

  @Test
  fun removeProtocol_unrecognizedProtocolDoesNotInvokeCallback() {
    delegate.removeProtocol(mockProtocol)

    verify(mockCallback, never()).onProtocolRemoved(mockProtocol)
  }

  @Test
  fun isEmpty_returnsFalseWithAliveProtocol() {
    delegate.addProtocol(mockProtocol)

    assertThat(delegate.isEmpty).isFalse()
  }

  @Test
  fun isEmpty_returnsTrueAfterBinderDied() {
    delegate.addProtocol(mockProtocol)
    argumentCaptor<IBinder.DeathRecipient>().apply {
      verify(mockBinder).linkToDeath(capture(), any())
      firstValue.binderDied()
      assertThat(delegate.isEmpty).isTrue()
    }
  }

  @Test
  fun isNotEmpty_returnsTrueWithAliveProtocol() {
    delegate.addProtocol(mockProtocol)

    assertThat(delegate.isNotEmpty).isTrue()
  }

  @Test
  fun isNotEmpty_returnsFalseForDeadProtocol() {
    delegate.addProtocol(mockProtocol)
    argumentCaptor<IBinder.DeathRecipient>().apply {
      verify(mockBinder).linkToDeath(capture(), any())
      firstValue.binderDied()
      assertThat(delegate.isNotEmpty).isFalse()
    }
  }

  @Test
  fun addProtocol_throwException_doNotNotifyCallback() {
    whenever(mockBinder.linkToDeath(any(), any())).thenThrow(RemoteException())
    delegate.addProtocol(mockProtocol)

    verify(mockCallback, never()).onProtocolRemoved(mockProtocol)
    assertThat(delegate.isEmpty).isTrue()
  }

  @Test
  fun addOobProtocol_throwException_oobListIsEmpty() {
    whenever(mockBinder.linkToDeath(any(), any())).thenThrow(RemoteException())
    delegate.addOobProtocol(mockProtocol)

    assertThat(delegate.oobProtocols).isEmpty()
  }

  @Test
  fun addOobProtocol_processDied_oobListIsEmpty() {
    delegate.addOobProtocol(mockProtocol)
    argumentCaptor<IBinder.DeathRecipient>().apply {
      verify(mockBinder).linkToDeath(capture(), any())
      firstValue.binderDied()
    }
    assertThat(delegate.oobProtocols).isEmpty()
  }
}
