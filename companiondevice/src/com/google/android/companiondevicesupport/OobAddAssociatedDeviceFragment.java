package com.google.android.companiondevicesupport;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Fragment that shows an out of band associated device. */
public class OobAddAssociatedDeviceFragment extends Fragment {
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.oob_add_associated_device_fragment, container, false);
  }
}
