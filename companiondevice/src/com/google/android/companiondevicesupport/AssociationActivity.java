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

import android.app.AlertDialog;
import android.app.Dialog;
import androidx.lifecycle.ViewModelProvider;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.widget.Toast;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import java.util.Arrays;

/** Activity class for association */
public class AssociationActivity extends FragmentActivity {

  private static final String TAG = "CompanionAssociationActivity";
  private static final String ADD_DEVICE_FRAGMENT_TAG = "AddAssociatedDeviceFragment";
  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "AssociatedDeviceDetailFragment";
  private static final String PAIRING_CODE_FRAGMENT_TAG = "ConfirmPairingCodeFragment";
  private static final String TURN_ON_BLUETOOTH_FRAGMENT_TAG = "TurnOnBluetoothFragment";
  private static final String ASSOCIATION_ERROR_FRAGMENT_TAG = "AssociationErrorFragment";
  private static final String TURN_ON_BLUETOOTH_DIALOG_TAG = "TurnOnBluetoothDialog";

  private ToolbarController toolbar;
  private AssociatedDeviceViewModel model;

  @Override
  public void onCreate(Bundle saveInstanceState) {
    super.onCreate(saveInstanceState);
    setContentView(R.layout.base_activity);

    toolbar = requireToolbar(this);
    toolbar.setState(SUBPAGE);

    observeViewModel();
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Only start association if a device is not already associated.
    if (model.getCurrentDeviceDetails().getValue() == null) {
      model.startAssociation();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    model.stopAssociation();
  }

  private void observeViewModel() {
    model =
        new ViewModelProvider(
                this,
                new AssociatedDeviceViewModelFactory(
                    getApplication(), getResources().getBoolean(R.bool.enable_spp)))
            .get(AssociatedDeviceViewModel.class);

    model
        .getAssociationState()
        .observe(
            this,
            state -> {
              switch (state) {
                case PENDING:
                  if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    runOnUiThread(this::showTurnOnBluetoothFragment);
                  }
                  break;
                case COMPLETED:
                  model.resetAssociationState();
                  runOnUiThread(
                      () ->
                          Toast.makeText(
                                  getApplicationContext(),
                                  getString(R.string.continue_setup_toast_text),
                                  Toast.LENGTH_SHORT)
                              .show());
                  break;
                case ERROR:
                  runOnUiThread(this::showAssociationErrorFragment);
                  break;
                case NONE:
                  runOnUiThread(
                      () -> {
                        dismissConfirmButtons();
                        hideProgressBar();
                      });
                  break;
                case STARTING:
                case STARTED:
                  runOnUiThread(this::showProgressBar);
                  break;
              }
            });

    model
        .getBluetoothState()
        .observe(
            this,
            state -> {
              if (state != BluetoothAdapter.STATE_ON
                  && getSupportFragmentManager().findFragmentByTag(DEVICE_DETAIL_FRAGMENT_TAG)
                      != null) {
                runOnUiThread(this::showTurnOnBluetoothDialog);
              }
            });

    model
        .getAdvertisedCarName()
        .observe(
            this,
            name -> {
              if (name != null) {
                runOnUiThread(() -> showAddAssociatedDeviceFragment(name));
              }
            });
    model
        .getPairingCode()
        .observe(
            this,
            code -> {
              if (code != null) {
                runOnUiThread(() -> showConfirmPairingCodeFragment(code));
              }
            });
    model
        .getCurrentDeviceDetails()
        .observe(
            this,
            deviceDetails -> {
              if (deviceDetails == null) {
                return;
              }
              AssociatedDevice device = deviceDetails.getAssociatedDevice();
              if (isStartedByFeature()) {
                setDeviceToReturn(device);
              } else {
                runOnUiThread(this::showAssociatedDeviceDetailFragment);
              }
            });
    model
        .getRemovedDevice()
        .observe(
            this,
            device -> {
              if (device != null) {
                runOnUiThread(() -> showDeviceRemovedToast(device.getDeviceName()));
                finish();
              }
            });
    model
        .isServiceDisconnected()
        .observe(
            this,
            isServiceDisconnected -> {
              if (isServiceDisconnected) {
                finish();
              }
            });
  }

