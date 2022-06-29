package com.google.android.connecteddevice.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.service.CompanionProtocolRegistry.Companion.ACTION_BIND_PROTOCOL
import com.google.android.connecteddevice.service.CompanionProtocolRegistry.Companion.META_SUPPORTED_OOB_CHANNELS
import com.google.android.connecteddevice.service.CompanionProtocolRegistry.Companion.META_SUPPORTED_TRANSPORT_PROTOCOLS
import com.google.android.connecteddevice.transport.ConnectChallenge
import com.google.android.connecteddevice.transport.ConnectionProtocol
import com.google.android.connecteddevice.transport.IDataSendCallback
import com.google.android.connecteddevice.transport.IDiscoveryCallback
import com.google.android.connecteddevice.transport.ProtocolDelegate
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionProtocolRegistryTest {

  private val mockPackageManager = mock<PackageManager>()

  private val mockMetaDataProvider = mock<MetaDataProvider>()

  private lateinit var context: FakeContext

  private lateinit var bluetoothManager: BluetoothManager

  private lateinit var registry: CompanionProtocolRegistry

  @Before
  fun setUp() {
    context = FakeContext(mockPackageManager)
    whenever(mockMetaDataProvider.getMetaStringArray(eq(META_SUPPORTED_TRANSPORT_PROTOCOLS), any()))
      .thenReturn(arrayOf(TransportProtocols.PROTOCOL_BLE_PERIPHERAL))
    whenever(mockMetaDataProvider.getMetaStringArray(eq(META_SUPPORTED_OOB_CHANNELS), any()))
      .thenReturn(arrayOf(TransportProtocols.PROTOCOL_EAP, TransportProtocols.PROTOCOL_SPP))
    registry = CompanionProtocolRegistry(context, mockMetaDataProvider, this::initializeProtocols)
    bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  }

  @Test
  fun init_bindsToPlatformService() {
    assertThat(context.boundService).isNotNull()
  }

  @Test
  fun onServiceConnected_bluetoothEnabled_initAllProtocols() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.enable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    assertThat(delegate.protocols).hasSize(1)
    assertThat(delegate.oobProtocols).hasSize(2)
  }

  @Test
  fun onServiceConnected_bluetoothDisabled_initAllProtocols() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.disable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    assertThat(delegate.protocols).isEmpty()
    assertThat(delegate.oobProtocols).isEmpty()
  }

  @Test
  fun onServiceDisconnected_rebindToCompanionPlatform() {
    context.boundService?.onServiceDisconnected(ComponentName("test.path", "test.path"))
    assertThat(context.bindAttempts).isEqualTo(2)
  }

  @Test
  fun onBluetoothStateChanged_doNotInitIfAlreadyExist() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.enable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)
    assertThat(delegate.protocols).hasSize(1)
    assertThat(delegate.oobProtocols).hasSize(2)
  }

  @Test
  fun onBluetoothStateChanged_disconnectProtocolWhenBluetoothOff() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.enable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_OFF)
    assertThat(delegate.protocols).isEmpty()
    assertThat(delegate.oobProtocols).isEmpty()
  }

  @Test
  fun onBluetoothStateChanged_doNotAddRepeatedProtocols() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.enable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    issueBluetoothChangedBroadcast(BluetoothAdapter.STATE_ON)
    assertThat(delegate.protocols).hasSize(1)
    assertThat(delegate.oobProtocols).hasSize(2)
  }

  @Test
  fun cleanUp_disconnectProtocols() {
    val delegate = ProtocolDelegate()
    bluetoothManager.adapter.enable()
    context.boundService?.onServiceConnected(
      ComponentName("test.path", "test.path"),
      delegate.asBinder()
    )
    registry.cleanUp()
    assertThat(delegate.protocols).isEmpty()
    assertThat(delegate.oobProtocols).isEmpty()
    assertThat(context.boundService).isNull()
    assertThat(context.receiver).isNull()
  }

  private fun issueBluetoothChangedBroadcast(state: Int) {
    val intent =
      Intent(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
        putExtra(BluetoothAdapter.EXTRA_STATE, state)
      }
    context.receiver?.onReceive(context, intent)
  }

  private fun initializeProtocols(protocols: Set<String>): Map<String, ConnectionProtocol> {
    return protocols.associateBy({ it }, { TestConnectionProtocol() })
  }
}

private open class FakeContext(val mockPackageManager: PackageManager) :
  ContextWrapper(ApplicationProvider.getApplicationContext()) {

  var bindAttempts = 0
  var boundService: ServiceConnection? = null

  var receiver: BroadcastReceiver? = null

  override fun getPackageManager(): PackageManager = mockPackageManager

  override fun bindService(service: Intent?, conn: ServiceConnection, flags: Int): Boolean {
    assertThat(service?.action).isEqualTo(ACTION_BIND_PROTOCOL)
    boundService = conn
    bindAttempts++
    return true
  }

  override fun unbindService(conn: ServiceConnection) {
    boundService = null
  }
  override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
    this.receiver = receiver
    return super.registerReceiver(receiver, filter)
  }

  override fun unregisterReceiver(receiver: BroadcastReceiver?) {
    this.receiver = null
    super.unregisterReceiver(receiver)
  }
}

private class TestConnectionProtocol : ConnectionProtocol() {
  override fun isDeviceVerificationRequired(): Boolean {
    return true
  }

  override fun startAssociationDiscovery(
    name: String,
    identifier: ParcelUuid,
    callback: IDiscoveryCallback,
  ) {}

  override fun startConnectionDiscovery(
    id: ParcelUuid,
    challenge: ConnectChallenge,
    callback: IDiscoveryCallback,
  ) {}

  override fun stopAssociationDiscovery() {}

  override fun stopConnectionDiscovery(id: ParcelUuid) {}

  override fun sendData(protocolId: String, data: ByteArray, callback: IDataSendCallback?) {}

  override fun disconnectDevice(protocolId: String) {}

  override fun getMaxWriteSize(protocolId: String): Int {
    // Arbitrary number for test only.
    return 700
  }
}
