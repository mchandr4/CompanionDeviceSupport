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

import static com.android.car.setupwizardlib.util.ResultCodes.RESULT_SKIP;
import static com.android.car.ui.core.CarUi.requireToolbar;
import static com.android.car.ui.toolbar.Toolbar.State.SUBPAGE;
import static com.google.android.connecteddevice.util.SafeLog.logd;

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import java.util.Arrays;
import java.util.List;

/** Activity class for association */
public class AssociationActivity extends FragmentActivity {

  private static final String TAG = "CompanionAssociationActivity";
  private static final String COMPANION_LANDING_FRAGMENT_TAG = "CompanionLandingFragment";
  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "AssociatedDeviceDetailFragment";
  private static final String PAIRING_CODE_FRAGMENT_TAG = "ConfirmPairingCodeFragment";
  private static final String TURN_ON_BLUETOOTH_FRAGMENT_TAG = "TurnOnBluetoothFragment";
  private static final String ASSOCIATION_ERROR_FRAGMENT_TAG = "AssociationErrorFragment";
  private static final String TURN_ON_BLUETOOTH_DIALOG_TAG = "TurnOnBluetoothDialog";
  private static final String EXTRA_AUTH_IS_SETUP_WIZARD = "is_setup_wizard";
  private static final String EXTRA_USE_IMMERSIVE_MODE = "useImmersiveMode";
  private static final String EXTRA_HIDE_SKIP_BUTTON = "hide_skip_button";

  private ToolbarController toolbar;
  private CarSetupWizardCompatLayout carSetupWizardLayout;
  private AssociatedDeviceViewModel model;
  private boolean isStartedForSuw = false;
  private boolean isImmersive = false;
  private boolean hideSkipButton = false;

  @Override
  public void onCreate(Bundle saveInstanceState) {
    resolveIntent();
    // Set theme before calling super.onCreate(bundle) to avoid recreating activity.
    setAssociationTheme();
    super.onCreate(saveInstanceState);
    prepareLayout();
    observeViewModel();
  }

  @Override
  protected void onStart() {
    super.onStart();
    handleImmersive();
  }

