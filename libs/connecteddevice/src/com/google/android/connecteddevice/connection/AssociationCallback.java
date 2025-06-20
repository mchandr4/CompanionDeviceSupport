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

package com.google.android.connecteddevice.connection;

import androidx.annotation.NonNull;
import com.google.android.connecteddevice.model.Errors.DeviceError;
import com.google.android.connecteddevice.model.StartAssociationResponse;

/** Callbacks that will be invoked during associating a new client. */
public interface AssociationCallback {

  /**
   * Invoked when IHU starts advertising with its device name for association successfully.
   *
   * @param response contains the data which is generated per association request.
   */
  default void onAssociationStartSuccess(@NonNull StartAssociationResponse response) {}

    /** Invoked when IHU failed to start advertising for association. */
    default void onAssociationStartFailure() {}

    /**
     * Invoked when a {@link DeviceError} has been encountered in attempting
     * to associate a new device.
     *
     * @param error The failure indication.
     */
    default void onAssociationError(@DeviceError int error) {}

    /**
     * Invoked when a verification code needs to be displayed. The user needs to confirm, and
     * then notify that the code has been verified.
     *
     * @param code The verification code.
     */
    default void onVerificationCodeAvailable(@NonNull String code) {}

    /**
     * Invoked when the association has completed.
     *
     * @param deviceId The id of the newly associated device.
     */
    default void onAssociationCompleted(@NonNull String deviceId) {}
}
