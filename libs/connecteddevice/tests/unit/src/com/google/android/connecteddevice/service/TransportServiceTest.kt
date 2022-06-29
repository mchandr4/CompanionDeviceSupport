package com.google.android.connecteddevice.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class TransportServiceTest {
  private lateinit var service: TestTransportService

  @Before
  fun setUp() {
    service = Robolectric.buildService(TestTransportService::class.java).create().get()
  }

  @Test
  fun initializeProtocols_initAllAvailableProtocols() {
    assertThat(
        service.initializeProtocols(
          setOf(TransportProtocols.PROTOCOL_BLE_PERIPHERAL, TransportProtocols.PROTOCOL_SPP)
        )
      )
      .hasSize(2)
  }

  @Test
  fun initializeProtocols_ignoreUnavailableProtocols() {
    assertThat(service.initializeProtocols(setOf(TransportProtocols.PROTOCOL_EAP))).isEmpty()
  }

  @Test
  fun onDestroy_cleansUpRegistry() {
    service.onDestroy()

    verify(service.mockRegistry).cleanUp()
  }
}

private class TestTransportService : TransportService() {
  val mockRegistry = mock<ProtocolRegistry>()
  private val mockMetaDataProvider = mock<MetaDataProvider>()

  override fun onCreate() {
    whenever(mockMetaDataProvider.getMetaString(any(), any()))
      .thenReturn(UUID.randomUUID().toString())
    // Arbitrary number for test.
    whenever(mockMetaDataProvider.getMetaInt(any(), any())).thenReturn(700)
    whenever(mockMetaDataProvider.getMetaBoolean(any(), any())).thenReturn(false)
    metaDataProvider = mockMetaDataProvider
    protocolRegister = mockRegistry
  }
}
