/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.companiondevicesupport;

import static com.android.car.ui.core.CarUi.requireToolbar;
import static com.android.car.ui.toolbar.Toolbar.State.SUBPAGE;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Activity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.widget.Toast;
import com.android.car.ui.toolbar.ToolbarController;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.common.base.Strings;

/** Activity for out of band association. */
public class OobAssociationActivity extends FragmentActivity {
  private static final String TAG = "OobAssociationActivity";
  private static final String OOB_ADD_DEVICE_FRAGMENT_TAG = "OobAddAssociatedDeviceFragment";

  private static final String EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS";

  private ToolbarController toolbar;
  private OobAssociatedDeviceViewModel model;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_base_activity);

    String deviceAddress = getIntent().getStringExtra(EXTRA_DEVICE_ADDRESS);
    if (Strings.isNullOrEmpty(deviceAddress)) {
      loge(
          TAG,
          "Activity was started with an intent that didn't "
              + "specify the device to connect to, finishing");

      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    observeViewModel(deviceAddress);

    toolbar = requireToolbar(this);
    toolbar.setState(SUBPAGE);

    getSupportFragmentManager()
        .beginTransaction()
        .replace(
            R.id.fragment_container,
            new OobAddAssociatedDeviceFragment(),
            OOB_ADD_DEVICE_FRAGMENT_TAG)
        .commit();
    toolbar.getProgressBar().setVisible(true);
  }

  @Override
  protected void onStart() {
    super.onStart();
    model.startAssociation();
  }

  @Override
  protected void onStop() {
    super.onStop();
    model.stopAssociation();
  }

  @Override
  protected void onDestroy() {
    toolbar.getProgressBar().setVisible(false);
    super.onDestroy();
  }

  private void observeViewModel(String deviceAddress) {
    model =
        new ViewModelProvider(
                ViewModelStore::new,
                new OobAssociatedDeviceViewModelFactory(
                    getApplication(),
                    new OobEligibleDevice(deviceAddress, OobEligibleDevice.OOB_TYPE_BLUETOOTH),
                    getResources().getBoolean(R.bool.enable_spp),
                    getResources().getString(R.string.ble_device_name_prefix)))
            .get(OobAssociatedDeviceViewModel.class);

    model
        .getAssociationState()
        .observe(
            this,
            state -> {
              switch (state) {
                case COMPLETED:
                  model.resetAssociationState();
                  runOnUiThread(
                      () ->
                          Toast.makeText(
                                  getApplicationContext(),
                                  getString(R.string.continue_setup_toast_text),
                                  Toast.LENGTH_SHORT)
                              .show());
                  setResult(Activity.RESULT_OK);
                  finish();
                  break;
                case PENDING:
                case ERROR:
                case NONE:
                case STARTING:
                case STARTED:
                  break;
              }
            });
  }
}
