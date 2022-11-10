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
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
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
import android.view.View;
import android.view.ViewStub;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;
import com.google.android.connecteddevice.api.RemoteFeature;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.TransportProtocols;
import com.google.android.connecteddevice.ui.AssociatedDeviceDetails;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModel;
import com.google.android.connecteddevice.ui.AssociatedDeviceViewModelFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Activity class for association */
public class AssociationActivity extends FragmentActivity {

  private static final String TAG = "CompanionAssociationActivity";
  private static final String COMPANION_LANDING_FRAGMENT_TAG = "CompanionLandingFragment";
  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "AssociatedDeviceDetailFragment";
  private static final String DEVICES_LIST_FRAGMENT_TAG = "AssociatedDevicesListFragment";
  private static final String PAIRING_CODE_FRAGMENT_TAG = "ConfirmPairingCodeFragment";
  private static final String TURN_ON_BLUETOOTH_FRAGMENT_TAG = "TurnOnBluetoothFragment";
  private static final String ASSOCIATION_ERROR_FRAGMENT_TAG = "AssociationErrorFragment";
  private static final String TURN_ON_BLUETOOTH_DIALOG_TAG = "TurnOnBluetoothDialog";
  private static final String COMPANION_NOT_AVAILABLE_DIALOG_TAG = "CompanionNotAvailableDialog";
  private static final String EXTRA_AUTH_IS_SETUP_WIZARD = "is_setup_wizard";
  private static final String EXTRA_AUTH_IS_SETUP_PROFILE = "is_setup_profile_association";
  private static final String EXTRA_USE_IMMERSIVE_MODE = "useImmersiveMode";
  private static final String EXTRA_HIDE_SKIP_BUTTON = "hide_skip_button";
  private static final String ASSOCIATED_DEVICE_DETAILS_BACKSTACK_NAME =
      "AssociatedDeviceDetailsBackstack";

  private static final String PROFILE_SWITCH_PACKAGE_NAME = "com.android.car.settings";
  private static final String PROFILE_SWITCH_CLASS_NAME_R =
      "com.android.car.settings.users.UserSwitcherActivity";
  private static final String PROFILE_SWITCH_CLASS_NAME =
      "com.android.car.settings.profiles.ProfileSwitcherActivity";

  private final List<String> requiredPermissions = new ArrayList<>();

  private ToolbarController toolbar;
  private CarSetupWizardCompatLayout carSetupWizardLayout;
  private AssociatedDeviceViewModel model;

