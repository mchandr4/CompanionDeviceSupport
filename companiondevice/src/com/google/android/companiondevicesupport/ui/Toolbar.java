package com.google.android.companiondevicesupport.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.companiondevicesupport.R;

/**
 * A toolbar for display at the top of an activity.
 *
 * <p>The toolbar display a back button, a title, and two trailing action buttons. A progress bar is
 * available and displayed at the bottom of the toolbar. The primary action button is closer to the
 * end of the view. By default, all elements are hidden except for the back button, although
 * {@link #setOnBackButtonClickListneer(View.OnClickListener)} needs to be called before the
 * button has any action.
 */
public class Toolbar extends ConstraintLayout {
  private View backButton;
  private TextView titleView;
  private Button primaryActionButton;
  private Button secondaryActionButton;
  private ProgressBar progressBar;

  public Toolbar(Context context) {
    super(context, /* attrs= */ null);
    init(context);
  }

  public Toolbar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public Toolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  public Toolbar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context);
  }

  private void init(Context context) {
    LayoutInflater.from(context).inflate(R.layout.toolbar, this);

    backButton = findViewById(R.id.toolbar_back_button);
    titleView = findViewById(R.id.toolbar_title);
    primaryActionButton = findViewById(R.id.toolbar_primary_action_button);
    secondaryActionButton = findViewById(R.id.toolbar_secondary_action_button);
    progressBar = findViewById(R.id.toolbar_progress_bar);
  }

  /**
   * Sets the given {@link View.OnClickListener} to be invoked when the back button of this toolbar
   * has been clicked.
   */
  public void setOnBackButtonClickListener(@Nullable View.OnClickListener listener) {
    backButton.setOnClickListener(listener);
  }

  /** Sets the title of this toolbar to be the string of the given resource id. */
  public void setTitle(@StringRes int title) {
    titleView.setText(title);
    titleView.setVisibility(View.VISIBLE);
  }

  /** Hides the title of the toolbar. */
  public void hideTitle() {
    titleView.setVisibility(View.GONE);
  }

  /**
   * Displays the primary action button with the given text and {@link View.OnClickListener} to be
   * invoked when it is pressed.
   */
  public void setPrimaryActionButton(@StringRes int title, @NonNull View.OnClickListener listener) {
    primaryActionButton.setText(title);
    primaryActionButton.setOnClickListener(listener);
    primaryActionButton.setVisibility(View.VISIBLE);
  }


  /** Hides the primary action button if it is currently being shown. */
  public void hidePrimaryActionButton() {
    primaryActionButton.setOnClickListener(null);
    primaryActionButton.setVisibility(View.GONE);
  }

  /**
   * Displays the secondary action button with the given text and {@link View.OnClickListener} to be
   * invoked when it is pressed.
   */
  public void setSecondaryActionButton(
      @StringRes int title, @NonNull View.OnClickListener listener) {
    secondaryActionButton.setText(title);
    secondaryActionButton.setOnClickListener(listener);
    secondaryActionButton.setVisibility(View.VISIBLE);
  }

  /** Hides the secondawry action button if it is currently being shown. */
  public void hideSecondaryActionButton() {
    secondaryActionButton.setOnClickListener(null);
    secondaryActionButton.setVisibility(View.GONE);
  }

  /** Shows the progress bar at the bottom of this toolbar. */
  public void showProgressBar() {
    progressBar.setVisibility(View.VISIBLE);
  }

  /** Hides the progress bar if it is currently being shown. */
  public void hideProgressBar() {
    progressBar.setVisibility(View.GONE);
  }
}
