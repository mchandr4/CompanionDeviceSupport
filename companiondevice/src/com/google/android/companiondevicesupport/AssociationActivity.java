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

import android.app.AlertDialog;
import android.app.Dialog;
import androidx.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.companiondevicesupport.ui.Toolbar;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;

/** Activity class for association */
public class AssociationActivity extends FragmentActivity {

  private static final String TAG = "CompanionAssociationActivity";
  private static final String ADD_DEVICE_FRAGMENT_TAG = "AddAssociatedDeviceFragment";
  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "AssociatedDeviceDetailFragment";
  private static final String PAIRING_CODE_FRAGMENT_TAG = "ConfirmPairingCodeFragment";
  private static final String TURN_ON_BLUETOOTH_FRAGMENT_TAG = "TurnOnBluetoothFragment";
  private static final String ASSOCIATION_ERROR_FRAGMENT_TAG = "AssociationErrorFragment";
  private static final String REMOVE_DEVICE_DIALOG_TAG = "RemoveDeviceDialog";
  private static final String TURN_ON_BLUETOOTH_DIALOG_TAG = "TurnOnBluetoothDialog";

  private Toolbar toolbar;
  private AssociatedDeviceViewModel model;

  @Override
  public void onCreate(Bundle saveInstanceState) {
    super.onCreate(saveInstanceState);
    setContentView(R.layout.base_activity);

    toolbar = findViewById(R.id.toolbar);
    toolbar.setOnBackButtonClickListener(v -> finish());

    observeViewModel();
    if (saveInstanceState != null) {
      resumePreviousState();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Only start association if a device is not already associated.
    if (model.getDeviceDetails().getValue() == null) {
      model.startAssociation();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    model.stopAssociation();
  }

  private void observeViewModel() {
    model = ViewModelProviders.of(this).get(AssociatedDeviceViewModel.class);

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
                        toolbar.hideProgressBar();
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
        .getDeviceDetails()
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
        .getDeviceToRemove()
        .observe(
            this,
            device -> {
              if (device != null) {
                runOnUiThread(() -> showRemoveDeviceDialog(device));
              }
            });

    model
        .getRemovedDevice()
        .observe(
            this,
            device -> {
              if (device != null) {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                getApplicationContext(),
                                getString(
                                    R.string.device_removed_success_toast_text,
                                    device.getDeviceName()),
                                Toast.LENGTH_SHORT)
                            .show());
                finish();
              }
            });
    model
        .isFinished()
        .observe(
            this,
            isFinished -> {
              if (isFinished) {
                finish();
              }
            });
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
    toolbar.setSecondaryActionButton(R.string.retry, v -> retryAssociation());
    toolbar.setPrimaryActionButton(
        R.string.confirm,
        v -> {
          model.acceptVerification();
          dismissConfirmButtons();
        });
  }

  private void dismissConfirmButtons() {
    toolbar.hideSecondaryActionButton();
    toolbar.hidePrimaryActionButton();
  }

  private void showProgressBar() {
    toolbar.showProgressBar();
  }

  private void showRemoveDeviceDialog(AssociatedDevice device) {
    RemoveDeviceDialogFragment removeDeviceDialogFragment =
        RemoveDeviceDialogFragment.newInstance(
            device.getDeviceName(), (d, which) -> model.removeCurrentDevice());
    removeDeviceDialogFragment.show(getSupportFragmentManager(), REMOVE_DEVICE_DIALOG_TAG);
  }

  private void showTurnOnBluetoothDialog() {
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      TurnOnBluetoothDialogFragment fragment = new TurnOnBluetoothDialogFragment();
      fragment.show(getSupportFragmentManager(), TURN_ON_BLUETOOTH_DIALOG_TAG);
    }
  }

  private void resumePreviousState() {
    RemoveDeviceDialogFragment removeDeviceDialogFragment =
        (RemoveDeviceDialogFragment)
            getSupportFragmentManager().findFragmentByTag(REMOVE_DEVICE_DIALOG_TAG);
    if (removeDeviceDialogFragment != null) {
      removeDeviceDialogFragment.setOnConfirmListener((d, which) -> model.removeCurrentDevice());
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

  /** Dialog fragment to confirm removing an associated device. */
  public static class RemoveDeviceDialogFragment extends DialogFragment {
    private static final String DEVICE_NAME_KEY = "device_name";

    private DialogInterface.OnClickListener onConfirmListener;

    static RemoveDeviceDialogFragment newInstance(
        @NonNull String deviceName, DialogInterface.OnClickListener listener) {
      Bundle bundle = new Bundle();
      bundle.putString(DEVICE_NAME_KEY, deviceName);
      RemoveDeviceDialogFragment fragment = new RemoveDeviceDialogFragment();
      fragment.setArguments(bundle);
      fragment.setOnConfirmListener(listener);
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      Bundle bundle = getArguments();
      String deviceName = bundle.getString(DEVICE_NAME_KEY);
      String title = getString(R.string.remove_associated_device_title, deviceName);
      Spanned styledTitle = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY);
      return new AlertDialog.Builder(getActivity())
          .setTitle(styledTitle)
          .setMessage(getString(R.string.remove_associated_device_message))
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.forget), onConfirmListener)
          .setCancelable(true)
          .create();
    }

    void setOnConfirmListener(DialogInterface.OnClickListener onConfirmListener) {
      this.onConfirmListener = onConfirmListener;
    }
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
