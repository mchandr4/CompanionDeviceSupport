package com.google.android.companiondevicesupport;

import android.app.Application;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.OobEligibleDevice;

/** Factory class for an {@link OobAssociatedDeviceViewModel}. */
public class OobAssociatedDeviceViewModelFactory extends ViewModelProvider.AndroidViewModelFactory {

  private final Application application;
  private final OobEligibleDevice oobEligibleDevice;

  public static OobAssociatedDeviceViewModelFactory getInstance(
      Application application, OobEligibleDevice oobEligibleDevice) {
    return new OobAssociatedDeviceViewModelFactory(application, oobEligibleDevice);
  }

  public OobAssociatedDeviceViewModelFactory(
      @NonNull Application application, OobEligibleDevice oobEligibleDevice) {
    super(application);
    this.application = application;
    this.oobEligibleDevice = oobEligibleDevice;
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    return modelClass.cast(new OobAssociatedDeviceViewModel(application, oobEligibleDevice));
  }
}
