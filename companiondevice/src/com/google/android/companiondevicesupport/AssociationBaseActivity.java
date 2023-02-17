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

package com.google.android.companiondevicesupport;

import static com.android.car.setupwizardlib.util.ResultCodes.RESULT_SKIP;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.UserManager;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Activity base class for association */
public abstract class AssociationBaseActivity extends FragmentActivity {

  private static final String TAG = "CompanionAssociationBaseActivity";
  private static final String COMPANION_LANDING_FRAGMENT_TAG = "CompanionLandingFragment";
  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "AssociatedDeviceDetailFragment";
  private static final String DEVICES_LIST_FRAGMENT_TAG = "AssociatedDevicesListFragment";
  private static final String PAIRING_CODE_FRAGMENT_TAG = "ConfirmPairingCodeFragment";
  private static final String TURN_ON_BLUETOOTH_FRAGMENT_TAG = "TurnOnBluetoothFragment";
  private static final String ASSOCIATION_ERROR_FRAGMENT_TAG = "AssociationErrorFragment";
  private static final String TURN_ON_BLUETOOTH_DIALOG_TAG = "TurnOnBluetoothDialog";
  private static final String ASSOCIATED_DEVICE_DETAILS_BACKSTACK_NAME =
      "AssociatedDeviceDetailsBackstack";
  private static final String EXTRA_AUTH_IS_SETUP_WIZARD = "is_setup_wizard";

  private final List<String> requiredPermissions = new ArrayList<>();
  protected AssociatedDeviceViewModel model;

  private boolean isPassengerEnabled = false;
  protected boolean isStartedForSuw = false;
  protected boolean isStartedForSetupProfile = false;
  private boolean pendingAssociationAfterPerms = false;
  private ActivityResultLauncher<String[]> requestPermissionLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Intent intent = getIntent();
    // TODO: Remove this check when all the setup wizard build updated the name of the action.
    if (intent != null) {
      if (intent.getBooleanExtra(EXTRA_AUTH_IS_SETUP_WIZARD, /* defaultValue= */ false)
          && RemoteFeature.ACTION_ASSOCIATION_SETTING.equals(intent.getAction())) {
        loge(TAG, "Received unsupported intent from setup wizard. Skip.");
        setResult(RESULT_SKIP);
        finish();
        return;
      }
    }
    // TODO(b/228328725): Remove strict mode change when the violation is resolved.
    maybeEnableStrictMode();

    // Set theme before calling super.onCreate(bundle) to avoid recreating activity.
    setAssociationTheme();
    super.onCreate(savedInstanceState);
    prepareLayout();

    if (VERSION.SDK_INT >= VERSION_CODES.S) {
      requiredPermissions.add(permission.BLUETOOTH_SCAN);
      requiredPermissions.add(permission.BLUETOOTH_ADVERTISE);
      requiredPermissions.add(permission.BLUETOOTH_CONNECT);
    }

    // Only need to attach the click listener if we are recreating this activity due to a
    // configuration change.
    isPassengerEnabled = getResources().getBoolean(R.bool.enable_passenger);
    if (isPassengerEnabled && savedInstanceState != null) {
      maybeAttachItemClickListener();
    }

