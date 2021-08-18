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

import static com.google.android.companionprotos.SystemQueryType.APP_NAME;
import static com.google.android.connecteddevice.model.DeviceMessage.OperationType.CLIENT_MESSAGE;
import static com.google.android.connecteddevice.model.DeviceMessage.OperationType.QUERY;
import static com.google.android.connecteddevice.model.DeviceMessage.OperationType.QUERY_RESPONSE;
import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.os.ParcelUuid;
import android.os.RemoteException;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.companionprotos.Query;
import com.google.android.companionprotos.QueryResponse;
import com.google.android.companionprotos.SystemQuery;
import com.google.android.connecteddevice.model.AssociatedDevice;
import com.google.android.connecteddevice.model.ConnectedDevice;
import com.google.android.connecteddevice.model.DeviceMessage;
import com.google.android.connecteddevice.model.DeviceMessage.OperationType;
import com.google.android.connecteddevice.util.ByteUtils;
import com.google.android.connecteddevice.util.Logger;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for a feature that must bind to {@code ConnectedDeviceService}. Callbacks are
 * registered automatically and events are forwarded to internal methods. Override these to add
 * custom logic for callback triggers.
 */
public abstract class RemoteFeature {

  private static final String TAG = "RemoteFeature";

  /** Intent action used to request a device be associated. */
  public static final String ACTION_ASSOCIATION_SETTING =
      "com.google.android.connecteddevice.api.ASSOCIATION_ACTIVITY";

  /** Data name for associated device. */
  public static final String ASSOCIATED_DEVICE_DATA_NAME_EXTRA =
      "com.google.android.connecteddevice.api.ASSOCIATED_DEVICE";

  /** Id for the system query feature. */
  protected static final ParcelUuid SYSTEM_FEATURE_ID =
      ParcelUuid.fromString("892ac5d9-e9a5-48dc-874a-c01e3cb00d5d");

  private final QueryIdGenerator queryIdGenerator = new QueryIdGenerator();

  // queryId -> callback
  private final Map<Integer, QueryCallback> queryCallbacks = new ConcurrentHashMap<>();

  // queryId -> original sender for response
  private final Map<Integer, ParcelUuid> queryResponseRecipients = new ConcurrentHashMap<>();

  private final Context context;

  private final ParcelUuid featureId;

  private final CompanionConnector connector;

  private IConnectedDeviceManager connectedDeviceManager;

  /**
   * Create a new RemoteFeature.
   *
   * @param context {@link Context} of the application process
   * @param featureId The id for this feature
   */
  protected RemoteFeature(@NonNull Context context, @NonNull ParcelUuid featureId) {
    this(context, featureId, /* forceFgUserBind= */ false);
  }

  /**
   * Create a new RemoteFeature.
   *
   * @param context {@link Context} of the application process
   * @param featureId The id for this feature
   * @param forceFgUserBind Force binding to the foreground user service to avoid a cross-user bind
   */
  protected RemoteFeature(
      @NonNull Context context,
      @NonNull ParcelUuid featureId,
      boolean forceFgUserBind) {
    this.context = context;
    this.featureId = featureId;
    connector =
        new CompanionConnector(
            context,
            new CompanionConnector.Callback() {
              @Override
              public void onConnected() {
                logd(TAG, "Successfully connected. Initializing feature.");
                connectedDeviceManager = connector.getConnectedDeviceManager();
                setupConnectedDeviceManager();
              }

              @Override
              public void onDisconnected() {
                logd(TAG, "Disconnected from companion. Stopping feature.");
                connectedDeviceManager = null;
                stop();
              }

              @Override
              public void onFailedToConnect() {
                loge(TAG, "Failed to connect. Stopping feature.");
                stop();
              }
            },
            forceFgUserBind);
  }

  /**
   * Create a new RemoteFeature with an already initialized {@link IConnectedDeviceManager}.
   *
   * @param context {@link Context} of the application process
   * @param featureId The id for this feature
   * @param connectedDeviceManager {@link IConnectedDeviceManager} reference for API calls
   */
  protected RemoteFeature(
      @NonNull Context context,
      @NonNull ParcelUuid featureId,
      @Nullable IConnectedDeviceManager connectedDeviceManager) {
    this(context, featureId);
    this.connectedDeviceManager = connectedDeviceManager;
  }

