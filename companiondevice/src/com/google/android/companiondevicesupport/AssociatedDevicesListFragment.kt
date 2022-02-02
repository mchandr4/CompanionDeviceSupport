/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.google.android.companiondevicesupport

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.android.car.ui.recyclerview.CarUiContentListItem
import com.android.car.ui.recyclerview.CarUiContentListItem.Action
import com.android.car.ui.recyclerview.CarUiListItem
import com.android.car.ui.recyclerview.CarUiListItemAdapter
import com.android.car.ui.recyclerview.CarUiRecyclerView
import com.google.android.connecteddevice.model.TransportProtocols
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState.CONNECTED
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState.DETECTED
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails.ConnectionState.NOT_DETECTED
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory
import com.google.android.connecteddevice.util.SafeLog.logd

/** Fragment that shows the a list of associated devices and their connection status. */
class AssociatedDevicesListFragment : Fragment() {
  private lateinit var deviceListView: CarUiRecyclerView
  private lateinit var model: AssociatedDeviceViewModel
  private lateinit var adapter: CarUiListItemAdapter

  /** The list of devices that are set within the [adapter]. */
  private val listItems = mutableListOf<CarUiListItem>()

  /** A listener for clicks on items within the list shown by this fragment. */
  interface OnListItemClickListener {
    /**
     * Invoked when an item in the list has been clicked. This method is passed the device that is
     * being shown by the item.
     */
    fun onListItemClicked(associatedDeviceDetails: AssociatedDeviceDetails)
  }

  public var onListItemClickListener: OnListItemClickListener? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View =
    inflater.inflate(
      R.layout.associated_devices_list_fragment,
      container,
      /* attachToRoot= */ false
    )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    deviceListView = view.findViewById(R.id.associated_devices_list) as CarUiRecyclerView
    deviceListView.adapter = CarUiListItemAdapter(listItems).also { adapter = it }

    model = obtainAssociatedDeviceViewModel()
    model.getAssociatedDevicesDetails().observe(this, ::refreshDeviceList)
  }

  private fun obtainAssociatedDeviceViewModel(): AssociatedDeviceViewModel {
    val activity = requireActivity()
    val transportProtocols = resources.getStringArray(R.array.transport_protocols)

    return ViewModelProvider(
        activity as ViewModelStoreOwner,
        AssociatedDeviceViewModelFactory(
          activity.getApplication(),
          transportProtocols.contains(TransportProtocols.PROTOCOL_SPP),
          resources.getString(R.string.ble_device_name_prefix),
          resources.getBoolean(R.bool.enable_passenger)
        )
      )
      .get(AssociatedDeviceViewModel::class.java)
  }

  private fun refreshDeviceList(associatedDeviceDetails: List<AssociatedDeviceDetails>) {
    logd(TAG, "Change detected in associated devices. Refreshing list.")

    // The adapter retains a reference to this list. So modify it in-place to ensure the adapter
    // has the up-to-date contents.
    listItems.clear()
    listItems.addAll(associatedDeviceDetails.map { it.toCarUiContentListItem() })

    adapter.notifyDataSetChanged()
  }

  /**
   * Converts this [AssociatedDeviceDetails] to its analogous [CarUiContentListItem].
   *
   * When the list item is clicked, any [onListItemClickListener] set on this fragment will be
   * invoked.
   */
  private fun AssociatedDeviceDetails.toCarUiContentListItem(): CarUiContentListItem {
    val context = requireContext()

    return CarUiContentListItem(Action.CHEVRON).apply {
      setTitle(deviceName)
      setBody(
        context.getString(
          if (belongsToDriver()) R.string.driver_device else R.string.passenger_device
        )
      )
      context.getDrawable(R.drawable.ic_connection_indicator)?.mutate()?.let {
        icon =
          DrawableCompat.wrap(it).apply {
            setTint(context.getColor(connectionState.toColorRes(isConnectionEnabled)))
          }
      }

      setOnItemClickedListener {
        onListItemClickListener?.onListItemClicked(this@toCarUiContentListItem)
      }
    }
  }

  @ColorRes
  private fun ConnectionState.toColorRes(isConnectionEnabled: Boolean): Int =
    if (isConnectionEnabled) {
      when (this) {
        NOT_DETECTED -> {
          R.color.connection_color_not_detected
        }
        DETECTED -> {
          R.color.connection_color_detected
        }
        CONNECTED -> {
          R.color.connection_color_connected
        }
      }
    } else {
      R.color.connection_color_disconnected
    }

  companion object {
    private const val TAG = "AssociatedDevicesListFragment"
  }
}
