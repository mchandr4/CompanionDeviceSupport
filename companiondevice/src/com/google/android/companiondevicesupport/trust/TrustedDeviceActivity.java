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

package com.google.android.companiondevicesupport.trust;

import static com.google.android.connecteddevice.api.RemoteFeature.ACTION_ASSOCIATION_SETTING;
import static com.google.android.connecteddevice.api.RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import androidx.lifecycle.ViewModelProvider;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.companiondevicesupport.R;
import com.google.android.companiondevicesupport.ui.Toolbar;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.trust.TrustedDeviceConstants;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel.EnrollmentState;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.concurrent.atomic.AtomicBoolean;

/** Activity for enrolling and viewing trusted devices. */
@SuppressWarnings("deprecation") // TODO(b/165802790) Remove startActivityForResult
public class TrustedDeviceActivity extends FragmentActivity {

  private static final String TAG = "TrustedDeviceActivity";

  private static final int ACTIVATE_TOKEN_REQUEST_CODE = 1;

  private static final int CREATE_LOCK_REQUEST_CODE = 2;

  private static final int RETRIEVE_ASSOCIATED_DEVICE_REQUEST_CODE = 3;

  private static final String ACTION_LOCK_SETTINGS = "android.car.settings.SCREEN_LOCK_ACTIVITY";

  private static final String DEVICE_DETAIL_FRAGMENT_TAG = "TrustedDeviceDetailFragmentTag";

  private static final String DEVICE_NOT_CONNECTED_DIALOG_TAG =
      "DeviceNotConnectedDialogFragmentTag";

  private static final String CREATE_PROFILE_LOCK_DIALOG_TAG = "CreateProfileLockDialogFragmentTag";

  private static final String UNLOCK_PROFILE_TO_FINISH_DIALOG_TAG =
      "UnlockProfileToFinishDialogFragmentTag";

  private static final String CREATE_PHONE_LOCK_DIALOG_TAG = "CreatePhoneLockDialogFragmentTag";

  private static final String ENROLLMENT_ERROR_DIALOG_TAG = "EnrollmentErrorDialogFragmentTag";

  /** {@code true} if a PIN/Pattern/Password has just been set as a screen lock. */
  private final AtomicBoolean isScreenLockNewlyCreated = new AtomicBoolean(false);

  private final AtomicBoolean isStartedForEnrollment = new AtomicBoolean(false);

  /**
   * {@code true} if this activity is relaunched for enrollment and the activity needs to be
   * finished after enrollment has completed.
   */
  private final AtomicBoolean wasRelaunched = new AtomicBoolean(false);

  private KeyguardManager keyguardManager;

  private Toolbar toolbar;

