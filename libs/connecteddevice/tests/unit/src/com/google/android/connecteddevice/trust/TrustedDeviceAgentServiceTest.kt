package com.google.android.connecteddevice.trust

import android.app.ActivityManager
import android.app.KeyguardManager
import android.os.PowerManager
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import android.os.Build;
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
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowKeyguardManager
import org.robolectric.shadows.ShadowPowerManager


@RunWith(RobolectricTestRunner::class)
@Config(sdk=[Build.VERSION_CODES.S, Build.VERSION_CODES.R, Build.VERSION_CODES.Q])
class TrustedDeviceAgentServiceTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val userId = ActivityManager.getCurrentUser()

  private val mockTrustedDeviceManager = mock<ITrustedDeviceManager>()

  private lateinit var service: TestTrustedDeviceAgentService

  private lateinit var delegate: ITrustedDeviceAgentDelegate
  private val keyguardManager =
    context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  private val powerManager =
    context.getSystemService(Context.POWER_SERVICE) as PowerManager
  private val shadowKeyguardManager = Shadow.extract<ShadowKeyguardManager>(keyguardManager)
  private val shadowPowerManager = Shadow.extract<ShadowPowerManager>(powerManager)

  @Before
  fun setup() {
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
    shadowPowerManager.turnScreenOn(true)
    shadowKeyguardManager.setIsDeviceLocked(false)
  }

  @Test
  fun unlockUserWithToken_invokesCallbackAfterTokenReceived_deviceAndUserAreLocked() {
    lockUser()

    sendToken()
    unlockUser()

    verify(mockTrustedDeviceManager).onUserUnlocked()
  }

  @Test
  fun unlockUserWithTokenBeforeAndroidT_invokesCallbackAfterTokenReceived_userIsUnlocked() {
    service.isUserUnlocked = true

    sendToken()

    verify(mockTrustedDeviceManager).onUserUnlocked()
  }

  @Config(sdk=[Build.VERSION_CODES.TIRAMISU])
  @Test
  fun unlockUserWithTokenAfterAndroidT_doNotInvokesCallbackAfterTokenReceivedImmediately() {
    service.isUserUnlocked = true

    sendToken()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  @Test
  fun onUserUnlock_doesNotInvokeCallbackIfTokenWasNotUsed() {
    unlockUser()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  @Test
  fun unlockUserWithToken_locksScreenStillPresent_doesNotInvokeCallback() {
    shadowKeyguardManager.setIsDeviceLocked(true)
    service.isUserUnlocked = true

    sendToken()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  @Test
  fun unlockUserWithToken_deviceNotInteractive_doesNotDismissScreen() {
    shadowPowerManager.turnScreenOn(false)
    service.isUserUnlocked = true

    sendToken()

    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  private fun unlockUser() {
    service.isUserUnlocked = true
    service.maybeDismissLockscreen()
  }

  private fun lockUser() {
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
