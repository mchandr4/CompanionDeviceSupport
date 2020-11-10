package com.google.android.companiondevicesupport;

import androidx.lifecycle.ViewModelProviders;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Fragment that displays when an error happens during association. */
public class AssociationErrorFragment extends Fragment {

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.association_error_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    view.findViewById(R.id.retry_button)
        .setOnClickListener(
            v -> {
              AssociatedDeviceViewModel model =
                  ViewModelProviders.of(getActivity()).get(AssociatedDeviceViewModel.class);
              model.retryAssociation();
            });
  }
}