  private TrustedDeviceViewModel model;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.base_activity);
    observeViewModel();
    resumePreviousState(savedInstanceState);

    toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.trusted_device_feature_title);
    toolbar.showProgressBar();
    toolbar.setOnBackButtonClickListener(v -> finish());

    extractAssociatedDevice();

    isScreenLockNewlyCreated.set(false);
    isStartedForEnrollment.set(false);
    wasRelaunched.set(false);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
      case ACTIVATE_TOKEN_REQUEST_CODE:
        if (resultCode != RESULT_OK) {
          loge(TAG, "Lock screen was unsuccessful. Returned result code: " + resultCode + ".");
          model.finishEnrollment();
          return;
        }
        logd(TAG, "Credentials accepted. Waiting for TrustAgent to activate " + "token.");
        break;
      case CREATE_LOCK_REQUEST_CODE:
        if (!isDeviceSecure()) {
          loge(TAG, "Set up new lock unsuccessful. Returned result code: " + resultCode + ".");
          isScreenLockNewlyCreated.set(false);
          return;
        }
        isScreenLockNewlyCreated.set(true);
        model.processEnrollment();
        break;
      case RETRIEVE_ASSOCIATED_DEVICE_REQUEST_CODE:
        onAssociatedDeviceRetrieved(data);
        break;
      default:
        logw(TAG, "Unrecognized activity result. Request code: " + requestCode + ". Ignoring.");
        break;
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    wasRelaunched.set(true);
    if (isStartedForEnrollment(intent)) {
      model.processEnrollment();
    }
  }

  private void resumePreviousState(Bundle saveInstanceState) {
    if (saveInstanceState == null) {
      return;
    }
    CreateProfileLockDialogFragment createProfileLockDialogFragment =
        (CreateProfileLockDialogFragment)
            getSupportFragmentManager().findFragmentByTag(CREATE_PROFILE_LOCK_DIALOG_TAG);
    if (createProfileLockDialogFragment != null) {
      createProfileLockDialogFragment.setOnConfirmListener((d, w) -> createScreenLock());
    }

    UnlockProfileDialogFragment unlockProfileDialogFragment =
        (UnlockProfileDialogFragment)
            getSupportFragmentManager().findFragmentByTag(UNLOCK_PROFILE_TO_FINISH_DIALOG_TAG);
    if (unlockProfileDialogFragment != null) {
      unlockProfileDialogFragment.setOnConfirmListener((d, w) -> validateCredentials());
    }
  }

  private void observeViewModel() {
    model = new ViewModelProvider(this).get(TrustedDeviceViewModel.class);
    model.getAssociatedDevice().observe(this, this::onAssociatedDeviceEnrolled);
    model.getEnrollmentError().observe(this, this::onEnrollmentError);

    model.getEnrollmentState().observe(this, this::onEnrollmentStateChanged);

    model
        .getEnabledDevice()
        .observe(this, device -> runOnUiThread(() -> showEnrollmentSuccessToast(device)));
    model
        .isCurrentAssociatedDeviceRemoved()
        .observe(
            this,
            isFinished -> {
              if (isFinished) {
                finish();
              }
            });
  }

  private void onAssociatedDeviceEnrolled(AssociatedDevice device) {
    if (device == null) {
      return;
    }
    showTrustedDeviceDetailFragment(device);
    Intent incomingIntent = getIntent();
    if (isStartedForEnrollment(incomingIntent)) {
      model.processEnrollment();
    }
  }

  private void onEnrollmentError(Integer error) {
    if (error != null) {
      runOnUiThread(() -> showEnrollmentErrorDialogFragment(error));
    }
  }

  private void onEnrollmentStateChanged(EnrollmentState state) {
    logd(TAG, "new enrollment state: " + state.name());
    switch (state) {
      case WAITING_FOR_PASSWORD_SETUP:
        runOnUiThread(this::promptToCreatePassword);
        break;
      case NO_CONNECTION:
        runOnUiThread(this::showDeviceNotConnectedDialog);
        break;
      case CREDENTIAL_PENDING:
        validateCredentials();
        break;
      case FINISHED:
        model.resetEnrollmentState();
        finishEnrollment();
        break;
      default:
        break;
    }
  }

  private void extractAssociatedDevice() {
    Intent intent = getIntent();
    String action = intent.getAction();
    if (!TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING.equals(action)) {
      retrieveAssociatedDevice();
      return;
    }
    AssociatedDevice device = intent.getParcelableExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA);
    if (device == null) {
      logd(TAG, "Failed to extract associated device intent, start retrieving associated device.");
      retrieveAssociatedDevice();
      return;
    }
    model.setAssociatedDevice(device);
  }

  private void validateCredentials() {
    KeyguardManager keyguardManager = getKeyguardManager();
    if (keyguardManager == null) {
      logd(TAG, "KeyguardManager was null. Aborting.");
      return;
    }
    if (isScreenLockNewlyCreated.get()) {
      showUnlockProfileDialogFragment();
      return;
    }
    @SuppressWarnings("deprecation") // Car does not support Biometric lock as of now.
    Intent confirmIntent =
        keyguardManager.createConfirmDeviceCredentialIntent(
            "PLACEHOLDER PROMPT TITLE", "PLACEHOLDER PROMPT MESSAGE");
    if (confirmIntent == null) {
      loge(TAG, "User either has no lock screen, or a token is already registered.");
      return;
    }
    logd(TAG, "Prompting user to validate credentials.");
    startActivityForResult(confirmIntent, ACTIVATE_TOKEN_REQUEST_CODE);
  }

  private static boolean isStartedForEnrollment(Intent intent) {
    return intent != null
        && intent.getBooleanExtra(TrustedDeviceConstants.INTENT_EXTRA_ENROLL_NEW_TOKEN, false);
  }

  private void finishEnrollment() {
    if (!wasRelaunched.get()) {
      // If the activity is not relaunched for enrollment, it needs to be finished to make the
      // foreground return to the previous screen.
      finish();
    }
  }

  private void promptToCreatePassword() {
    if (isDeviceSecure()) {
      return;
    }
    CreateProfileLockDialogFragment fragment =
        CreateProfileLockDialogFragment.newInstance((d, w) -> createScreenLock());
    fragment.show(getSupportFragmentManager(), CREATE_PROFILE_LOCK_DIALOG_TAG);
  }

  private void createScreenLock() {
    if (isDeviceSecure()) {
      return;
    }
    logd(TAG, "User has not set a lock screen. Redirecting to set up.");
    Intent intent = new Intent(ACTION_LOCK_SETTINGS);
    startActivityForResult(intent, CREATE_LOCK_REQUEST_CODE);
  }

  private boolean isDeviceSecure() {
    KeyguardManager keyguardManager = getKeyguardManager();
    if (keyguardManager == null) {
      return false;
    }
    return keyguardManager.isDeviceSecure();
  }

  private void retrieveAssociatedDevice() {
    Intent intent = new Intent(ACTION_ASSOCIATION_SETTING);
    startActivityForResult(intent, RETRIEVE_ASSOCIATED_DEVICE_REQUEST_CODE);
  }

  private void showTrustedDeviceDetailFragment(AssociatedDevice device) {
    toolbar.hideProgressBar();
    TrustedDeviceDetailFragment fragment = TrustedDeviceDetailFragment.newInstance(device);
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, fragment, DEVICE_DETAIL_FRAGMENT_TAG)
        .commit();
  }

  private void showUnlockProfileDialogFragment() {
    isScreenLockNewlyCreated.set(false);
    UnlockProfileDialogFragment fragment =
        UnlockProfileDialogFragment.newInstance((d, w) -> validateCredentials());
    fragment.show(getSupportFragmentManager(), UNLOCK_PROFILE_TO_FINISH_DIALOG_TAG);
  }

  private void showDeviceNotConnectedDialog() {
    DeviceNotConnectedDialogFragment fragment = new DeviceNotConnectedDialogFragment();
    fragment.show(getSupportFragmentManager(), DEVICE_NOT_CONNECTED_DIALOG_TAG);
  }

  private void showEnrollmentSuccessToast(TrustedDevice device) {
    if (device == null) {
      return;
    }
    AssociatedDevice addedDevice = model.getAssociatedDevice().getValue();
    if (addedDevice == null) {
      loge(TAG, "No associated device retrieved when a trusted device has been added.");
      return;
    }
    if (!addedDevice.getDeviceId().equals(device.getDeviceId())) {
      loge(TAG, "Id of the enrolled trusted device doesn't match id of the current device");
      return;
    }
    String message =
        getString(R.string.trusted_device_enrollment_success_message, addedDevice.getDeviceName());
    Spanned styledMessage = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);
    runOnUiThread(
        () -> Toast.makeText(getApplicationContext(), styledMessage, Toast.LENGTH_SHORT).show());
  }

  private void showEnrollmentErrorDialogFragment(int error) {
    switch (error) {
      case TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_DEVICE_NOT_SECURED:
        CreatePhoneLockDialogFragment createPhoneLockDialogFragment =
            new CreatePhoneLockDialogFragment();
        createPhoneLockDialogFragment.show(
            getSupportFragmentManager(), CREATE_PHONE_LOCK_DIALOG_TAG);
        break;
      case TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_MESSAGE_TYPE_UNKNOWN:
      case TrustedDeviceConstants.TRUSTED_DEVICE_ERROR_UNKNOWN:
        EnrollmentErrorDialogFragment enrollmentErrorDialogFragment =
            new EnrollmentErrorDialogFragment();
        enrollmentErrorDialogFragment.show(
            getSupportFragmentManager(), ENROLLMENT_ERROR_DIALOG_TAG);
        break;
      default:
        loge(TAG, "Encountered unexpected error: " + error + ".");
    }
  }

  @Nullable
  private KeyguardManager getKeyguardManager() {
    if (keyguardManager == null) {
      keyguardManager = getSystemService(KeyguardManager.class);
    }
    if (keyguardManager == null) {
      loge(TAG, "Unable to get KeyguardManager.");
    }
    return keyguardManager;
  }

  private void onAssociatedDeviceRetrieved(Intent data) {
    if (data == null) {
      loge(TAG, "Intent is null. Failed to extract associated device from intent.");
      finish();
      return;
    }
    AssociatedDevice device = data.getParcelableExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA);
    if (device == null) {
      loge(TAG, "Associated device extracted from intent is null.");
      finish();
      return;
    }
    model.setAssociatedDevice(device);
    logd(TAG, "on associated device retrieved from association activity: " + device);
  }

  /** Dialog Fragment to notify that the device is not actively connected. */
  public static class DeviceNotConnectedDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.device_not_connected_dialog_title))
          .setMessage(getString(R.string.device_not_connected_dialog_message))
          .setNegativeButton(getString(R.string.ok), null)
          .setCancelable(true)
          .create();
    }
  }

  /** Dialog Fragment to notify that a profile lock is needed to continue enrollment. */
  public static class CreateProfileLockDialogFragment extends DialogFragment {
    private DialogInterface.OnClickListener onConfirmListener;

    static CreateProfileLockDialogFragment newInstance(DialogInterface.OnClickListener listener) {
      CreateProfileLockDialogFragment fragment = new CreateProfileLockDialogFragment();
      fragment.setOnConfirmListener(listener);
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.create_profile_lock_dialog_title))
          .setMessage(getString(R.string.create_profile_lock_dialog_message))
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.continue_button), onConfirmListener)
          .setCancelable(true)
          .create();
    }

    void setOnConfirmListener(DialogInterface.OnClickListener onConfirmListener) {
      this.onConfirmListener = onConfirmListener;
    }
  }

  /** Dialog Fragment to notify that the user needs to unlock again to finish enrollment. */
  public static class UnlockProfileDialogFragment extends DialogFragment {
    private DialogInterface.OnClickListener onConfirmListener;

    static UnlockProfileDialogFragment newInstance(DialogInterface.OnClickListener listener) {
      UnlockProfileDialogFragment fragment = new UnlockProfileDialogFragment();
      fragment.setOnConfirmListener(listener);
      return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.unlock_profile_to_finish_title))
          .setMessage(getString(R.string.unlock_profile_to_finish_message))
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.continue_button), onConfirmListener)
          .setCancelable(true)
          .create();
    }

    void setOnConfirmListener(DialogInterface.OnClickListener onConfirmListener) {
      this.onConfirmListener = onConfirmListener;
    }
  }

  /** Dialog Fragment to notify that the user needs to set up phone unlock before enrollment. */
  public static class CreatePhoneLockDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.create_phone_lock_dialog_title))
          .setMessage(getString(R.string.create_phone_lock_dialog_message))
          .setPositiveButton(getString(R.string.ok), null)
          .setCancelable(true)
          .create();
    }
  }

  /** Dialog Fragment to notify error during enrollment. */
  public static class EnrollmentErrorDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return new AlertDialog.Builder(getActivity())
          .setTitle(getString(R.string.trusted_device_enrollment_error_dialog_title))
          .setMessage(getString(R.string.trusted_device_enrollment_error_dialog_message))
          .setPositiveButton(getString(R.string.ok), null)
          .setCancelable(true)
          .create();
    }
  }
}