  /** Start setup process and begin binding to {@code ConnectedDeviceService}. */
  @CallSuper
  public void start() {
    if (connectedDeviceManager == null) {
      connector.connect();
      return;
    }
    setupConnectedDeviceManager();
  }

  /** Called when the hosting service is being destroyed. Cleans up internal feature logic. */
  @CallSuper
  public void stop() {
    if (connectedDeviceManager == null) {
      return;
    }
    try {
      connectedDeviceManager.unregisterConnectionCallback(connectionCallback);
      for (ConnectedDevice device : connectedDeviceManager.getActiveUserConnectedDevices()) {
        connectedDeviceManager.unregisterDeviceCallback(device, featureId, deviceCallback);
      }
      connectedDeviceManager.unregisterDeviceAssociationCallback(deviceAssociationCallback);
      connectedDeviceManager.unregisterOnLogRequestedListener(
          Logger.getLogger().getLoggerId(), onLogRequestedListener);
    } catch (RemoteException e) {
      loge(TAG, "Error while stopping remote feature.", e);
    }
    if (connector != null) {
      connector.disconnect();
    }
    connectedDeviceManager = null;
  }

  /** Return the {@link Context} registered with the feature. */
  @NonNull
  public Context getContext() {
    return context;
  }

  /**
   * Return the {@link IConnectedDeviceManager} bound with the feature. Returns {@code null} if
   * binding has not completed yet.
   */
  @Nullable
  public IConnectedDeviceManager getConnectedDeviceManager() {
    return connectedDeviceManager;
  }

