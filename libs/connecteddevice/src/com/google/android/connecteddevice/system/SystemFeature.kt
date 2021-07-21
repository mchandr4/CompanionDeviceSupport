package com.google.android.connecteddevice.system

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.android.companionprotos.SystemQuery
import com.google.android.companionprotos.SystemQueryType.DEVICE_NAME
import com.google.android.connecteddevice.api.IConnectedDeviceManager
import com.google.android.connecteddevice.api.RemoteFeature
import com.google.android.connecteddevice.model.ConnectedDevice
import com.google.android.connecteddevice.storage.ConnectedDeviceStorage
import com.google.android.connecteddevice.util.SafeLog.logd
import com.google.android.connecteddevice.util.SafeLog.loge
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets

/** Feature responsible for system queries. */
open class SystemFeature internal constructor(
  context: Context,
  manager: IConnectedDeviceManager,
  private val storage: ConnectedDeviceStorage,
  private val nameProvider: () -> String?
) : RemoteFeature(context, SYSTEM_FEATURE_ID, manager) {

  constructor(
    context: Context,
    manager: IConnectedDeviceManager,
    storage: ConnectedDeviceStorage
  ) : this(context, manager, storage, { BluetoothAdapter.getDefaultAdapter()?.name })

  override fun onSecureChannelEstablished(device: ConnectedDevice) =
    onSecureChannelEstablishedInternal(device)

  @VisibleForTesting
  internal fun onSecureChannelEstablishedInternal(device: ConnectedDevice) {
    logd(TAG, "Secure channel has been established. Issuing device name query.")
    val deviceNameQuery = SystemQuery.newBuilder().setType(DEVICE_NAME).build()
    sendQuerySecurely(
      device,
      deviceNameQuery.toByteArray(),
      /* parameters= */ null,
      object : QueryCallback {
        override fun onSuccess(response: ByteArray?) {
          if (response?.isNotEmpty() != true) {
            loge(TAG, "Received a null or empty device name query response. Ignoring.")
            return
          }
          val deviceName = String(response, StandardCharsets.UTF_8)
          logd(TAG, "Updating device ${device.deviceId}'s name to $deviceName.")
          storage.updateAssociatedDeviceName(device.deviceId, deviceName)
        }
      }
    )
  }

  override fun onQueryReceived(
    device: ConnectedDevice,
    queryId: Int,
    request: ByteArray,
    parameters: ByteArray
  ) = onQueryReceivedInternal(device, queryId, request)

  @VisibleForTesting
  internal fun onQueryReceivedInternal(
    device: ConnectedDevice,
    queryId: Int,
    request: ByteArray,
  ) {
    val query = try {
      SystemQuery.parseFrom(request, ExtensionRegistryLite.getEmptyRegistry())
    } catch (e: InvalidProtocolBufferException) {
      loge(TAG, "Unable to parse system query.", e)
      respondWithError(device, queryId)
      return
    }

    when (query.type) {
      DEVICE_NAME -> respondWithDeviceName(device, queryId)
      else -> {
        loge(TAG, "Received unknown query type ${query.type}. Responding with error.")
        respondWithError(device, queryId)
      }
    }
  }

  private fun respondWithDeviceName(device: ConnectedDevice, queryId: Int) {
    val deviceName = nameProvider()
    logd(TAG, "Responding to query for device name with $deviceName.")
    respondToQuerySecurely(
      device,
      queryId,
      deviceName != null,
      deviceName?.toByteArray(StandardCharsets.UTF_8)
    )
  }

  private fun respondWithError(device: ConnectedDevice, queryId: Int) =
    respondToQuerySecurely(device, queryId, /* success= */ false, /* response= */ null)

  companion object {
    private const val TAG = "SystemFeature"
  }
}
