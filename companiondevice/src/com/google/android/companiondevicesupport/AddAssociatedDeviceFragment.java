package com.google.android.companiondevicesupport;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;

/** Fragment that notifies the user to select the car. */
public class AddAssociatedDeviceFragment extends Fragment {
  private static final String DEVICE_NAME_KEY = "deviceName";
  private static final String TAG = "AddAssociatedDeviceFragment";

  static AddAssociatedDeviceFragment newInstance(@NonNull String deviceName) {
    Bundle bundle = new Bundle();
    bundle.putString(DEVICE_NAME_KEY, deviceName);
    AddAssociatedDeviceFragment fragment = new AddAssociatedDeviceFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.add_associated_device_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Bundle bundle = getArguments();
    String deviceName = bundle.getString(DEVICE_NAME_KEY);
    TextView selectTextView = view.findViewById(R.id.associated_device_select_device);
    setDeviceNameForAssociation(selectTextView, deviceName);
  }

  private void setDeviceNameForAssociation(TextView textView, String deviceName) {
    if (textView == null) {
      loge(TAG, "No valid TextView to show device name.");
      return;
    }
    String selectText = getString(R.string.associated_device_select_device, deviceName);
    Spanned styledSelectText = Html.fromHtml(selectText, Html.FROM_HTML_MODE_LEGACY);
    textView.setText(styledSelectText);
  }
}