  private void showDeviceRemovedToast(String deviceName) {
    Toast.makeText(
            getApplicationContext(),
            getString(R.string.device_removed_success_toast_text, deviceName),
            Toast.LENGTH_SHORT)
        .show();
  }

  private void showTurnOnBluetoothFragment() {
    showProgressBar();
    TurnOnBluetoothFragment fragment = new TurnOnBluetoothFragment();
    launchFragment(fragment, TURN_ON_BLUETOOTH_FRAGMENT_TAG);
  }

  private void showAddAssociatedDeviceFragment(String deviceName) {
    AddAssociatedDeviceFragment fragment = AddAssociatedDeviceFragment.newInstance(deviceName);
    launchFragment(fragment, ADD_DEVICE_FRAGMENT_TAG);
  }

  private void showConfirmPairingCodeFragment(String pairingCode) {
    ConfirmPairingCodeFragment fragment = ConfirmPairingCodeFragment.newInstance(pairingCode);
    launchFragment(fragment, PAIRING_CODE_FRAGMENT_TAG);
    showConfirmButtons();
  }

  private void showAssociationErrorFragment() {
    dismissConfirmButtons();
    showProgressBar();
    AssociationErrorFragment fragment = new AssociationErrorFragment();
    launchFragment(fragment, ASSOCIATION_ERROR_FRAGMENT_TAG);
  }

  private void showAssociatedDeviceDetailFragment() {
    AssociatedDeviceDetailFragment fragment = new AssociatedDeviceDetailFragment();
    launchFragment(fragment, DEVICE_DETAIL_FRAGMENT_TAG);
    showTurnOnBluetoothDialog();
  }

  private void showConfirmButtons() {
    MenuItem cancelButton =
        MenuItem.builder(this)
            .setTitle(R.string.retry)
            .setOnClickListener(i -> retryAssociation())
            .build();
    MenuItem confirmButton =
        MenuItem.builder(this)
            .setTitle(R.string.confirm)
            .setOnClickListener(
                i -> {
                  model.acceptVerification();
                  dismissConfirmButtons();
                })
            .build();
    toolbar.setMenuItems(Arrays.asList(cancelButton, confirmButton));
  }

  private void dismissConfirmButtons() {
    toolbar.setMenuItems(null);
  }

  private void showProgressBar() {
    toolbar.getProgressBar().setVisible(true);
  }

  private void hideProgressBar() {
    toolbar.getProgressBar().setVisible(false);
  }

  private void showTurnOnBluetoothDialog() {
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      TurnOnBluetoothDialogFragment fragment = new TurnOnBluetoothDialogFragment();
      fragment.show(getSupportFragmentManager(), TURN_ON_BLUETOOTH_DIALOG_TAG);
    }
  }

  private void launchFragment(Fragment fragment, String tag) {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, fragment, tag)
        .commit();
  }

  private void retryAssociation() {
    dismissConfirmButtons();
    showProgressBar();
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(PAIRING_CODE_FRAGMENT_TAG);
    if (fragment != null) {
      getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }
    model.retryAssociation();
  }

  private void setDeviceToReturn(AssociatedDevice device) {
    if (!isStartedByFeature()) {
      return;
    }
    Intent intent = new Intent();
    intent.putExtra(RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    setResult(RESULT_OK, intent);
    finish();
  }

  private boolean isStartedByFeature() {
    String action = getIntent().getAction();
    return RemoteFeature.ACTION_ASSOCIATION_SETTING.equals(action);
  }

  /** Dialog fragment to turn on bluetooth. */
  public static class TurnOnBluetoothDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.turn_on_bluetooth_dialog_title))
          .setMessage(getString(R.string.turn_on_bluetooth_dialog_message))
          .setPositiveButton(
              getString(R.string.turn_on), (d, w) -> BluetoothAdapter.getDefaultAdapter().enable())
          .setNegativeButton(getString(R.string.not_now), null)
          .setCancelable(true)
          .create();
    }
  }
}
