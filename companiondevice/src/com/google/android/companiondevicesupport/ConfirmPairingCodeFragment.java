package com.google.android.companiondevicesupport;

import static com.google.android.connecteddevice.util.SafeLog.loge;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;

/** Fragment that displays the pairing code. */
public class ConfirmPairingCodeFragment extends Fragment {
  private static final String PAIRING_CODE_KEY = "pairingCodeKey";
  private static final String TAG = "ConfirmPairingCodeFragment";

  static ConfirmPairingCodeFragment newInstance(@NonNull String pairingCode) {
    Bundle bundle = new Bundle();
    bundle.putString(PAIRING_CODE_KEY, pairingCode);
    ConfirmPairingCodeFragment fragment = new ConfirmPairingCodeFragment();
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.confirm_pairing_code_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    Bundle bundle = getArguments();
    String pairingCode = bundle.getString(PAIRING_CODE_KEY);
    TextView pairingCodeTextView = view.findViewById(R.id.pairing_code);
    setPairingCode(pairingCodeTextView, pairingCode);
  }

  private static void setPairingCode(TextView textView, String pairingCode) {
    if (textView == null) {
      loge(TAG, "No valid TextView to show pairing code.");
      return;
    }
    textView.setText(pairingCode);
  }
}
