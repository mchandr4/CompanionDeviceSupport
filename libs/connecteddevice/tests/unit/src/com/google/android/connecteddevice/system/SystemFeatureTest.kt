package com.google.android.connecteddevice.system

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.companionprotos.DeviceOS
import com.google.android.companionprotos.DeviceVersionsResponse
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.companionprotos.SystemQueryType.DEVICE_OS
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
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SystemFeatureTest {

  private val mockStorage: ConnectedDeviceStorage = mock()

  private val fakeConnector = spy(FakeConnector())

  private val device =
    ConnectedDevice(
      UUID.randomUUID().toString(),
      /* deviceName= */ null,
      /* belongsToDriver= */ true,
      /* hasSecureChannel= */ true,
    )

  private val queriedFeature1 = UUID.randomUUID()
  private val queriedFeature2 = UUID.randomUUID()
  private val onConnectionQueriedFeatures = listOf(queriedFeature1, queriedFeature2)

  private lateinit var systemFeature: SystemFeature

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSystemService(BluetoothManager::class.java).adapter.name = TEST_DEVICE_NAME
    systemFeature =
      SystemFeature(
        context,
        TestLifecycleOwner(),
        mockStorage,
        fakeConnector,
        onConnectionQueriedFeatures,
      )
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
  fun onSecureChannelEstablished_sendsQueryForDeviceNameAndDeviceOs() {
    fakeConnector.callback?.onSecureChannelEstablished(device)

    argumentCaptor<ByteArray>() {
      // onSecureChannelEstablished calls sendQuerySecurely twice, once for device name and once for
      // device OS.
      verify(fakeConnector, times(2)).sendQuerySecurely(eq(device), capture(), anyOrNull(), any())
      val nameSystemQuery =
        SystemQuery.parseFrom(firstValue, ExtensionRegistryLite.getEmptyRegistry())
      val osSystemQuery =
        SystemQuery.parseFrom(secondValue, ExtensionRegistryLite.getEmptyRegistry())
      assertThat(nameSystemQuery.type).isEqualTo(DEVICE_NAME)
      assertThat(osSystemQuery.type).isEqualTo(DEVICE_OS)
    }
  }

  @Test
  fun onSecureChannelEstablished_sendsQueryForFeatureSupportStatus() {
    runBlocking {
      fakeConnector.callback?.onSecureChannelEstablished(device)
      shadowOf(Looper.getMainLooper()).idle()

      argumentCaptor<List<UUID>>() {
        verify(fakeConnector).queryFeatureSupportStatuses(eq(device), capture())
        val queriedFeatures = firstValue
        assertThat(queriedFeatures).isEqualTo(onConnectionQueriedFeatures)
      }
    }
  }

  @Test
  fun deviceNameQueryResponse_successUpdatesDeviceName() =
    runBlocking<Unit> {
      fakeConnector.callback?.onSecureChannelEstablished(device)

      val callback =
        argumentCaptor<Connector.QueryCallback>().run {
          // onSecureChannelEstablished calls sendQuerySecurely twice, once for device name and once
          // for device OS.
          verify(fakeConnector, times(2))
            .sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
          firstValue
        }
      callback.onSuccess(TEST_DEVICE_NAME.toByteArray(StandardCharsets.UTF_8))
      verify(mockStorage).updateAssociatedDeviceName(device.deviceId, TEST_DEVICE_NAME)
    }

  @Test
  fun deviceNameQueryResponse_successDoesNotUpdateDeviceNameIfEmpty() =
    runBlocking<Unit> {
      fakeConnector.callback?.onSecureChannelEstablished(device)

      argumentCaptor<Connector.QueryCallback>() {
        // onSecureChannelEstablished calls sendQuerySecurely twice, once for device name and once
        // for device OS.
        verify(fakeConnector, times(2)).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
        firstValue.onSuccess(ByteArray(0))
        verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
      }
    }

  @Test
  fun deviceNameQueryResponse_onErrorDoesNotUpdateDeviceNameOrDeviceOs() =
    runBlocking<Unit> {
      fakeConnector.callback?.onSecureChannelEstablished(device)

      argumentCaptor<Connector.QueryCallback>() {
        verify(fakeConnector, atLeastOnce())
          .sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
        firstValue.onError(ByteArray(0))
        secondValue.onError(ByteArray(0))
        verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceOs(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceOsVersion(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceCompanionSdkVersion(any(), any())
      }
    }

  @Test
  fun deviceNameQueryResponse_onQueryFailedToSendDoesNotUpdateDeviceName() =
    runBlocking<Unit> {
      fakeConnector.callback?.onSecureChannelEstablished(device)

      argumentCaptor<Connector.QueryCallback>() {
        // onSecureChannelEstablished calls sendQuerySecurely twice, once for device name and once
        // for
        // device OS.
        verify(fakeConnector, times(2)).sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
        firstValue.onQueryFailedToSend(isTransient = false)
        firstValue.onQueryFailedToSend(isTransient = true)
        secondValue.onQueryFailedToSend(isTransient = false)
        secondValue.onQueryFailedToSend(isTransient = true)
        verify(mockStorage, never()).updateAssociatedDeviceName(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceOs(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceOsVersion(any(), any())
        verify(mockStorage, never()).updateAssociatedDeviceCompanionSdkVersion(any(), any())
      }
    }

  @Test
  fun deviceOSQueryResponse_successUpdatesDeviceOs() =
    runBlocking<Unit> {
      val testDeviceOs = DeviceOS.ANDROID
      val testDeviceOsVersion = "TEST_DEVICE_OS_VERSION"
      val testDeviceCompanionSdkVersion = "TEST_DEVICE_COMPANION_SDK_VERSION"

      fakeConnector.callback?.onSecureChannelEstablished(device)

      val callback =
        argumentCaptor<Connector.QueryCallback>().run {
          // onSecureChannelEstablished calls sendQuerySecurely twice, once for device name and once
          // for device OS.
          verify(fakeConnector, times(2))
            .sendQuerySecurely(eq(device), any(), anyOrNull(), capture())
          secondValue
        }
      val response: DeviceVersionsResponse =
        DeviceVersionsResponse.newBuilder()
          .setOs(testDeviceOs)
          .setOsVersion(testDeviceOsVersion)
          .setCompanionSdkVersion(testDeviceCompanionSdkVersion)
          .build()
      callback.onSuccess(response.toByteArray())
      verify(mockStorage).updateAssociatedDeviceOs(device.deviceId, testDeviceOs)
      verify(mockStorage).updateAssociatedDeviceOsVersion(device.deviceId, testDeviceOsVersion)
      verify(mockStorage)
        .updateAssociatedDeviceCompanionSdkVersion(device.deviceId, testDeviceCompanionSdkVersion)
    }

  @Test
  fun onQueryReceived_deviceNameQueryRespondsWithName() {
    val query = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    val queryId = 0

    fakeConnector.callback?.onQueryReceived(device, queryId, query.toByteArray(), null)

    argumentCaptor<ByteArray> {
      verify(fakeConnector).respondToQuerySecurely(eq(device), eq(queryId), eq(true), capture())
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
    systemFeature = spy(SystemFeature(context, TestLifecycleOwner(), mockStorage, fakeConnector))
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
        /* hasSecureChannel= */ true,
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
        /* hasSecureChannel= */ true,
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
