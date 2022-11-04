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

package com.google.android.companiondevicesupport;

import static com.android.car.ui.core.CarUi.requireToolbar;
import static com.android.car.ui.toolbar.Toolbar.State.SUBPAGE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.annotation.StringRes;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;
import java.util.Arrays;

/** Activity class for association */
public class AssociationActivity extends AssociationBaseActivity {
  private static final String COMPANION_NOT_AVAILABLE_DIALOG_TAG = "CompanionNotAvailableDialog";
  private static final String PROFILE_SWITCH_PACKAGE_NAME = "com.android.car.settings";
  private static final String PROFILE_SWITCH_CLASS_NAME_R =
      "com.android.car.settings.users.UserSwitcherActivity";
  private static final String PROFILE_SWITCH_CLASS_NAME =
      "com.android.car.settings.profiles.ProfileSwitcherActivity";
  private ToolbarController toolbar;

  @Override
  protected void handleGuestProfile() {
    showCompanionNotAvailableDialog();
  }

  @Override
  protected void setAssociationTheme() {
    setTheme(R.style.Theme_CompanionDevice_Car_CarUi_WithToolbar);
  }

  @Override
  protected void prepareLayout() {
    setContentView(R.layout.settings_base_activity);
    toolbar = requireToolbar(this);
    toolbar.setState(SUBPAGE);
  }

  @Override
  protected void showConfirmButtons() {
    MenuItem cancelButton =
        MenuItem.builder(this)
            .setTitle(R.string.retry)
            .setOnClickListener(i -> retryAssociation())
            .build();
    MenuItem confirmButton =
        MenuItem.builder(this)
            .setTitle(R.string.confirm)
            .setOnClickListener(
                i -> {
                  model.acceptVerification();
                  dismissButtons();
                })
            .build();
    toolbar.setMenuItems(Arrays.asList(cancelButton, confirmButton));
  }

  @Override
  protected void showAssociateButton() {
    MenuItem associationButton =
        MenuItem.builder(this)
            .setTitle(R.string.add_associated_device_button)
            .setOnClickListener(
                v -> {
                  dismissButtons();
                  startAssociation();
                })
            .build();

    toolbar.setMenuItems(Arrays.asList(associationButton));
  }

  @Override
  protected void setIsProgressBarVisible(boolean isVisible) {
    toolbar.getProgressBar().setVisible(isVisible);
  }

  @Override
  protected void dismissButtons() {
    toolbar.setMenuItems(null);
  }

  @Override
  protected void showSkipButton() {
    // No-op: No need to show skip button in Settings flow.
  }

  private void showCompanionNotAvailableDialog() {
    CompanionNotAvailableDialogFragment fragment = new CompanionNotAvailableDialogFragment();
    fragment.show(getSupportFragmentManager(), COMPANION_NOT_AVAILABLE_DIALOG_TAG);
  }

  /** Dialog fragment to notify CompanionDevice is not available to guest user. */
  public static class CompanionNotAvailableDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      @StringRes
      int messageResId =
          VERSION.SDK_INT <= VERSION_CODES.R
              ? R.string.companion_not_available_dialog_switch_user_message
              : R.string.companion_not_available_dialog_message;
      AlertDialog.Builder builder =
          new AlertDialog.Builder(getActivity())
              .setTitle(getString(R.string.companion_not_available_dialog_title))
              .setMessage(getString(messageResId))
              .setNegativeButton(getString(R.string.ok), (d, w) -> getActivity().finish())
              .setCancelable(false);
      addChangeProfileButton(builder);
      Dialog dialog = builder.create();
      dialog.setCanceledOnTouchOutside(/* cancel= */ false);
      return dialog;
    }

    private void addChangeProfileButton(AlertDialog.Builder builder) {
      if (VERSION.SDK_INT <= VERSION_CODES.Q) {
        return;
      }
      String profileSwitcherClassName =
          VERSION.SDK_INT >= VERSION_CODES.S
              ? PROFILE_SWITCH_CLASS_NAME
              : PROFILE_SWITCH_CLASS_NAME_R;
      builder.setPositiveButton(
          getString(R.string.change_profile),
          (d, w) -> {
            Intent intent = new Intent();
            intent.setComponent(
                new ComponentName(PROFILE_SWITCH_PACKAGE_NAME, profileSwitcherClassName));
            getActivity().startActivity(intent);
          });
    }
  }
}
