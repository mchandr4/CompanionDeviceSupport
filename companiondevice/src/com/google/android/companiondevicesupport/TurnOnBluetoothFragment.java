package com.google.android.companiondevicesupport;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Fragment that displays when Bluetooth is off. */
public class TurnOnBluetoothFragment extends Fragment {

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.turn_on_bluetooth_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    view.findViewById(R.id.turn_on_button)
        .setOnClickListener(
            button -> {
              // Disable for any future clicks so the user cannot repeatedly click this button and
              // possibly cause UI errors.
              button.setEnabled(false);
              BluetoothAdapter.getDefaultAdapter().enable();
            });
  }
}
