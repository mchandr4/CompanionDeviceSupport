package com.google.android.connecteddevice.trust

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager
import com.google.android.connecteddevice.util.ByteUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowKeyguardManager
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S, Build.VERSION_CODES.R, Build.VERSION_CODES.Q])
class TrustedDeviceAgentServiceTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val userId = ActivityManager.getCurrentUser()

  private val mockTrustedDeviceManager = mock<ITrustedDeviceManager>()

  private lateinit var service: TestTrustedDeviceAgentService

  private lateinit var delegate: ITrustedDeviceAgentDelegate
  private val keyguardManager =
    context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
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

  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
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

  @Test
  fun unlockUserWithToken_deviceBecomeInteractive_dismissScreen() {
    val screenOnIntent = Intent(Intent.ACTION_SCREEN_ON)
    shadowPowerManager.turnScreenOn(false)
    service.isUserUnlocked = true
    sendToken()

    shadowPowerManager.turnScreenOn(true)
    service.screenOnReceiver?.onReceive(context, screenOnIntent)

    verify(mockTrustedDeviceManager).onUserUnlocked()
  }

  @Test
  fun unlockUserWithToken_deviceBecomeInteractiveUserLocked_noAction() {
    val screenOnIntent = Intent(Intent.ACTION_SCREEN_ON)
    shadowPowerManager.turnScreenOn(false)
    service.isUserUnlocked = false
    sendToken()

    shadowPowerManager.turnScreenOn(true)
    service.screenOnReceiver?.onReceive(context, screenOnIntent)

    verify(mockTrustedDeviceManager, never()).sendUnlockRequest()
    verify(mockTrustedDeviceManager, never()).onUserUnlocked()
  }

  @Test
  fun unlockUserWithoutToken_deviceBecomesInteractive_sendUnlockRequest() {
    val screenOnIntent = Intent(Intent.ACTION_SCREEN_ON)
    shadowPowerManager.turnScreenOn(false)
    service.isUserUnlocked = true

    shadowPowerManager.turnScreenOn(true)
    service.screenOnReceiver?.onReceive(context, screenOnIntent)

    verify(mockTrustedDeviceManager).sendUnlockRequest()
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
  var screenOnReceiver: BroadcastReceiver? = null

  override fun bindToService() {
    setupManager()
  }

  override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
    if (filter!!.hasAction(Intent.ACTION_SCREEN_ON)) {
      screenOnReceiver = receiver
    }
    return null
  }

  override fun isUserUnlocked(userId: Int): Boolean {
    return isUserUnlocked
  }
}
