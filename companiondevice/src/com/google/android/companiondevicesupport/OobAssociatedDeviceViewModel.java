package com.google.android.companiondevicesupport;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.os.RemoteException;
import com.google.android.connecteddevice.api.IAssociatedDeviceManager;
import com.google.android.connecteddevice.model.OobEligibleDevice;

/** {@link ViewModel} for out of band association. */
public class OobAssociatedDeviceViewModel extends AssociatedDeviceViewModel {
  private static final String TAG = "OobAssociatedDeviceViewModel";

  private final OobEligibleDevice oobEligibleDevice;

  public OobAssociatedDeviceViewModel(
      Application application, OobEligibleDevice oobEligibleDevice) {
    super(application);

    this.oobEligibleDevice = oobEligibleDevice;
  }

  @Override
  protected void startAssociation() {
    IAssociatedDeviceManager manager = getAssociatedDeviceManager();
    if (manager == null) {
      return;
    }
    getAssociationState().postValue(AssociationState.PENDING);
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
      return;
    }
    try {
      logd(TAG, "Starting association with " + oobEligibleDevice.getDeviceAddress());
      manager.startOobAssociation(oobEligibleDevice);

    } catch (RemoteException e) {
      loge(TAG, "Failed to start association .", e);
      getAssociationState().postValue(AssociationState.ERROR);
    }
    getAssociationState().postValue(AssociationState.STARTING);
  }
}