    observeViewModel();
    requestPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            results -> {
              for (boolean granted : results.values()) {
                if (!granted) {
                  loge(
                      TAG, "At least one permission was not granted. Unable to start association.");
                  showAssociationErrorFragment();
                  return;
                }
              }
              logd(TAG, "All permissions have been granted.");
              if (pendingAssociationAfterPerms) {
                pendingAssociationAfterPerms = false;
                model.startAssociation();
              }
            });
  }

  /**
   * Attempts to attach a click listener to a {@link AssociatedDevicesListFragment} if it is
   * currently visible.
   */
  private void maybeAttachItemClickListener() {
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(DEVICES_LIST_FRAGMENT_TAG);
    if (fragment == null) {
      return;
    }
    ((AssociatedDevicesListFragment) fragment)
        .setOnListItemClickListener(this::showAssociatedDeviceDetailFragment);
  }

  @Override
  protected void onStart() {
    super.onStart();
    UserManager userManager = getSystemService(UserManager.class);
    if (userManager.isGuestUser()) {
      logw(TAG, "Companion opened under guest profile.");
      handleGuestProfile();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    // Resets the UI/model when activity goes into the background during association.
    model.stopAssociation();
    finish();
  }

  /** Companion activity is not supported under guest profile. */
  protected abstract void handleGuestProfile();

  /** Set the theme of the showing fragment. */
  protected abstract void setAssociationTheme();

  protected abstract void prepareLayout();

  /** Shows confirm button on pairing code page. */
  protected abstract void showConfirmButtons();

  /** Dismissed all toolbar buttons. */
  protected abstract void dismissButtons();

  protected abstract void showSkipButton();

  /** Only available under passenger mode. */
  protected abstract void showAssociateButton();

  /** Sets the local progress bar visibility. */
  protected abstract void setIsProgressBarVisible(boolean isVisible);

  /** Performs permission checks and starts association flow. */
  void startAssociation() {
    logd(TAG, "Starting association.");
    if (pendingAssociationAfterPerms) {
      logd(TAG, "Pending on user grant permission, ignore start association request.");
      return;
    }
    if (maybeAskForPermissions()) {
      logd(TAG, "Waiting for permissions to be granted before association can be started.");
      pendingAssociationAfterPerms = true;
      return;
    }
    model.startAssociation();
  }

  /**
   * Prompts the user to accept the required runtime permissions if needed. Returns {@code true} if
   * the user was prompted, otherwise {@code false}.
   */
  @CanIgnoreReturnValue
  private boolean maybeAskForPermissions() {
    logd(TAG, "Checking if there are any runtime permissions that have not yet been granted.");
    List<String> missingPermissions = new ArrayList<>();
    for (String permission : requiredPermissions) {
      if (ContextCompat.checkSelfPermission(this, permission)
          == PackageManager.PERMISSION_GRANTED) {
        continue;
      }
      logd(TAG, "Required permission " + permission + " has not been granted yet.");
      missingPermissions.add(permission);
    }
    if (missingPermissions.isEmpty()) {
      logd(TAG, "The user has already granted all necessary permissions.");
      return false;
    }
    logd(TAG, "Prompting the user for all the required permissions.");
    requestPermissionLauncher.launch(missingPermissions.toArray(new String[0]));
    return true;
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
                    getResources().getString(R.string.ble_device_name_prefix),
                    getResources().getBoolean(R.bool.enable_passenger)))
            .get(AssociatedDeviceViewModel.class);

    model
        .getAssociationState()
        .observe(
            this,
            state -> {
              switch (state) {
                case PENDING:
                  if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    showTurnOnBluetoothFragment();
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
                  runOnUiThread(() -> setIsProgressBarVisible(false));
                  break;
                case STARTING:
                case STARTED:
                  runOnUiThread(
                      () -> {
                        logd(TAG, "Association state is started; Show companion landing fragment.");
                        showCompanionLandingFragment();
                        setIsProgressBarVisible(true);
                      });
                  break;
              }
            });

    model.getBluetoothState().observe(this, this::handleBluetoothStateChange);
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
        .getAssociatedDevicesDetails()
        .observe(
            this,
            devicesDetails -> {
              boolean isServiceConnected = model.isServiceConnected().getValue();
              handleAssociatedDevicesDetailsAndConnectionChange(devicesDetails, isServiceConnected);
            });
    model
        .getRemovedDevice()
        .observe(
            this,
            device -> {
              if (device != null) {
                runOnUiThread(() -> showDeviceRemovedToast(device.getDeviceName()));
              }
            });
    model
        .isServiceConnected()
        .observe(
            this,
            isServiceConnected -> {
              logd(TAG, "Service connection status: " + isServiceConnected);
              List<AssociatedDeviceDetails> devicesDetails =
                  model.getAssociatedDevicesDetails().getValue();
              handleAssociatedDevicesDetailsAndConnectionChange(devicesDetails, isServiceConnected);
            });
  }

  private void handleBluetoothStateChange(int state) {
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (state == BluetoothAdapter.STATE_ON) {
      DialogFragment bluetoothDialog =
          (DialogFragment) fragmentManager.findFragmentByTag(TURN_ON_BLUETOOTH_DIALOG_TAG);
      if (bluetoothDialog != null) {
        logd(TAG, "Bluetooth dialog is shown when Bluetooth is on; dismissed.");
        bluetoothDialog.dismiss();
      }
      return;
    }

    // A warning dialog for Bluetooth should only be shown during association.
    if (fragmentManager.findFragmentByTag(DEVICE_DETAIL_FRAGMENT_TAG) != null
        && fragmentManager.findFragmentByTag(DEVICES_LIST_FRAGMENT_TAG) != null) {
      runOnUiThread(this::showTurnOnBluetoothDialog);
    }
  }

  private void handleAssociatedDevicesDetailsAndConnectionChange(
      List<AssociatedDeviceDetails> devicesDetails, boolean isServiceConnected) {
    if (!isServiceConnected) {
      logw(TAG, "Service not connected, ignore device details change.");
      showLoadingScreen();
      return;
    }
    // An empty list means that there are no more associated devices.
    if (devicesDetails.isEmpty()) {
      handleEmptyDeviceList();
      return;
    }
    maybeAskForPermissions();

    if (isPassengerEnabled) {
      showAssociatedDevicesList(devicesDetails);
    } else {
      // Otherwise, there will only be one associated device.
      showAssociatedDeviceDetails(devicesDetails.get(0));
    }
  }

  private void showAssociatedDeviceDetails(AssociatedDeviceDetails deviceDetails) {
    if (isStartedByRemoteFeature()) {
      // Features always expect activity result when they start AssociationActivity.
      setDeviceAndResultOkToReturn(deviceDetails.getAssociatedDevice());
      finish();
      return;
    }

    runOnUiThread(() -> showAssociatedDeviceDetailFragment(deviceDetails));
  }

  /** Shows a list of associated devices. The provided list should be non-empty. */
  private void showAssociatedDevicesList(List<AssociatedDeviceDetails> devicesDetails) {
    if (isStartedByRemoteFeature()) {
      setDeviceAndResultOkToReturn(findFirstDriverDevice(devicesDetails));
      finish();
      return;
    }

    // If the device details are showing, then the details fragment will handle any UI changes.
    if (isAssociatedDeviceDetailsShowing()) {
      return;
    }

    runOnUiThread(this::showAssociatedDevicesListFragment);
  }

  /**
   * Activity result and intent will be set properly if the activity is started by remote feature.
   */
  private boolean isStartedByRemoteFeature() {
    String action = getIntent().getAction();
    return RemoteFeature.ACTION_ASSOCIATION_SETTING.equals(action)
        || RemoteFeature.ACTION_ASSOCIATION_SETUP_WIZARD.equals(action);
  }

  @Nullable
  private AssociatedDevice findFirstDriverDevice(List<AssociatedDeviceDetails> devicesDetails) {
    for (AssociatedDeviceDetails deviceDetails : devicesDetails) {
      if (deviceDetails.belongsToDriver()) {
        return deviceDetails.getAssociatedDevice();
      }
    }
    return null;
  }

  private boolean isAssociatedDeviceDetailsShowing() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
      if (ASSOCIATED_DEVICE_DETAILS_BACKSTACK_NAME.equals(
          fragmentManager.getBackStackEntryAt(i).getName())) {
        return true;
      }
    }
    return false;
  }

  private void showDeviceRemovedToast(String deviceName) {
    Toast.makeText(
            this,
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
    setIsProgressBarVisible(true);
  }

  private void handleEmptyDeviceList() {
    Fragment fragment =
        getSupportFragmentManager().findFragmentByTag(COMPANION_LANDING_FRAGMENT_TAG);

    if (fragment != null) {
      logd(
          TAG,
          "Device list is empty, but landing fragment already showing. Nothing more to be done.");
      return;
    }
    logd(TAG, "No associated device, showing landing screen.");
    setIsProgressBarVisible(false);
    showCompanionLandingFragment();
  }

  private void showCompanionLandingFragment() {
    maybeClearDetailsFragmentFromBackstack();

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
    dismissButtons();
    fragment = CompanionLandingFragment.newInstance(isStartedForSuw);
    launchFragment(fragment, COMPANION_LANDING_FRAGMENT_TAG);
  }

  private void showCompanionQrCodeLandingFragment() {
    CompanionQrCodeLandingFragment fragment =
        (CompanionQrCodeLandingFragment)
            getSupportFragmentManager().findFragmentByTag(COMPANION_LANDING_FRAGMENT_TAG);
    if (fragment != null && fragment.isVisible()) {
      logd(TAG, "Attempted to show QR code, but fragment is visible already. Ignoring");
      return;
    }
    fragment =
        CompanionQrCodeLandingFragment.newInstance(isStartedForSuw, isStartedForSetupProfile);
    dismissButtons();
    launchFragment(fragment, COMPANION_LANDING_FRAGMENT_TAG);
    showSkipButton();
  }

  private void showTurnOnBluetoothFragment() {
    setIsProgressBarVisible(true);
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
    setIsProgressBarVisible(true);
    AssociationErrorFragment fragment = new AssociationErrorFragment();
    launchFragment(fragment, ASSOCIATION_ERROR_FRAGMENT_TAG);
  }

  private void showAssociatedDeviceDetailFragment(AssociatedDeviceDetails deviceDetails) {
    // If passenger mode is enabled, then the device list fragment is shown first and will take
    // care of adjusting the toolbar buttons.
    if (!isPassengerEnabled) {
      dismissButtons();
    }

    setIsProgressBarVisible(false);

    AssociatedDeviceDetailFragment fragment =
        AssociatedDeviceDetailFragment.newInstance(deviceDetails);

    // If passenger mode is enabled, the details should be pushed on top of the list of devices.
    // Otherwise, it will be the root view.
    if (isPassengerEnabled) {
      addFragment(fragment, DEVICE_DETAIL_FRAGMENT_TAG);
    } else {
      launchFragment(fragment, DEVICE_DETAIL_FRAGMENT_TAG);
    }

    showTurnOnBluetoothDialog();
  }

  private void showAssociatedDevicesListFragment() {
    showAssociateButton();
    setIsProgressBarVisible(false);

    AssociatedDevicesListFragment fragment = new AssociatedDevicesListFragment();
    fragment.setOnListItemClickListener(this::showAssociatedDeviceDetailFragment);

    launchFragment(fragment, DEVICES_LIST_FRAGMENT_TAG);
    showTurnOnBluetoothDialog();
  }

  private void showTurnOnBluetoothDialog() {
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      TurnOnBluetoothDialogFragment fragment = new TurnOnBluetoothDialogFragment();
      fragment.show(getSupportFragmentManager(), TURN_ON_BLUETOOTH_DIALOG_TAG);
    }
  }

  /** Checks for the details page being shown and clears it from the Fragment backstack. */
  private void maybeClearDetailsFragmentFromBackstack() {
    // The details page is only added to the backstack if passenger mode is enabled. Also, no need
    // to clear if the fragment isn't there.
    if (!isPassengerEnabled
        || getSupportFragmentManager().findFragmentByTag(DEVICE_DETAIL_FRAGMENT_TAG) == null) {
      return;
    }

    // If the details fragment is showing, then it will be on the backstack.
    FragmentManager fragmentManager = getSupportFragmentManager();
    for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
      fragmentManager.popBackStack();
    }
  }

  /** Displays the given {@code fragment} and replaces the current content. */
  private void launchFragment(Fragment fragment, String tag) {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, fragment, tag)
        .commit();
  }

  /** Displays the given {@code fragment} and adds it to the backstack. */
  private void addFragment(Fragment fragment, String tag) {
    getSupportFragmentManager()
        .beginTransaction()
        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
        .replace(R.id.fragment_container, fragment, tag)
        .addToBackStack(ASSOCIATED_DEVICE_DETAILS_BACKSTACK_NAME)
        .commit();
  }

  public void retryAssociation() {
    dismissButtons();
    setIsProgressBarVisible(true);
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(PAIRING_CODE_FRAGMENT_TAG);
    if (fragment != null) {
      getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }
    model.stopAssociation();
    startAssociation();
  }

  private void setDeviceAndResultOkToReturn(AssociatedDevice device) {
    Intent intent = new Intent();
    intent.putExtra(RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA, device);
    setResult(RESULT_OK, intent);
  }

  private static void maybeEnableStrictMode() {
    if (!Build.TYPE.equals("user")) {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().penaltyDialog().build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
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
