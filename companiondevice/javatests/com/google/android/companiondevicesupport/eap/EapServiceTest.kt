/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.companiondevicesupport.eap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.service.ProtocolRegistry
import com.google.android.connecteddevice.util.MetaDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class EapServiceTest {
  private lateinit var service: TestEapService

  @Before
  fun setUp() {
    service = Robolectric.buildService(TestEapService::class.java).create().get()
  }

  @Test
  fun initializeProtocols_ableToInitiateEapProtocol() {
    val protocols =
      service.initializeProtocols(
        setOf(TransportProtocols.PROTOCOL_SPP, TransportProtocols.PROTOCOL_EAP)
      )
    assertThat(protocols).hasSize(1)
  }

  @Test
  fun initializeProtocols_noEapConfig_returnEmptyMap() {
    val mockEmptyMetaDataProvider = mock<MetaDataProvider>()
    whenever(mockEmptyMetaDataProvider.getMetaString(any(), any())).thenReturn("")
    service.metaDataProvider = mockEmptyMetaDataProvider
    val protocols =
      service.initializeProtocols(
        setOf(TransportProtocols.PROTOCOL_SPP, TransportProtocols.PROTOCOL_EAP)
      )
    assertThat(protocols).isEmpty()
  }

  @Test
  fun onDestroy_cleansUpRegistry() {
    service.onDestroy()

    verify(service.mockRegistry).cleanUp()
  }
}

private class TestEapService : EapService() {
  val mockRegistry = mock<ProtocolRegistry>()
  val mockMetaDataProvider = mock<MetaDataProvider>()

  override fun onCreate() {
    whenever(mockMetaDataProvider.getMetaString(any(), any())).thenReturn("test string")
    whenever(mockMetaDataProvider.getMetaInt(any(), any())).thenReturn(700)
    metaDataProvider = mockMetaDataProvider
    protocolRegistry = mockRegistry
  }
}