  private boolean isPassengerEnabled = false;
  private boolean isStartedForSuw = false;
  private boolean isStartedForSetupProfile = false;
  private boolean isImmersive = false;
  private boolean hideSkipButton = false;
  private boolean pendingAssociationAfterPerms = false;
  private ActivityResultLauncher<String[]> requestPermissionLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // TODO(b/228328725): Remove strict mode change when the violation is resolved.
    maybeEnableStrictMode();
    resolveIntent();

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
    ((AssociatedDevicesListFragment) fragment).setOnListItemClickListener(
        this::showAssociatedDeviceDetailFragment);
  }

  @Override
  protected void onStart() {
    super.onStart();
    handleImmersive();
    UserManager userManager = getSystemService(UserManager.class);
    if (userManager.isGuestUser()) {
      showCompanionNotAvailableDialog();
    }
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

  private void resolveIntent() {
    Intent intent = getIntent();
    if (intent == null) {
      return;
    }
    isStartedForSuw = intent.getBooleanExtra(EXTRA_AUTH_IS_SETUP_WIZARD, /* defaultValue= */ false);
    isStartedForSetupProfile =
        intent.getBooleanExtra(EXTRA_AUTH_IS_SETUP_PROFILE, /* defaultValue= */ false);
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
      ViewStub content = carSetupWizardLayout.getContentViewStub();
      if (content != null) {
        content.setLayoutResource(R.layout.suw_splitnav_fragment_container);
        content.inflate();
      } else {
        loge(TAG, "Couldn't find ViewStub in suw_companion_base_activity.");
      }
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
                        logd(TAG, "Association state is started; Show companion landing fragment.");
                        showCompanionLandingFragment();
                        showProgressBar();
                      });
                  break;
              }
            });

    model
        .getBluetoothState()
        .observe(this, this::handleBluetoothStateChange);
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

    // A warning dialog for Bluetooth should only be shown during association.
    if (state != BluetoothAdapter.STATE_ON
        && fragmentManager.findFragmentByTag(DEVICE_DETAIL_FRAGMENT_TAG) != null
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
    if (isStartedByFeature()) {
      // Features always expect activity result when they start AssociationActivity.
      setDeviceToReturn(deviceDetails.getAssociatedDevice());
      finish();
      return;
    }

    runOnUiThread(() -> showAssociatedDeviceDetailFragment(deviceDetails));
  }

  /** Shows a list of associated devices. The provided list should be non-empty. */
  private void showAssociatedDevicesList(List<AssociatedDeviceDetails> devicesDetails) {
    if (isStartedByFeature()) {
      setDeviceToReturn(findFirstDriverDevice(devicesDetails));
      finish();
      return;
    }

    // If the device details are showing, then the details fragment will handle any UI changes.
    if (isAssociatedDeviceDetailsShowing()) {
      return;
    }

    runOnUiThread(this::showAssociatedDevicesListFragment);
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

  private void handleEmptyDeviceList() {
    Fragment fragment =
        getSupportFragmentManager().findFragmentByTag(COMPANION_LANDING_FRAGMENT_TAG);

    if (fragment != null) {
      logd(TAG,
          "Device list is empty, but landing fragment already showing. Nothing more to be done.");
      return;
    }
    logd(TAG, "No associated device, showing landing screen.");
    hideProgressBar();
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

  private void showAssociatedDeviceDetailFragment(AssociatedDeviceDetails deviceDetails) {
    // If passenger mode is enabled, then the device list fragment is shown first and will take
    // care of adjusting the toolbar buttons.
    if (!isPassengerEnabled) {
      dismissButtons();
    }

    hideProgressBar();

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
    hideProgressBar();

    AssociatedDevicesListFragment fragment = new AssociatedDevicesListFragment();
    fragment.setOnListItemClickListener(this::showAssociatedDeviceDetailFragment);

    launchFragment(fragment, DEVICES_LIST_FRAGMENT_TAG);
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
      logd(TAG, "Dismissing SUW toolbar buttons.");
      carSetupWizardLayout.setPrimaryToolbarButtonVisible(false);
      carSetupWizardLayout.setSecondaryToolbarButtonVisible(false);
      return;
    }
    toolbar.setMenuItems(null);
  }

  private void showSkipButton() {
    if (!isStartedForSuw || hideSkipButton) {
      logw(TAG, "Not in SUW or hideSkipButton is true; Do not show skip button.");
      return;
    }
    logd(TAG, "Show skip button on SUW page.");
    carSetupWizardLayout.setPrimaryToolbarButtonFlat(true);
    carSetupWizardLayout.setPrimaryToolbarButtonText(getString(R.string.skip));
    carSetupWizardLayout.setPrimaryToolbarButtonVisible(true);
    carSetupWizardLayout.setPrimaryToolbarButtonListener(
        v -> {
          setResult(RESULT_SKIP);
          finish();
        });
  }

  private void showAssociateButton() {
    if (isStartedForSuw) {
      return;
    }

    MenuItem associationButton =
        MenuItem.builder(this)
            .setTitle(R.string.add_associated_device_button)
            .setOnClickListener(
                v -> {
                  dismissButtons();
                  startAssociation();
                })
            .build();

    toolbar.setMenuItems(Arrays.asList(associationButton));
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

  private void showCompanionNotAvailableDialog() {
    CompanionNotAvailableDialogFragment fragment = new CompanionNotAvailableDialogFragment();
    fragment.show(getSupportFragmentManager(), COMPANION_NOT_AVAILABLE_DIALOG_TAG);
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
        .setCustomAnimations(
            R.anim.fade_in,
            R.anim.fade_out,
            R.anim.fade_in,
            R.anim.fade_out
        )
        .replace(R.id.fragment_container, fragment, tag)
        .addToBackStack(ASSOCIATED_DEVICE_DETAILS_BACKSTACK_NAME)
        .commit();
  }

  public void retryAssociation() {
    dismissButtons();
    showProgressBar();
    Fragment fragment = getSupportFragmentManager().findFragmentByTag(PAIRING_CODE_FRAGMENT_TAG);
    if (fragment != null) {
      getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }
    model.stopAssociation();
    startAssociation();
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

  /** Dialog fragment to notify CompanionDevice is not available to guest user. */
  public static class CompanionNotAvailableDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      @StringRes
      int messageResId =
          VERSION.SDK_INT <= VERSION_CODES.R
              ? R.string.companion_not_available_dialog_switch_user_message
              : R.string.companion_not_available_dialog_message;
      AlertDialog.Builder builder =
          new AlertDialog.Builder(getActivity())
              .setTitle(getString(R.string.companion_not_available_dialog_title))
              .setMessage(getString(messageResId))
              .setNegativeButton(getString(R.string.ok), (d, w) -> getActivity().finish())
              .setCancelable(false);
      addChangeProfileButton(builder);
      Dialog dialog = builder.create();
      dialog.setCanceledOnTouchOutside(/* cancel= */ false);
      return dialog;
    }

    private void addChangeProfileButton(AlertDialog.Builder builder) {
      if (VERSION.SDK_INT <= VERSION_CODES.Q) {
        return;
      }
      String profileSwitcherClassName =
          VERSION.SDK_INT >= VERSION_CODES.S
              ? PROFILE_SWITCH_CLASS_NAME
              : PROFILE_SWITCH_CLASS_NAME_R;
      builder.setPositiveButton(
          getString(R.string.change_profile),
          (d, w) -> {
            Intent intent = new Intent();
            intent.setComponent(
                new ComponentName(PROFILE_SWITCH_PACKAGE_NAME, profileSwitcherClassName));
            getActivity().startActivity(intent);
          });
    }
  }
}
