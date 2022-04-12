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

package com.panasonic.iapx;

import com.panasonic.iapx.IDeviceConnection;

interface IDeviceConnectionDelegate {
  oneway void OnConnectionReady(in com.panasonic.iapx.IDeviceConnection connection, in int transportType) = 0;
  oneway void OnConnectionClosed(in com.panasonic.iapx.IDeviceConnection connection) = 1;
  oneway void OnDeviceNameUpdate(in com.panasonic.iapx.IDeviceConnection connection, in String name) = 100;
  oneway void OnDeviceTransientUUIDUpdate(in com.panasonic.iapx.IDeviceConnection connection, in String uuid) = 101;
  oneway void OnEAPSessionStart(in com.panasonic.iapx.IDeviceConnection connection, in long eapSessionId, in String eapProtocolName) = 3000;
  oneway void OnEAPSessionStop(in com.panasonic.iapx.IDeviceConnection connection, in long eapSessionId) = 3001;
  oneway void OnEAPData(in com.panasonic.iapx.IDeviceConnection connection, in long eapSessionId, in byte[] data) = 3010;
}
