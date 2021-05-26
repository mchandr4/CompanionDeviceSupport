package com.google.android.connecteddevice.system

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.companionprotos.SystemQueryType.SYSTEM_QUERY_TYPE_UNKNOWN
import com.google.android.connecteddevice.api.IConnectedDeviceManager
import com.google.android.connecteddevice.api.RemoteFeature.QueryCallback
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.ByteUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ExtensionRegistryLite
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemFeatureTest {

  private val mockStorage: ConnectedDeviceStorage = mock()

  private val mockManager: IConnectedDeviceManager = mock()

  private val device =
    ConnectedDevice(
      UUID.randomUUID().toString(),
      /* deviceName= */ null,
      /* belongsToActiveUser= */ true,
      /* hasSecureChannel= */ true
    )

  private lateinit var systemFeature: SystemFeature

  @Before
  fun setUp() {
    systemFeature =
      spy(
        SystemFeature(
          ApplicationProvider.getApplicationContext(),
          mockManager,
          mockStorage
        ) { TEST_DEVICE_NAME }
      )
  }

  @Test
  fun onSecureChannelEstablished_sendsQueryForDeviceName() {
    systemFeature.onSecureChannelEstablishedInternal(device)

    val captor = argumentCaptor<ByteArray>()
    verify(systemFeature).sendQuerySecurely(eq(device), captor.capture(), anyOrNull(), any())

    val systemQuery =
      SystemQuery.parseFrom(captor.firstValue, ExtensionRegistryLite.getEmptyRegistry())
    assertThat(systemQuery.type).isEqualTo(DEVICE_NAME)
  }

  @Test
  fun deviceNameQueryResponse_successUpdatesDeviceName() {
    systemFeature.onSecureChannelEstablishedInternal(device)

    val callback = argumentCaptor<QueryCallback>().run {
      verify(systemFeature).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
      firstValue
    }
    callback.onSuccess(TEST_DEVICE_NAME.toByteArray(StandardCharsets.UTF_8))
    verify(mockStorage).updateAssociatedDeviceName(device.deviceId, TEST_DEVICE_NAME)
  }

  @Test
  fun deviceNameQueryResponse_successDoesNotUpdateDeviceNameIfEmpty() {
    systemFeature.onSecureChannelEstablishedInternal(device)

    val captor = argumentCaptor<QueryCallback>()
    verify(systemFeature).sendQuerySecurely(eq(device), any(), anyOrNull(), captor.capture())
    captor.firstValue.onSuccess(ByteArray(0))
    verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
  }

  @Test
  fun deviceNameQueryResponse_onErrorDoesNotUpdateDeviceName() {
    systemFeature.onSecureChannelEstablishedInternal(device)

    val captor = argumentCaptor<QueryCallback>()
    verify(systemFeature).sendQuerySecurely(eq(device), any(), anyOrNull(), captor.capture())
    captor.firstValue.onError(/* response= */ null)
    verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
  }

  @Test
  fun deviceNameQueryResponse_onQueryFailedToSendDoesNotUpdateDeviceName() {
    systemFeature.onSecureChannelEstablishedInternal(device)

    val captor = argumentCaptor<QueryCallback>()
    verify(systemFeature).sendQuerySecurely(eq(device), any(), anyOrNull(), captor.capture())
    captor.firstValue.onQueryFailedToSend(/* isTransient= */ false)
    captor.firstValue.onQueryFailedToSend(/* isTransient= */ true)
    verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
  }

  @Test
  fun onQueryReceived_deviceNameQueryRespondsWithName() {
    val query = SystemQuery
      .newBuilder()
      .setType(DEVICE_NAME)
      .build()
    val queryId = 0

    systemFeature.onQueryReceivedInternal(device, queryId, query.toByteArray())
    val captor = argumentCaptor<ByteArray>()
    verify(systemFeature)
      .respondToQuerySecurely(eq(device), eq(queryId), eq(true), captor.capture())

    assertThat(captor.firstValue).isEqualTo(TEST_DEVICE_NAME.toByteArray(StandardCharsets.UTF_8))
  }

  @Test
  fun onQueryReceived_unknownQueryTypeRespondsWithError() {
    val query = SystemQuery
      .newBuilder()
      .setType(SYSTEM_QUERY_TYPE_UNKNOWN)
      .build()
    val queryId = 0

    systemFeature.onQueryReceivedInternal(device, queryId, query.toByteArray())
    verify(systemFeature).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  @Test
  fun onQueryReceived_nullNameFromNameProviderRespondsWithError() {
    systemFeature =
      spy(
        SystemFeature(
          ApplicationProvider.getApplicationContext(),
          mockManager,
          mockStorage
        ) { null }
      )
    val query = SystemQuery
      .newBuilder()
      .setType(DEVICE_NAME)
      .build()
    val queryId = 0

    systemFeature.onQueryReceivedInternal(device, queryId, query.toByteArray())
    verify(systemFeature).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  @Test
  fun onQueryReceived_failureToParseQueryRespondsWithError() {
    val queryId = 0
    systemFeature.onQueryReceivedInternal(device, queryId, ByteUtils.randomBytes(100))
    verify(systemFeature).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  companion object {
    const val TEST_DEVICE_NAME = "TEST_DEVICE_NAME"
  }
}
