package com.google.android.connecteddevice.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.service.TrunkService.META_EARLY_SERVICES
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class TrunkServiceTest {

  @Test
  fun onCreate_startsEarlyStartServices() {
    val component1 = ComponentName("package", "class1")
    val component2 = ComponentName("package", "class2")
    val service =
      createTrunkService(arrayOf(component1.flattenToString(), component2.flattenToString()))

    service.onCreate()

    assertThat(service.boundServices).containsKey(component1.flattenToString())
    assertThat(service.boundServices).containsKey(component2.flattenToString())
  }

  @Test
  fun onCreate_doesNotThrowWithEmptyListOfServices() {
    createTrunkService(arrayOf()).onCreate()
  }

  @Test
  fun onCreate_doesNotThrowWithMalformedServiceName() {
    createTrunkService(arrayOf("")).onCreate()
  }

  @Test
  fun onDestroy_stopsOnlyStartedServices() {
    val component1 = ComponentName("package", "class1")
    val component2 = ComponentName("package", "class2")
    val component3 = ComponentName("package", "class3")
    val service =
      createTrunkService(
        arrayOf(
          component1.flattenToString(),
          component2.flattenToString(),
          component3.flattenToString()
        )
      )

    service.onCreate()
    val boundService1 = service.boundServices[component1.flattenToString()]
      ?: fail("Expected service1 was not bound.")
    boundService1.onNullBinding(component1)
    val boundService2 = service.boundServices[component2.flattenToString()]
      ?: fail("Expected service2 was not bound.")
    boundService2.onServiceConnected(component2, mock())
    service.onDestroy()

    assertThat(service.unboundServices).containsExactly(boundService1, boundService2)
  }

  @Test
  fun onServiceDisconnected_attemptsToRestartService() {
    val component = ComponentName("package", "class")
    val service =
      createTrunkService(arrayOf(component.flattenToString()))

    service.onCreate()
    val boundService = service.boundServices.remove(component.flattenToString())
      ?: fail("Expected service was not bound.")
    boundService.onServiceDisconnected(component)

    assertThat(service.boundServices).containsKey(component.flattenToString())
  }

  @Test
  fun startBranchServices_retriesBindingIfUnsuccessful() {
    val component = ComponentName("package", "class")
    val service = FailingBindTrunkService().apply {
      context = ApplicationProvider.getApplicationContext()
      earlyStartServices = arrayOf(component.flattenToString())
    }
    val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())

    service.onCreate()
    repeat(TrunkService.MAX_BIND_ATTEMPTS) {
      assertThat(service.boundServices.remove(component.flattenToString())).isNotNull()
      shadowLooper.runToEndOfTasks()
    }
  }

  private fun createTrunkService(earlyStartServices: Array<String>) =
    TestTrunkService().apply {
      context = ApplicationProvider.getApplicationContext()
      this.earlyStartServices = earlyStartServices
    }

  private open class TestTrunkService : TrunkService() {

    val boundServices = mutableMapOf<String, ServiceConnection>()

    val unboundServices = mutableListOf<ServiceConnection>()

    lateinit var context: Context

    lateinit var earlyStartServices: Array<String>

    override fun retrieveMetaDataBundle(): Bundle =
      Bundle().apply { putInt(META_EARLY_SERVICES, EARLY_START_SERVICES_ID) }

    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      val component = service?.component ?: return false
      boundServices[component.flattenToString()] = conn
      return true
    }

    override fun unbindService(conn: ServiceConnection) {
      unboundServices.add(conn)
    }

    override fun getResources(): Resources {
      return object : Resources(
        context.resources.assets,
        context.resources.displayMetrics,
        context.resources.configuration
      ) {
        override fun getStringArray(id: Int): Array<String> {
          if (id == EARLY_START_SERVICES_ID) {
            return earlyStartServices
          }
          return super.getStringArray(id)
        }
      }
    }
  }

  private class FailingBindTrunkService : TestTrunkService() {
    override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
      super.bindService(service, conn, flags)
      return false
    }
  }

  companion object {
    const val EARLY_START_SERVICES_ID = 99999
  }
}
