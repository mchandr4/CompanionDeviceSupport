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

package com.google.android.connecteddevice.transport.spp;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * An object representing a message that was requested to be sent via the {@link
 * ConnectedDeviceSppDelegateBinder}.
 */
public class PendingSentMessage implements Parcelable {

  private OnSuccessListener onSuccessListener;

  public void setOnSuccessListener(@Nullable OnSuccessListener onSuccessListener) {
    this.onSuccessListener = onSuccessListener;
  }

  @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
  public void notifyMessageSent() {
    if (onSuccessListener != null) {
      onSuccessListener.onSuccess();
    }
  }

  public static final Creator<PendingSentMessage> CREATOR =
      new Creator<PendingSentMessage>() {
        @Override
        public PendingSentMessage createFromParcel(Parcel in) {
          return new PendingSentMessage();
        }

        @Override
        public PendingSentMessage[] newArray(int size) {
          return new PendingSentMessage[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {}

  /** Callback that is run when the message is successfully sent. */
  public interface OnSuccessListener {
    void onSuccess();
  }
}
