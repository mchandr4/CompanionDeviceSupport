package com.google.android.connecteddevice.trust

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager
import com.google.android.connecteddevice.util.ByteUtils
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.lang.IllegalStateException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowKeyguardManager

@RunWith(RobolectricTestRunner::class)
class TrustedDeviceAgentServiceTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val userId = ActivityManager.getCurrentUser()

  private val mockTrustedDeviceManager = mock<ITrustedDeviceManager>()

  private lateinit var keyguardManager: ShadowKeyguardManager

  private lateinit var service: TestTrustedDeviceAgentService

  private lateinit var delegate: ITrustedDeviceAgentDelegate

  @Before
  fun setup() {
    keyguardManager =
      shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
    service =
      Robolectric.buildService(TestTrustedDeviceAgentService::class.java)
        .apply { get().trustedDeviceManager = mockTrustedDeviceManager }
        .create()
        .get()
    service.onCreate()
    argumentCaptor<ITrustedDeviceAgentDelegate> {
      verify(mockTrustedDeviceManager, atLeastOnce()).setTrustedDeviceAgentDelegate(capture())
      delegate = firstValue
    }
  }

  @Test
  fun unlockUserWithToken_invokesCallbackAfterTokenReceived_DeviceAndUserAreLocked() {
    lockUser()

    sendToken()
    unlockUser()

    verify(mockTrustedDeviceManager).onUserUnlocked()
  }

  @Test
  fun unlockUserWithToken_invokesCallbackAfterTokenReceived_DeviceIsLockedAndUserIsUnlocked() {
    keyguardManager.setIsDeviceLocked(true)
    service.isUserUnlocked = true

    sendToken()

    verify(mockTrustedDeviceManager).onUserUnlocked()
  }

  @Test
  fun unlockUserWithToken_doesNotInvokeCallbackIfDeviceIsNotLocked() {
    keyguardManager.setIsDeviceLocked(false)

    sendToken()
    unlockUser()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  @Test
  fun onUserUnlock_doesNotInvokeCallbackIfTokenWasNotUsed() {
    keyguardManager.setIsDeviceLocked(true)

    unlockUser()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  private fun unlockUser() {
    service.isUserUnlocked = true
    service.maybeDismissLockscreen()
  }

  private fun lockUser() {
    keyguardManager.setIsDeviceLocked(true)
    service.isUserUnlocked = false
  }

  private fun sendToken() {
    try {
      delegate.unlockUserWithToken(ByteUtils.randomBytes(ESCROW_TOKEN_LENGTH), HANDLE, userId)
    } catch (e: IllegalStateException) {
      // This is expected because we aren't actually connected to a real TrustAgent.
    }
  }

  companion object {
    private const val ESCROW_TOKEN_LENGTH = 8

    private const val HANDLE = 10L
  }
}

class TestTrustedDeviceAgentService : TrustedDeviceAgentService() {

  var isUserUnlocked = true

  override fun bindToService() {
    setupManager()
  }

  override fun isUserUnlocked(userId: Int): Boolean {
    return isUserUnlocked
  }
}
