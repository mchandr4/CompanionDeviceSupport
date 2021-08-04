/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange;
import com.google.android.companionprotos.CapabilitiesExchangeProto.CapabilitiesExchange.OobChannelType;
import com.google.android.companionprotos.VersionExchangeProto.VersionExchange;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ExtensionRegistryLite;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the version and capabilities exchange that must be completed in order to establish a
 * secure channel with a remote device.
 *
 * <p>The {@link ResolvedConnection} result is used to determine which {@code SecureChannel} to use
 * for the association flow.
 */
public final class ConnectionResolver {
  private static final String TAG = "ConnectionResolver";

  public static final int MIN_SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE = 3;
  // Messaging and security versions are dictated by this device.
  @VisibleForTesting public static final int MESSAGING_VERSION = 3;
  @VisibleForTesting public static final int MIN_SECURITY_VERSION = 2;
  @VisibleForTesting public static final int MAX_SECURITY_VERSION = 3;
  private final ImmutableList<OobChannelType> supportedOobChannels;

  private enum State {
    UNKNOWN,
    WAITING_FOR_VERSION_EXCHANGE,
    WAITING_FOR_CAPABILITIES_EXCHANGE,
    DONE
  };

  private ErrorListener errorListener;
  private State state = State.WAITING_FOR_VERSION_EXCHANGE;
  private int messagingVersion;
  private int securityVersion;

  private final List<ConnectionResolvedListener> listeners;
  private final DeviceMessageStream deviceMessageStream;
  private final boolean isCapabilitiesEligible;

  public ConnectionResolver(
      DeviceMessageStream deviceMessageStream,
      boolean isCapabilitiesEligible) {
    this(deviceMessageStream, isCapabilitiesEligible, ImmutableList.of(OobChannelType.BT_RFCOMM));
  }

  public ConnectionResolver(
      DeviceMessageStream deviceMessageStream,
      boolean isCapabilitiesEligible,
      ImmutableList<OobChannelType> supportedOobChannels) {
    listeners = new ArrayList<>();
    this.deviceMessageStream = deviceMessageStream;
    this.isCapabilitiesEligible = isCapabilitiesEligible;
    this.supportedOobChannels = supportedOobChannels;
  }

  private void initializeListeners() {
    setErrorListener(deviceMessageStream::notifyDataReceivedErrorListener);
    registerListener((resolvedConnection) -> deviceMessageStream.setConnectionResolved(true));

    logd(TAG, "Setting data received listener");
    deviceMessageStream.setDataReceivedListener(this::onMessageReceived);
  }

  public void resolveConnection(@Nullable ConnectionResolvedListener listener) {
    initializeListeners();

    if (listener != null) {
      registerListener(listener);
    }
  }

  public void registerListener(ConnectionResolvedListener listener) {
    listeners.add(listener);
  }

  public void unregisterListener(ConnectionResolvedListener listener) {
    listeners.remove(listener);
  }

  public void setErrorListener(@Nullable ErrorListener errorListener) {
    this.errorListener = errorListener;
  }

  public void onMessageReceived(byte[] message) {
    logd(TAG, "Message received for state " + state);
    switch (state) {
      case WAITING_FOR_VERSION_EXCHANGE:
        exchangeVersion(message);
        break;
      case WAITING_FOR_CAPABILITIES_EXCHANGE:
        exchangeCapabilities(message);
        break;
      case DONE:
      case UNKNOWN:
        notifyError(null);
    }
  }

