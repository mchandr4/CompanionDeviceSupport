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

package com.google.android.connecteddevice.api;

import com.google.android.connecteddevice.model.StartAssociationResponse;

/**
 * Callback for triggered association events.
 *
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 *
 * Next transaction value: 5
 */
oneway interface IAssociationCallback {
    /** Triggered when IHU starts advertising for association successfully. */
    void onAssociationStartSuccess(in StartAssociationResponse response) = 0;

    /** Triggered when IHU failed to start advertising for association. */
    void onAssociationStartFailure() = 1;

    /** Triggered when an error has been encountered during association with a new device. */
    void onAssociationError(in int error) = 2;

    /**  Triggered when a pairing code is available to be present. */
    void onVerificationCodeAvailable(in String code) = 3;

    /** Triggered when the association has completed */
    void onAssociationCompleted() = 4;
}
