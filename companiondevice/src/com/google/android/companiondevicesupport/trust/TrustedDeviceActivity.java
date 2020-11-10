package com.google.android.companiondevicesupport.trust;

import static com.google.android.connecteddevice.api.RemoteFeature.ACTION_ASSOCIATION_SETTING;
import static com.google.android.connecteddevice.api.RemoteFeature.ASSOCIATED_DEVICE_DATA_NAME_EXTRA;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import androidx.lifecycle.ViewModelProviders;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.companiondevicesupport.R;
import com.google.android.companiondevicesupport.ui.Toolbar;
import com.google.android.connecteddevice.api.IDeviceAssociationCallback;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.trust.TrustedDeviceConstants;
import com.google.android.connecteddevice.trust.TrustedDeviceManagerService;
import com.google.android.connecteddevice.trust.TrustedDeviceViewModel;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback;
import com.google.android.connecteddevice.trust.api.ITrustedDeviceManager;
import com.google.android.connecteddevice.trust.api.TrustedDevice;
import java.util.List;
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

  private final AtomicBoolean hasPendingCredential = new AtomicBoolean(false);

  /**
   * {@code true} if this activity is relaunched for enrollment and the activity needs to be
   * finished after enrollment has completed.
   */
  private final AtomicBoolean wasRelaunched = new AtomicBoolean(false);

  private KeyguardManager keyguardManager;

  private ITrustedDeviceManager trustedDeviceManager;

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

    isScreenLockNewlyCreated.set(false);
    isStartedForEnrollment.set(false);
    hasPendingCredential.set(false);
    wasRelaunched.set(false);
    Intent intent = new Intent(this, TrustedDeviceManagerService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
      case ACTIVATE_TOKEN_REQUEST_CODE:
        if (resultCode != RESULT_OK) {
          loge(TAG, "Lock screen was unsuccessful. Returned result code: " + resultCode + ".");
          finishEnrollment();
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

        if (hasPendingCredential.get()) {
          showUnlockProfileDialogFragment();
        }
        break;
      case RETRIEVE_ASSOCIATED_DEVICE_REQUEST_CODE:
        AssociatedDevice device = data.getParcelableExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA);
        if (device == null) {
          loge(TAG, "No valid associated device.");
          return;
        }
        model.setAssociatedDevice(device);
        Intent incomingIntent = getIntent();
        if (isStartedForEnrollment(incomingIntent)) {
          processEnrollment();
          return;
        }
        showTrustedDeviceDetailFragment(device);
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
      processEnrollment();
    }
  }

  @Override
  protected void onDestroy() {
    try {
      unregisterCallbacks();
    } catch (RemoteException e) {
      loge(TAG, "Error while disconnecting from service.", e);
    }
    unbindService(serviceConnection);
    super.onDestroy();
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
    model = ViewModelProviders.of(this).get(TrustedDeviceViewModel.class);
    model
        .getDeviceToDisable()
        .observe(
            this,
            trustedDevice -> {
              if (trustedDevice == null) {
                return;
              }
              model.setDeviceToDisable(null);
              if (trustedDeviceManager == null) {
                loge(TAG, "Failed to remove trusted device. service not connected.");
                return;
              }
              try {
                logd(TAG, "calling removeTrustedDevice");
                trustedDeviceManager.removeTrustedDevice(trustedDevice);
              } catch (RemoteException e) {
                loge(TAG, "Failed to remove trusted device.", e);
              }
            });
    model
        .getDeviceToEnable()
        .observe(
            this,
            associatedDevice -> {
              if (associatedDevice == null) {
                return;
              }
              model.setDeviceToEnable(null);
              attemptInitiatingEnrollment(associatedDevice);
            });
  }

  private boolean hasAssociatedDevice() {
    Intent intent = getIntent();
    String action = intent.getAction();
    if (!TrustedDeviceConstants.INTENT_ACTION_TRUSTED_DEVICE_SETTING.equals(action)) {
      return false;
    }
    AssociatedDevice device = intent.getParcelableExtra(ASSOCIATED_DEVICE_DATA_NAME_EXTRA);
    if (device == null) {
      loge(TAG, "No valid associated device.");
      return false;
    }
    model.setAssociatedDevice(device);
    showTrustedDeviceDetailFragment(device);
    return true;
  }

  private void attemptInitiatingEnrollment(AssociatedDevice device) {
    if (!isCompanionDeviceConnected(device.getDeviceId())) {
      DeviceNotConnectedDialogFragment fragment = new DeviceNotConnectedDialogFragment();
      fragment.show(getSupportFragmentManager(), DEVICE_NOT_CONNECTED_DIALOG_TAG);
      return;
    }
    try {
      trustedDeviceManager.initiateEnrollment(device.getDeviceId());
    } catch (RemoteException e) {
      loge(TAG, "Failed to initiate enrollment. ", e);
    }
  }

  private boolean isCompanionDeviceConnected(String deviceId) {
    if (trustedDeviceManager == null) {
      loge(
          TAG,
          "Failed to check connection status for device: " + deviceId + "Service not connected.");
      return false;
    }
    List<ConnectedDevice> devices = null;
    try {
      devices = trustedDeviceManager.getActiveUserConnectedDevices();
    } catch (RemoteException e) {
      loge(TAG, "Failed to check connection status for device: " + deviceId, e);
      return false;
    }
    if (devices == null || devices.isEmpty()) {
      return false;
    }
    for (ConnectedDevice device : devices) {
      if (device.getDeviceId().equals(deviceId)) {
        return true;
      }
    }
    return false;
  }

  private void validateCredentials() {
    logd(TAG, "Validating credentials to activate token.");
    KeyguardManager keyguardManager = getKeyguardManager();
    if (keyguardManager == null) {
      logd(TAG, "KeyguardManager was null. Aborting.");
      return;
    }
    if (!isStartedForEnrollment.get()) {
      hasPendingCredential.set(true);
      logd(TAG, "Activity not started for enrollment. Credentials are pending.");
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
    hasPendingCredential.set(false);
    isStartedForEnrollment.set(false);
    logd(TAG, "Prompting user to validate credentials.");
    startActivityForResult(confirmIntent, ACTIVATE_TOKEN_REQUEST_CODE);
  }

  private void processEnrollment() {
    isStartedForEnrollment.set(true);
    if (hasPendingCredential.get()) {
      validateCredentials();
      return;
    }
    maybePromptToCreatePassword();
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

  private void maybePromptToCreatePassword() {
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
    isScreenLockNewlyCreated.set(true);
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

  private void showEnrollmentSuccessToast(TrustedDevice device) {
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

  private void registerCallbacks() throws RemoteException {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to register callbacks.");
      return;
    }
    trustedDeviceManager.registerTrustedDeviceEnrollmentCallback(trustedDeviceEnrollmentCallback);
    trustedDeviceManager.registerTrustedDeviceCallback(trustedDeviceCallback);
    trustedDeviceManager.registerAssociatedDeviceCallback(deviceAssociationCallback);
  }

  private void unregisterCallbacks() throws RemoteException {
    if (trustedDeviceManager == null) {
      loge(TAG, "Server not connected when attempting to unregister callbacks.");
      return;
    }
    trustedDeviceManager.unregisterTrustedDeviceEnrollmentCallback(
        trustedDeviceEnrollmentCallback);
    trustedDeviceManager.unregisterTrustedDeviceCallback(trustedDeviceCallback);
    trustedDeviceManager.unregisterAssociatedDeviceCallback(deviceAssociationCallback);
  }

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          trustedDeviceManager = ITrustedDeviceManager.Stub.asInterface(service);
          try {
            registerCallbacks();
          } catch (RemoteException e) {
            loge(TAG, "Error while connecting to service.");
            return;
          }

          model.updateTrustedDevices(trustedDeviceManager);

          logd(TAG, "Successfully connected to TrustedDeviceManager.");

          if (!hasAssociatedDevice()) {
            retrieveAssociatedDevice();
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
      };

  private final ITrustedDeviceCallback trustedDeviceCallback =
      new ITrustedDeviceCallback.Stub() {
        @Override
        public void onTrustedDeviceAdded(TrustedDevice device) {
          logd(TAG, "Added trusted device: " + device.getDeviceId() + ".");
          model.setEnabledDevice(device);
          showEnrollmentSuccessToast(device);
          finishEnrollment();
        }

        @Override
        public void onTrustedDeviceRemoved(TrustedDevice device) {
          logd(TAG, "Removed trusted device: " + device.getDeviceId() + ".");
          model.setDisabledDevice(device);
        }
      };

  private final IDeviceAssociationCallback deviceAssociationCallback =
      new IDeviceAssociationCallback.Stub() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {}

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          AssociatedDevice currentDevice = model.getAssociatedDevice().getValue();
          if (device.equals(currentDevice)) {
            finish();
          }
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          if (device != null) {
            model.setAssociatedDevice(device);
          }
        }
      };

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

  private final ITrustedDeviceEnrollmentCallback trustedDeviceEnrollmentCallback =
      new ITrustedDeviceEnrollmentCallback.Stub() {

        @Override
        public void onValidateCredentialsRequest() {
          validateCredentials();
        }

        @Override
        public void onTrustedDeviceEnrollmentError(int error) {
          loge(TAG, "Failed to enroll trusted device, encountered error: " + error + ".");
          showEnrollmentErrorDialogFragment(error);
        }
      };

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