  private void exchangeVersion(byte[] message) {
    logd(TAG, "Exchanging version.");
    VersionExchange versionExchange;
    try {
      versionExchange =
          VersionExchange.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
    } catch (IOException e) {
      loge(TAG, "Could not parse version exchange message", e);
      notifyError(e);

      return;
    }
    int minMessagingVersion = versionExchange.getMinSupportedMessagingVersion();
    int maxMessagingVersion = versionExchange.getMaxSupportedMessagingVersion();
    int minSecurityVersion = versionExchange.getMinSupportedSecurityVersion();
    int maxSecurityVersion = versionExchange.getMaxSupportedSecurityVersion();
    if (minMessagingVersion > MESSAGING_VERSION
        || maxMessagingVersion < MESSAGING_VERSION
        || minSecurityVersion > MAX_SECURITY_VERSION
        || maxSecurityVersion < MIN_SECURITY_VERSION) {
      loge(
          TAG,
          "Unsupported message version for min "
              + minMessagingVersion
              + " and max "
              + maxMessagingVersion
              + " or security version for "
              + minSecurityVersion
              + " and max "
              + maxSecurityVersion
              + ".");
      notifyError(new IllegalStateException("Unsupported version."));
      return;
    }

    VersionExchange headunitVersion =
        VersionExchange.newBuilder()
            .setMinSupportedMessagingVersion(MESSAGING_VERSION)
            .setMaxSupportedMessagingVersion(MESSAGING_VERSION)
            .setMinSupportedSecurityVersion(MIN_SECURITY_VERSION)
            .setMaxSupportedSecurityVersion(MAX_SECURITY_VERSION)
            .build();
    deviceMessageStream.writeRawBytes(headunitVersion.toByteArray());
    logd(TAG, "Sent supported versions to the phone.");

    int maxSharedSecurityVersion = min(maxSecurityVersion, MAX_SECURITY_VERSION);
    messagingVersion = MESSAGING_VERSION;
    securityVersion = maxSharedSecurityVersion;
    logd(
        TAG,
        "Resolved to messaging v"
            + MESSAGING_VERSION + " and security v"
            + maxSharedSecurityVersion
            + ".");

    if (shouldDoCapabilitiesExchange()) {
      state = State.WAITING_FOR_CAPABILITIES_EXCHANGE;
    } else {
      notifyConnectionResolved(
          ResolvedConnection.create(
              messagingVersion, securityVersion, /* oobChannelTypes= */ null));
    }
  }

  private boolean shouldDoCapabilitiesExchange() {
    return isCapabilitiesEligible
        && securityVersion >= MIN_SECURITY_VERSION_FOR_CAPABILITIES_EXCHANGE;
  }

  private void exchangeCapabilities(byte[] message) {
    CapabilitiesExchange capabilitiesExchange;
    try {
      capabilitiesExchange =
          CapabilitiesExchange.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
    } catch (IOException e) {
      loge(TAG, "Could not parse capabilities exchange message", e);
      notifyError(e);
      return;
    }

    // Must wrap in an ArrayList to prevent retainAll from throwing an
    // UnsupportedOperationException.
    List<OobChannelType> sharedSupportedOobChannels =
        new ArrayList<>(capabilitiesExchange.getSupportedOobChannelsList());
    logd(
        TAG,
        "Received " + sharedSupportedOobChannels.size() + " supported OOB channels from device.");
    sharedSupportedOobChannels.retainAll(supportedOobChannels);
    logd(TAG, "Found " + sharedSupportedOobChannels.size() + " common OOB channels.");
    CapabilitiesExchange headunitCapabilities = CapabilitiesExchange.newBuilder()
      .addAllSupportedOobChannels(sharedSupportedOobChannels)
      .build();

    deviceMessageStream.writeRawBytes(headunitCapabilities.toByteArray());

    logd(TAG, "Sent supported capabilities to the phone.");

    notifyConnectionResolved(
        ResolvedConnection.create(
            messagingVersion, securityVersion, ImmutableList.copyOf(sharedSupportedOobChannels)));
  }

  private void notifyConnectionResolved(ResolvedConnection resolvedConnection) {
    state = State.DONE;

    for (ConnectionResolvedListener listener : listeners) {
      listener.onConnectionResolved(resolvedConnection);
    }
  }

  private void notifyError(@Nullable Exception e) {
    if (errorListener != null) {
      errorListener.onError(e);
    }
  }

  /** Listener to be invoked when version and capabilities exchange is complete. */
  public interface ConnectionResolvedListener {
    void onConnectionResolved(ResolvedConnection resolvedConnection);
  }

  /** Listener to be invoked when there is an error with the version or capabilities exchange. */
  public interface ErrorListener {
    void onError(Exception e);
  }

  /** Holds the values of the version and capabilities exchange. */
  @AutoValue
  public abstract static class ResolvedConnection {
    public static ResolvedConnection create(
        int messagingVersion,
        int securityVersion,
        @Nullable ImmutableList<OobChannelType> oobChannelTypes) {
      return new AutoValue_ConnectionResolver_ResolvedConnection(
          messagingVersion, securityVersion, oobChannelTypes);
    }

    public abstract int messagingVersion();

    public abstract int securityVersion();

    @Nullable
    public abstract ImmutableList<OobChannelType> oobChannelTypes();
  }
}
