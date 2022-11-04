/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.car.setupwizardlib.util.ResultCodes.RESULT_SKIP;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.content.Intent;
import android.view.View;
import android.view.ViewStub;
import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;

/** Activity class for SUW association flow */
public class SetupWizardAssociationActivity extends AssociationBaseActivity {

  private static final String TAG = "SetupWizardCompanionAssociationActivity";
  private static final String EXTRA_AUTH_IS_SETUP_PROFILE = "is_setup_profile_association";
  private static final String EXTRA_USE_IMMERSIVE_MODE = "useImmersiveMode";
  private static final String EXTRA_HIDE_SKIP_BUTTON = "hide_skip_button";

  private CarSetupWizardCompatLayout carSetupWizardLayout;

  private boolean isImmersive = false;
  private boolean hideSkipButton = false;

  @Override
  protected void onStart() {
    super.onStart();
    handleImmersive();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      handleImmersive();
    }
  }

  private void handleImmersive() {
    if (!isImmersive) {
      return;
    }
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            // Set the content to appear under the system bars so that the
            // content doesn't resize when the system bars hide and show.
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Hide the nav bar and status bar
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  @Override
  protected void handleGuestProfile() {
    logd(TAG, "Skip Companion flow in guest profile.");
    setResult(RESULT_SKIP);
    finish();
  }

  @Override
  protected void showAssociateButton() {
    // No-op: SUW flow does not support passenger mode.
  }

  @Override
  protected void setIsProgressBarVisible(boolean isVisible) {
    carSetupWizardLayout.setProgressBarVisible(isVisible);
  }

  @Override
  protected void setAssociationTheme() {
    setTheme(R.style.Theme_CompanionDevice_Car_SetupWizard_NoActionBar);
  }

  @Override
  protected void prepareLayout() {
    resolveIntent();
    setContentView(R.layout.suw_companion_base_activity);
    carSetupWizardLayout = findViewById(R.id.car_setup_wizard_layout);
    ViewStub content = carSetupWizardLayout.getContentViewStub();
    if (content != null) {
      content.setLayoutResource(R.layout.suw_splitnav_fragment_container);
      content.inflate();
    } else {
      loge(TAG, "Couldn't find ViewStub in suw_companion_base_activity.");
    }
    carSetupWizardLayout.setBackButtonListener(l -> onBackPressed());
  }

  private void resolveIntent() {
    Intent intent = getIntent();
    if (intent == null) {
      loge(TAG, "Fail to get intent in SUW association activity.");
      return;
    }
    isStartedForSuw = true;
    isStartedForSetupProfile =
        intent.getBooleanExtra(EXTRA_AUTH_IS_SETUP_PROFILE, /* defaultValue= */ false);
    isImmersive = intent.getBooleanExtra(EXTRA_USE_IMMERSIVE_MODE, /* defaultValue= */ false);
    hideSkipButton = intent.getBooleanExtra(EXTRA_HIDE_SKIP_BUTTON, /* defaultValue= */ false);
  }

  @Override
  protected void showConfirmButtons() {
    carSetupWizardLayout.setPrimaryToolbarButtonText(getString(R.string.confirm));
    carSetupWizardLayout.setPrimaryToolbarButtonVisible(true);
    carSetupWizardLayout.setPrimaryToolbarButtonListener(
        l -> {
          model.acceptVerification();
          dismissButtons();
        });
    carSetupWizardLayout.setSecondaryToolbarButtonText(getString(R.string.retry));
    carSetupWizardLayout.setSecondaryToolbarButtonVisible(true);
    carSetupWizardLayout.setSecondaryToolbarButtonListener(l -> retryAssociation());
  }

  @Override
  protected void dismissButtons() {
    logd(TAG, "Dismissing SUW toolbar buttons.");
    carSetupWizardLayout.setPrimaryToolbarButtonVisible(false);
    carSetupWizardLayout.setSecondaryToolbarButtonVisible(false);
  }

  @Override
  protected void showSkipButton() {
    if (hideSkipButton) {
      logw(TAG, "Not in SUW or hideSkipButton is true; Do not show skip button.");
      return;
    }
    logd(TAG, "Show skip button on SUW page.");
    carSetupWizardLayout.setPrimaryToolbarButtonFlat(true);
    carSetupWizardLayout.setPrimaryToolbarButtonText(getString(R.string.skip));
    carSetupWizardLayout.setPrimaryToolbarButtonVisible(true);
    carSetupWizardLayout.setPrimaryToolbarButtonListener(
        v -> {
          setResult(RESULT_SKIP);
          finish();
        });
  }
}
