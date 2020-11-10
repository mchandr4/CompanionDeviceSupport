package com.google.android.companiondevicesupport;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Activity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.widget.Toast;
import com.google.android.companiondevicesupport.ui.Toolbar;
import com.google.android.connecteddevice.model.OobEligibleDevice;
import com.google.common.base.Strings;

/** Activity for out of band association. */
public class OobAssociationActivity extends FragmentActivity {
  private static final String TAG = "OobAssociationActivity";
  private static final String OOB_ADD_DEVICE_FRAGMENT_TAG = "OobAddAssociatedDeviceFragment";

  private static final String EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS";

  private Toolbar toolbar;
  private OobAssociatedDeviceViewModel model;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.base_activity);

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

    toolbar = findViewById(R.id.toolbar);
    toolbar.setOnBackButtonClickListener(v -> finish());

    getSupportFragmentManager()
        .beginTransaction()
        .replace(
            R.id.fragment_container,
            new OobAddAssociatedDeviceFragment(),
            OOB_ADD_DEVICE_FRAGMENT_TAG)
        .commit();
    toolbar.showProgressBar();
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
    toolbar.hideProgressBar();
    super.onDestroy();
  }

  private void observeViewModel(String deviceAddress) {
    model =
        new ViewModelProvider(
                ViewModelStore::new,
                OobAssociatedDeviceViewModelFactory.getInstance(
                    getApplication(),
                    new OobEligibleDevice(deviceAddress, OobEligibleDevice.OOB_TYPE_BLUETOOTH)))
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
