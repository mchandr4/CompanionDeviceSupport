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

package com.google.android.connecteddevice.transport;

import com.google.android.connecteddevice.transport.IConnectionProtocol;

/** Delegate for registering protocols to the platform. */
interface IProtocolDelegate {
  /** Add a protocol to the collection of supported protocols. */
  void addProtocol(in IConnectionProtocol protocol);

  /** Remove a protocol from the collection of supported protocols. */
  void removeProtocol(in IConnectionProtocol protocol);
}
