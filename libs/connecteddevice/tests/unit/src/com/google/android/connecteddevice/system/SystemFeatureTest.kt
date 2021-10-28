package com.google.android.connecteddevice.system

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.companionprotos.SystemQueryType.SYSTEM_QUERY_TYPE_UNKNOWN
import com.google.android.companionprotos.SystemQueryType.USER_ROLE
import com.google.android.companionprotos.SystemUserRole
import com.google.android.companionprotos.SystemUserRoleResponse
import com.google.android.connecteddevice.api.Connector
import com.google.android.connecteddevice.api.FakeConnector
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

  private val fakeConnector = spy(FakeConnector())

  private val device =
    ConnectedDevice(
      UUID.randomUUID().toString(),
      /* deviceName= */ null,
      /* belongsToDriver= */ true,
      /* hasSecureChannel= */ true
    )

  private lateinit var systemFeature: SystemFeature

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSystemService(BluetoothManager::class.java).adapter.name = TEST_DEVICE_NAME
    systemFeature = SystemFeature(context, mockStorage, fakeConnector)
    assertThat(fakeConnector.callback).isNotNull()
  }

  @Test
  fun start_connectsConnector() {
    systemFeature.start()

    verify(fakeConnector).connect()
  }

  @Test
  fun stop_disconnectsConnector() {
    systemFeature.stop()

    verify(fakeConnector).disconnect()
  }

  @Test
  fun onSecureChannelEstablished_sendsQueryForDeviceName() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    argumentCaptor<ByteArray>() {
      verify(fakeConnector).sendQuerySecurely(eq(device), capture(), anyOrNull(), any())
      val systemQuery =
        SystemQuery.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(systemQuery.type).isEqualTo(DEVICE_NAME)
    }
  }

  @Test
  fun deviceNameQueryResponse_successUpdatesDeviceName() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    val callback =
      argumentCaptor<Connector.QueryCallback>().run {
        verify(fakeConnector).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
        firstValue
      }
    callback.onSuccess(TEST_DEVICE_NAME.toByteArray(StandardCharsets.UTF_8))
    verify(mockStorage).updateAssociatedDeviceName(device.deviceId, TEST_DEVICE_NAME)
  }

  @Test
  fun deviceNameQueryResponse_successDoesNotUpdateDeviceNameIfEmpty() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    argumentCaptor<Connector.QueryCallback>() {
      verify(fakeConnector).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
      firstValue.onSuccess(ByteArray(0))
      verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
    }
  }

  @Test
  fun deviceNameQueryResponse_onErrorDoesNotUpdateDeviceName() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    argumentCaptor<Connector.QueryCallback>() {
      verify(fakeConnector).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
      firstValue.onError(/* response= */ null)
      verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
    }
  }

  @Test
  fun deviceNameQueryResponse_onQueryFailedToSendDoesNotUpdateDeviceName() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    argumentCaptor<Connector.QueryCallback>() {
      verify(fakeConnector).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
      firstValue.onQueryFailedToSend(/* isTransient= */ false)
      firstValue.onQueryFailedToSend(/* isTransient= */ true)
      verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
    }
  }

  @Test
  fun onQueryReceived_deviceNameQueryRespondsWithName() {
    val query = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    val queryId = 0

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)

    argumentCaptor<ByteArray> {
      verify(fakeConnector)
        .respondToQuerySecurely(eq(device), eq(queryId), eq(true), capture())
      assertThat(firstValue).isEqualTo(TEST_DEVICE_NAME.toByteArray(StandardCharsets.UTF_8))
    }
  }

  @Test
  fun onQueryReceived_unknownQueryTypeRespondsWithError() {
    val query = SystemQuery.newBuilder().setType(SYSTEM_QUERY_TYPE_UNKNOWN).build()
    val queryId = 0

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)

    verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  @Test
  fun onQueryReceived_nullNameFromNameProviderRespondsWithError() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSystemService(BluetoothManager::class.java).adapter.name = null
    systemFeature = spy(SystemFeature(context, mockStorage, fakeConnector))
    val query = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    val queryId = 0

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)
    verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  @Test
  fun onQueryReceived_failureToParseQueryRespondsWithError() {
    val queryId = 0

    fakeConnector.callback?.onQueryReceived(device, queryId, ByteUtils.randomBytes(100), null)

    verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(false), anyOrNull())
  }

  @Test
  fun onQueryReceived_driverDeviceRespondsWithDriver() {
    val queryId = 0
    val query = SystemQuery.newBuilder().setType(USER_ROLE).build()
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ null,
        /* belongsToDriver= */ true,
        /* hasSecureChannel= */ true
      )

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)

    argumentCaptor<ByteArray> {
      verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(true), capture())
      val response =
        SystemUserRoleResponse.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(response.role).isEqualTo(SystemUserRole.DRIVER)
    }
  }

  @Test
  fun onQueryReceived_passengerDeviceRespondsWithPassenger() {
    val queryId = 0
    val query = SystemQuery.newBuilder().setType(USER_ROLE).build()
    val device =
      ConnectedDevice(
        UUID.randomUUID().toString(),
        /* deviceName= */ null,
        /* belongsToDriver= */ false,
        /* hasSecureChannel= */ true
      )

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)

    argumentCaptor<ByteArray> {
      verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(true), capture())
      val response =
        SystemUserRoleResponse.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(response.role).isEqualTo(SystemUserRole.PASSENGER)
    }
  }

  companion object {
    const val TEST_DEVICE_NAME = "TEST_DEVICE_NAME"
  }
}
