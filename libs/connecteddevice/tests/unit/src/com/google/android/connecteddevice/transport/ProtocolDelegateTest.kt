package com.google.android.connecteddevice.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.core.util.mockToBeDead
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolDelegateTest {

  private val mockCallback = mock<ProtocolDelegate.Callback>()

  private val delegate = ProtocolDelegate().apply { callback = mockCallback }

  @Test
  fun addProtocol_invokesCallback() {
    val protocol = mockToBeAlive<IConnectionProtocol>()

    delegate.addProtocol(protocol)

    verify(mockCallback).onProtocolAdded(protocol)
  }

  @Test
  fun removeProtocol_invokesCallback() {
    val protocol = mockToBeAlive<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    delegate.removeProtocol(protocol)

    verify(mockCallback).onProtocolRemoved(protocol)
  }

  @Test
  fun addOobProtocol_addedToOobList() {
    val protocol = mockToBeAlive<IConnectionProtocol>()

    delegate.addOobProtocol(protocol)

    assertThat(delegate.oobProtocols).containsExactly(protocol)
  }

  @Test
  fun removeOobProtocol_removeFromOobList() {
    val protocol = mockToBeAlive<IConnectionProtocol>()

    delegate.addOobProtocol(protocol)
    delegate.removeOobProtocol(protocol)

    assertThat(delegate.oobProtocols).isEmpty()
  }

  @Test
  fun removeProtocol_unrecognizedProtocolDoesNotInvokeCallback() {
    val protocol = mockToBeAlive<IConnectionProtocol>()

    delegate.removeProtocol(protocol)

    verify(mockCallback, never()).onProtocolRemoved(protocol)
  }

  @Test
  fun protocols_accessWithDeadProtocolInvokesCallback() {
    val protocol = mockToBeDead<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    val protocols = delegate.protocols

    assertThat(protocols).isEmpty()
    verify(mockCallback).onProtocolRemoved(protocol)
  }

  @Test
  fun isEmpty_returnsFalseWithAliveProtocol() {
    val protocol = mockToBeAlive<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    assertThat(delegate.isEmpty).isFalse()
  }

  @Test
  fun isEmpty_returnsTrueForDeadProtocol() {
    val protocol = mockToBeDead<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    assertThat(delegate.isEmpty).isTrue()
  }

  @Test
  fun isNotEmpty_returnsTrueWithAliveProtocol() {
    val protocol = mockToBeAlive<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    assertThat(delegate.isNotEmpty).isTrue()
  }

  @Test
  fun isNotEmpty_returnsFalseForDeadProtocol() {
    val protocol = mockToBeDead<IConnectionProtocol>()
    delegate.addProtocol(protocol)

    assertThat(delegate.isNotEmpty).isFalse()
  }
}