  /** Return the {@link ParcelUuid} feature id registered for the feature. */
  @NonNull
  public ParcelUuid getFeatureId() {
    return featureId;
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull String deviceId, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      onMessageFailedToSend(deviceId, message, /* isTransient= */ true);
      return;
    }
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send "
              + "secure message.");
      onMessageFailedToSend(deviceId, message, /* isTransient= */ false);
      return;
    }

    sendMessageSecurely(device, message);
  }

  /** Securely send message to a device. */
  public void sendMessageSecurely(@NonNull ConnectedDevice device, @NonNull byte[] message) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      onMessageFailedToSend(device.getDeviceId(), message, /* isTransient= */ true);
      return;
    }
    DeviceMessage deviceMessage = new DeviceMessage(
        getFeatureId().getUuid(),
        /* isMessageEncrypted= */ true,
        CLIENT_MESSAGE,
        message);
    try {
      connectedDeviceManager.sendMessage(device, deviceMessage);
    } catch (RemoteException e) {
      loge(TAG, "Error while sending secure message.", e);
      onMessageFailedToSend(device.getDeviceId(), message, /* isTransient= */ false);
    }
  }

  /** Securely send a query to a device and register a {@link QueryCallback} for a response. */
  public void sendQuerySecurely(
      @NonNull String deviceId,
      @NonNull byte[] request,
      @Nullable byte[] parameters,
      @NonNull QueryCallback callback) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send query, ConnectedDeviceManager not actively connected.");
      callback.onQueryFailedToSend(/* isTransient= */ true);
      return;
    }
    ConnectedDevice device = getConnectedDeviceById(deviceId);
    if (device == null) {
      loge(
          TAG,
          "No matching device found with id "
              + deviceId
              + " when trying to send a secure query.");
      callback.onQueryFailedToSend(/* isTransient= */ false);
      return;
    }

    sendQuerySecurely(device, request, parameters, callback);
  }

  /** Securely send a query to a device and register a {@link QueryCallback} for a response. */
  public void sendQuerySecurely(
      @NonNull ConnectedDevice device,
      @NonNull byte[] request,
      @Nullable byte[] parameters,
      @NonNull QueryCallback callback) {
    sendQuerySecurelyInternal(device, featureId, request, parameters, callback);
  }

  private void sendQuerySecurelyInternal(
      @NonNull ConnectedDevice device,
      @NonNull ParcelUuid recipient,
      @NonNull byte[] request,
      @Nullable byte[] parameters,
      @NonNull QueryCallback callback) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send message, ConnectedDeviceManager not actively connected.");
      callback.onQueryFailedToSend(/* isTransient= */ true);
      return;
    }
    int id = queryIdGenerator.next();
    Query.Builder builder = Query.newBuilder()
        .setId(id)
        .setSender(ByteString.copyFrom(ByteUtils.uuidToBytes(featureId.getUuid())))
        .setRequest(ByteString.copyFrom(request));
    if (parameters != null) {
      builder.setParameters(ByteString.copyFrom(parameters));
    }

    logd(TAG, "Sending secure query with id " + id + ".");
    DeviceMessage deviceMessage = new DeviceMessage(
        recipient.getUuid(),
        /* isMessageEncrypted= */ true,
        QUERY,
        builder.build().toByteArray());
    try {
      connectedDeviceManager.sendMessage(device, deviceMessage);
    } catch (RemoteException e) {
      loge(TAG, "Error while sending secure query.", e);
      callback.onQueryFailedToSend(/* isTransient= */ false);
      return;
    }
    queryCallbacks.put(id, callback);
  }

  /** Send a secure response to a query with an indication of whether it was successful. */
  public void respondToQuerySecurely(
      @NonNull ConnectedDevice device,
      int queryId,
      boolean success,
      @Nullable byte[] response) {
    if (connectedDeviceManager == null) {
      loge(TAG, "Unable to send query response, ConnectedDeviceManager not actively connected.");
      return;
    }
    ParcelUuid recipientId = queryResponseRecipients.remove(queryId);
    if (recipientId == null) {
      loge(TAG, "Unable to send response to unrecognized query " + queryId + ".");
      return;
    }

    QueryResponse.Builder builder =
        QueryResponse.newBuilder()
            .setQueryId(queryId)
            .setSuccess(success);
    if (response != null) {
      builder.setResponse(ByteString.copyFrom(response));
    }
    QueryResponse queryResponse = builder.build();
    logd(TAG, "Sending response to query " + queryId + " to "  + recipientId + ".");
    DeviceMessage deviceMessage = new DeviceMessage(
        recipientId.getUuid(),
        /* isMessageEncrypted= */ true,
        QUERY_RESPONSE,
        queryResponse.toByteArray());
    try {
      connectedDeviceManager.sendMessage(device, deviceMessage);
    } catch (RemoteException e) {
      loge(TAG, "Error while sending query response.", e);
    }
  }

  /**
   * Return the {@link ConnectedDevice} with a matching device id for the currently active user.
   * Returns {@code null} if no match found.
   */
  @Nullable
  public ConnectedDevice getConnectedDeviceById(@NonNull String deviceId) {
    if (connectedDeviceManager == null) {
      loge(
          TAG,
          "Unable to get connected device. ConnectedDeviceManager not actively " + "connected.");
      return null;
    }
    List<ConnectedDevice> connectedDevices;
    try {
      connectedDevices = connectedDeviceManager.getActiveUserConnectedDevices();
    } catch (RemoteException e) {
      loge(TAG, "Exception while retrieving connected devices.", e);
      return null;
    }

    for (ConnectedDevice device : connectedDevices) {
      if (device.getDeviceId().equals(deviceId)) {
        return device;
      }
    }

    return null;
  }

  /** Query the {@link ConnectedDevice} for its companion application name. */
  public void getCompanionApplicationName(ConnectedDevice device, AppNameCallback callback) {
    SystemQuery systemQuery = SystemQuery.newBuilder().setType(APP_NAME).build();
    sendQuerySecurelyInternal(
        device,
        SYSTEM_FEATURE_ID,
        systemQuery.toByteArray(),
        /* parameters= */ null,
        new QueryCallback() {
          @Override
          public void onSuccess(@Nullable byte[] response) {
            if (response == null || response.length == 0) {
              loge(TAG, "Received a null or empty response for the application name.");
              callback.onError();
            }
            String appName = new String(response, UTF_8);
            logd(TAG, "Received successful app name query response of " + appName + ".");
            callback.onNameReceived(appName);
          }

          @Override
          public void onError(@Nullable byte[] response) {
            loge(TAG, "Received an error response when querying for application name.");
            callback.onError();
          }

          @Override
          public void onQueryFailedToSend(boolean isTransient) {
            loge(TAG, "Failed to send the query for the application name.");
            callback.onError();
          }
        });
  }

  // These can be overridden to perform custom actions.

  /**
   * Called when {@link #getConnectedDeviceManager()} will return a non-null reference and is ready
   * for interaction.
   */
  protected void onReady() {}

  /** Called when a new {@link ConnectedDevice} is connected. */
  protected void onDeviceConnected(@NonNull ConnectedDevice device) {}

  /** Called when a {@link ConnectedDevice} disconnects. */
  protected void onDeviceDisconnected(@NonNull ConnectedDevice device) {}

  /** Called when a secure channel has been established with a {@link ConnectedDevice}. */
  protected void onSecureChannelEstablished(@NonNull ConnectedDevice device) {}

  /**
   * Called when a message fails to send to a device.
   *
   * @param deviceId Id of the device the message failed to send to.
   * @param message Message to send.
   * @param isTransient {@code true} if cause of failure is transient and can be retried. {@code
   *     false} if failure is permanent.
   */
  protected void onMessageFailedToSend(
      @NonNull String deviceId,
      @NonNull byte[] message,
      boolean isTransient) {}

  /** Called when a new {@link byte[]} message is received for this feature. */
  protected void onMessageReceived(@NonNull ConnectedDevice device, @NonNull byte[] message) {}

  /** Called when a new query is received for this feature. */
  protected void onQueryReceived(
      @NonNull ConnectedDevice device,
      int queryId,
      @NonNull byte[] request,
      @NonNull byte[] parameters) {}

  /** Called when an error has occurred with the connection. */
  protected void onDeviceError(@NonNull ConnectedDevice device, int error) {}

  /** Called when a new {@link AssociatedDevice} is added for the given user. */
  protected void onAssociatedDeviceAdded(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is removed for the given user. */
  protected void onAssociatedDeviceRemoved(@NonNull AssociatedDevice device) {}

  /** Called when an {@link AssociatedDevice} is updated for the given user. */
  protected void onAssociatedDeviceUpdated(@NonNull AssociatedDevice device) {}

  private void setupConnectedDeviceManager() {
    onReady();
    try {
      connectedDeviceManager.registerActiveUserConnectionCallback(connectionCallback);
      connectedDeviceManager.registerDeviceAssociationCallback(deviceAssociationCallback);
      connectedDeviceManager.registerOnLogRequestedListener(
          Logger.getLogger().getLoggerId(), onLogRequestedListener);
      logd(TAG, "Setting up ConnectedDeviceManager.");
      List<ConnectedDevice> activeUserConnectedDevices =
          connectedDeviceManager.getActiveUserConnectedDevices();
      for (ConnectedDevice device : activeUserConnectedDevices) {
        onDeviceConnected(device);
        if (device.hasSecureChannel()) {
          onSecureChannelEstablished(device);
        }
        connectedDeviceManager.registerDeviceCallback(device, featureId, deviceCallback);
      }
    } catch (RemoteException e) {
      loge(TAG, "Error while inspecting connected devices.", e);
    }
  }

  private void processIncomingMessage(ConnectedDevice device, DeviceMessage deviceMessage) {
    OperationType operationType = deviceMessage.getOperationType();
    byte[] message = deviceMessage.getMessage();
    switch (operationType) {
      case CLIENT_MESSAGE:
        logd(TAG, "Received client message. Passing on to feature.");
        onMessageReceived(device, message);
        return;
      case QUERY:
        try {
          Query query = Query.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
          processQuery(device, query);
        } catch (InvalidProtocolBufferException e) {
          loge(TAG, "Unable to parse query.", e);
        }
        return;
      case QUERY_RESPONSE:
        try {
          QueryResponse response =
              QueryResponse.parseFrom(message, ExtensionRegistryLite.getEmptyRegistry());
          processQueryResponse(response);
        } catch (InvalidProtocolBufferException e) {
          loge(TAG, "Unable to parse query response.", e);
        }
        return;
      default:
        loge(TAG, "Received unknown type of message: " + operationType + ". Ignoring.");
    }
  }

  private void processQuery(ConnectedDevice device, Query query) {
    logd(TAG, "Received a new query with id " + query.getId() + ". Passing on to feature.");
    ParcelUuid sender = new ParcelUuid(ByteUtils.bytesToUUID(query.getSender().toByteArray()));
    queryResponseRecipients.put(query.getId(), sender);
    onQueryReceived(
        device,
        query.getId(),
        query.getRequest().toByteArray(),
        query.getParameters().toByteArray());
  }

  private void processQueryResponse(QueryResponse response) {
    logd(TAG, "Received a query response. Issuing registered callback.");
    QueryCallback callback = queryCallbacks.remove(response.getQueryId());
    if (callback == null) {
      loge(TAG, "Unable to locate callback for query " + response.getQueryId() + ".");
      return;
    }
    if (response.getSuccess()) {
      callback.onSuccess(response.getResponse().toByteArray());
    } else {
      callback.onError(response.getResponse().toByteArray());
    }
  }

  private final IConnectionCallback connectionCallback =
      new IConnectionCallback.Stub() {
        @Override
        public void onDeviceConnected(ConnectedDevice connectedDevice) throws RemoteException {
          connectedDeviceManager.registerDeviceCallback(
              connectedDevice, featureId, deviceCallback);
          RemoteFeature.this.onDeviceConnected(connectedDevice);
          if (connectedDevice.hasSecureChannel()) {
            RemoteFeature.this.onSecureChannelEstablished(connectedDevice);
          }
        }

        @Override
        public void onDeviceDisconnected(ConnectedDevice connectedDevice) throws RemoteException {
          connectedDeviceManager.unregisterDeviceCallback(
              connectedDevice, featureId, deviceCallback);
          RemoteFeature.this.onDeviceDisconnected(connectedDevice);
        }
      };

  private final IDeviceCallback deviceCallback =
      new IDeviceCallback.Stub() {
        @Override
        public void onSecureChannelEstablished(ConnectedDevice connectedDevice) {
          RemoteFeature.this.onSecureChannelEstablished(connectedDevice);
        }

        @Override
        public void onMessageReceived(ConnectedDevice connectedDevice, DeviceMessage message) {
          processIncomingMessage(connectedDevice, message);
        }

        @Override
        public void onDeviceError(ConnectedDevice connectedDevice, int error) {
          RemoteFeature.this.onDeviceError(connectedDevice, error);
        }
      };

  private final IDeviceAssociationCallback deviceAssociationCallback =
      new IDeviceAssociationCallback.Stub() {
        @Override
        public void onAssociatedDeviceAdded(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceAdded(device);
        }

        @Override
        public void onAssociatedDeviceRemoved(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceRemoved(device);
        }

        @Override
        public void onAssociatedDeviceUpdated(AssociatedDevice device) {
          RemoteFeature.this.onAssociatedDeviceUpdated(device);
        }
      };

  private final IOnLogRequestedListener onLogRequestedListener =
      new IOnLogRequestedListener.Stub() {
        @Override
        public void onLogRecordsRequested() {
          Logger logger = Logger.getLogger();
          try {
            connectedDeviceManager.processLogRecords(logger.getLoggerId(), logger.toByteArray());
          } catch (RemoteException exception) {
            loge(TAG, "Failed to send log records for logger" + logger + ".", exception);
          }
        }
      };

  /** A generator of unique IDs for queries. */
  private static class QueryIdGenerator {
    private final AtomicInteger messageId = new AtomicInteger(0);

    int next() {
      int current = messageId.getAndIncrement();
      messageId.compareAndSet(Integer.MAX_VALUE, 0);
      return current;
    }
  }

  /** Callback for a query response. */
  public interface QueryCallback {
    /** Invoked with a successful response to a query. */
    default void onSuccess(@Nullable byte[] response) {}

    /** Invoked with an unsuccessful response to a query. */
    default void onError(@Nullable byte[] response) {}

    /**
     * Invoked when a query failed to send to the device. {@code isTransient} is set to {@code true}
     * if cause of failure is transient and can be retried, or {@code false} if failure is
     * permanent.
     */
    default void onQueryFailedToSend(boolean isTransient) {}
  }

  /** Callback for a query for the name of the companion application on the connected device. */
  public interface AppNameCallback {
    /** Invoked with the name of the companion application on the connected device. */
    default void onNameReceived(@NonNull String appName) {}

    /** Invoked when the name failed to be retrieved from the connected device. */
    default void onError() {}
  }
}