  @Override
  protected void onStop() {
    super.onStop();
    model.stopAssociation();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      handleImmersive();
    }
  }

  private void resolveIntent() {
    Intent intent = getIntent();
    if (intent == null) {
      return;
    }
    isStartedForSuw = intent.getBooleanExtra(EXTRA_AUTH_IS_SETUP_WIZARD, /* defaultValue= */ false);
    isImmersive = intent.getBooleanExtra(EXTRA_USE_IMMERSIVE_MODE, /* defaultValue= */ false);
    hideSkipButton = intent.getBooleanExtra(EXTRA_HIDE_SKIP_BUTTON, /* defaultValue= */ false);
  }

  private void setAssociationTheme() {
    if (isStartedForSuw) {
      setTheme(R.style.Theme_CompanionDevice_Car_SetupWizard_NoActionBar);
      return;
    }
    setTheme(R.style.Theme_CompanionDevice_Car_CarUi_WithToolbar);
  }

  private void prepareLayout() {
    if (isStartedForSuw) {
      setContentView(R.layout.suw_companion_base_activity);
      carSetupWizardLayout = findViewById(R.id.car_setup_wizard_layout);
      carSetupWizardLayout.setBackButtonListener(l -> onBackPressed());
      return;
    }
    setContentView(R.layout.settings_base_activity);
    toolbar = requireToolbar(this);
    toolbar.setState(SUBPAGE);
  }

  private void handleImmersive() {
    if (!isImmersive) {
      return;
    }
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            // Set the content to appear under the system bars so that the
            // content doesn't resize when the system bars hide and show.
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Hide the nav bar and status bar
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  private void observeViewModel() {
    List<String> transportProtocols =
        Arrays.asList(getResources().getStringArray(R.array.transport_protocols));
    model =
        new ViewModelProvider(
                (ViewModelStoreOwner) this,
                new AssociatedDeviceViewModelFactory(
                    getApplication(),
                    transportProtocols.contains(TransportProtocols.PROTOCOL_SPP),
                    getResources().getString(R.string.ble_device_name_prefix)))
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
                  runOnUiThread(this::hideProgressBar);
                  break;
                case STARTING:
                case STARTED:
                  runOnUiThread(
                      () -> {
                        showCompanionLandingFragment();
                        showProgressBar();
                      });
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
                // Features always expect activity result when they start AssociationActivity.
                setDeviceToReturn(device);
                finish();
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
                showCompanionLandingFragment();
              }
            });
    model
        .isServiceConnected()
        .observe(
            this,
            isServiceConnected -> {
              logd(TAG, "Service connection status: " + isServiceConnected);
              if (isServiceConnected) {
                showCompanionLandingFragment();
                hideProgressBar();
              } else {
                showLoadingScreen();
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

  private void showLoadingScreen() {
    showSkipButton();
    Fragment currentFragment =
        getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (currentFragment != null) {
      getSupportFragmentManager().beginTransaction().remove(currentFragment).commit();
    }
    showProgressBar();
  }

  private void showCompanionLandingFragment() {
    if (getResources().getBoolean(R.bool.enable_qr_code)) {
      logd(TAG, "Showing LandingFragment with QR code.");
      showCompanionQrCodeLandingFragment();
      return;
    }
    CompanionLandingFragment fragment =
        (CompanionLandingFragment)
            getSupportFragmentManager().findFragmentByTag(COMPANION_LANDING_FRAGMENT_TAG);
    if (fragment != null && fragment.isVisible()) {
      return;
    }
    fragment = CompanionLandingFragment.newInstance(isStartedForSuw);
    launchFragment(fragment, COMPANION_LANDING_FRAGMENT_TAG);
  }

  private void showCompanionQrCodeLandingFragment() {
    CompanionQrCodeLandingFragment fragment =
        (CompanionQrCodeLandingFragment)
            getSupportFragmentManager().findFragmentByTag(COMPANION_LANDING_FRAGMENT_TAG);
    if (fragment != null && fragment.isVisible()) {
      return;
    }
    fragment = CompanionQrCodeLandingFragment.newInstance(isStartedForSuw);
    launchFragment(fragment, COMPANION_LANDING_FRAGMENT_TAG);
  }

  private void showTurnOnBluetoothFragment() {
    showProgressBar();
    TurnOnBluetoothFragment fragment = new TurnOnBluetoothFragment();
    launchFragment(fragment, TURN_ON_BLUETOOTH_FRAGMENT_TAG);
  }

  private void showConfirmPairingCodeFragment(String pairingCode) {
    ConfirmPairingCodeFragment fragment =
        ConfirmPairingCodeFragment.newInstance(isStartedForSuw, pairingCode);
    launchFragment(fragment, PAIRING_CODE_FRAGMENT_TAG);
    showConfirmButtons();
  }

  private void showAssociationErrorFragment() {
    dismissButtons();
    showSkipButton();
    showProgressBar();
    AssociationErrorFragment fragment = new AssociationErrorFragment();
    launchFragment(fragment, ASSOCIATION_ERROR_FRAGMENT_TAG);
  }

  private void showAssociatedDeviceDetailFragment() {
    dismissButtons();
    hideProgressBar();
    AssociatedDeviceDetailFragment fragment = new AssociatedDeviceDetailFragment();
    launchFragment(fragment, DEVICE_DETAIL_FRAGMENT_TAG);
    showTurnOnBluetoothDialog();
  }

  private void showConfirmButtons() {
    if (isStartedForSuw) {
      carSetupWizardLayout.setPrimaryToolbarButtonText(getString(R.string.confirm));
      carSetupWizardLayout.setPrimaryToolbarButtonVisible(true);
      carSetupWizardLayout.setPrimaryToolbarButtonListener(
          l -> {
            model.acceptVerification();
            dismissButtons();
          });
      carSetupWizardLayout.setSecondaryToolbarButtonText(getString(R.string.retry));
      carSetupWizardLayout.setSecondaryToolbarButtonVisible(true);
      carSetupWizardLayout.setSecondaryToolbarButtonListener(l -> retryAssociation());
      return;
    }
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
                  dismissButtons();
                })
            .build();
    toolbar.setMenuItems(Arrays.asList(cancelButton, confirmButton));
  }

  private void dismissButtons() {
    if (isStartedForSuw) {
      carSetupWizardLayout.setPrimaryToolbarButtonVisible(false);
      carSetupWizardLayout.setSecondaryToolbarButtonVisible(false);
      return;
    }
    toolbar.setMenuItems(null);
  }

  private void showSkipButton() {
    if (!isStartedForSuw || hideSkipButton) {
      return;
    }
    carSetupWizardLayout.setPrimaryToolbarButtonText(getString(R.string.skip));
    carSetupWizardLayout.setPrimaryToolbarButtonVisible(true);
    carSetupWizardLayout.setPrimaryToolbarButtonListener(
        v -> {
          setResult(RESULT_SKIP);
          finish();
        });
  }

  private void showProgressBar() {
    if (isStartedForSuw) {
      carSetupWizardLayout.setProgressBarVisible(true);
      return;
    }
    toolbar.getProgressBar().setVisible(true);
  }

  private void hideProgressBar() {
    if (isStartedForSuw) {
      carSetupWizardLayout.setProgressBarVisible(false);
      return;
    }
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
    dismissButtons();
    showProgressBar();
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(PAIRING_CODE_FRAGMENT_TAG);
    if (fragment != null) {
      getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }
    model.retryAssociation();
  }

  private void setDeviceToReturn(AssociatedDevice device) {
    Intent intent = new Intent();
    intent.putExtra(RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    setResult(RESULT_OK, intent);
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
              getString(R.string.turn_on),
              (d, w) -> {
                BluetoothAdapter.getDefaultAdapter().enable();
                TurnOnBluetoothDialogFragment.this.dismiss();
              })
          .setNegativeButton(getString(R.string.not_now), null)
          .setCancelable(true)
          .create();
    }
  }
}
