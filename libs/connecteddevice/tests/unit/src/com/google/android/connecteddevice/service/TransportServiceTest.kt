package com.google.android.connecteddevice.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.core.util.mockToBeAlive
import com.google.android.connecteddevice.transport.IProtocolDelegate
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.lang.IllegalArgumentException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class TransportServiceTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val mockDelegate = mockToBeAlive<IProtocolDelegate>()

  private lateinit var service: TestTransportService

  private lateinit var bluetoothManager: BluetoothManager

  @Before
  fun setUp() {
    bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    service =
      Robolectric.buildService(TestTransportService::class.java).create().get().apply {
        delegate = mockDelegate
        onCreate()
      }
  }

  @Test
  fun onCreate_bindsToPlatformService() {
    val service =
      Robolectric.buildService(TestTransportService::class.java).create().get().apply { onCreate() }

    assertThat(service.boundService).isNotNull()
  }

  @Test
  fun onDestroy_cleansUpService() {
    bluetoothManager.adapter.enable()
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)

    service.onDestroy()

    verify(mockDelegate).removeProtocol(any())
    assertThat(service.boundService).isNull()
    assertThat(service.receiver).isNull()
    assertThat(service.supportedProtocols).isEmpty()
  }

  @Test
  fun onDestroy_unregisterReceiverIllegalArgumentExceptionIsCaught() {
    service.shouldThrowOnUnregisterReceiver = true

    service.onDestroy()
  }

  @Test
  fun serviceDisconnect_clearsDelegateAndRebinds() {
    val service =
      Robolectric.buildService(TestTransportService::class.java).create().get().apply { onCreate() }
    val boundService = service.boundService
    service.boundService = null
    service.delegate = mockDelegate

    boundService?.onServiceDisconnected(null)

    assertThat(service.delegate).isNull()
    assertThat(service.boundService).isNotNull()
    assertThat(service.supportedProtocols).isEmpty()
  }

  @Test
  fun initializeBluetoothProtocols_addsProtocolsIfBluetoothIsOn() {
    bluetoothManager.adapter.enable()

    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)

    verify(mockDelegate).addProtocol(any())
  }

  @Test
  fun initializeBluetoothProtocols_doesNotAddProtocolsIfBluetoothIsOff() {
    bluetoothManager.adapter.disable()
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)

    verify(mockDelegate, never()).addProtocol(any())
  }

  @Test
  fun onBluetoothStateChanged_addsProtocolsWhenBluetoothIsTurnedOn() {
    bluetoothManager.adapter.enable()
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)

    verify(mockDelegate).addProtocol(any())
  }

  @Test
  fun onBluetoothStateChanged_removesProtocolsWhenBluetoothIsTurnedOff() {
    bluetoothManager.adapter.enable()
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_OFF)

    verify(mockDelegate).removeProtocol(any())
    assertThat(service.supportedProtocols).isEmpty()
  }

  @Test
  fun onBluetoothStateChanged_doesNotAddProtocolsMultipleTimes() {
    bluetoothManager.adapter.enable()
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)

    verify(mockDelegate).addProtocol(any())
  }

  private fun issueBluetoothChangedBroadcast(state: Int) {
    val intent =
      Intent(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
        putExtra(BluetoothAdapter.EXTRA_STATE, state)
      }
    service.receiver?.onReceive(context, intent)
  }
}

class TestTransportService : TransportService() {

  var boundService: ServiceConnection? = null

  var receiver: BroadcastReceiver? = null

  var shouldThrowOnUnregisterReceiver = false

  override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
    this.receiver = receiver
    return super.registerReceiver(receiver, filter)
  }

  override fun unregisterReceiver(receiver: BroadcastReceiver?) {
    if (shouldThrowOnUnregisterReceiver) {
      throw IllegalArgumentException()
    }
    this.receiver = null
    super.unregisterReceiver(receiver)
  }

  override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
    assertThat(service?.action).isEqualTo(ACTION_BIND_PROTOCOL)
    boundService = conn
    return true
  }

  override fun unbindService(conn: ServiceConnection) {
    boundService = null
  }

  override fun retrieveMetaDataBundle(): Bundle {
    return Bundle()
  }
}
