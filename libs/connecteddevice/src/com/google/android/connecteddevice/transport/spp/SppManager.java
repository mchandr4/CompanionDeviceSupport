package com.google.android.connecteddevice.transport.spp;

import static com.google.android.connecteddevice.util.SafeLog.logd;
import static com.google.android.connecteddevice.util.SafeLog.loge;
import static com.google.android.connecteddevice.util.SafeLog.logw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.connecteddevice.util.ThreadSafeCallbacks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A generic class that handles all the Spp connection events including:
 *
 * <ol>
 *   <li>listen and accept connection request from client.
 *   <li>send a message through an established connection.
 *   <li>notify any connection or message events happening during the connection.
 * </ol>
 */
public class SppManager {
  // An int will take 4 bytes.
  static final int LENGTH_BYTES_SIZE = 4;
  private static final String TAG = "SppManager";
  // Service names and UUIDs of SDP(Service Discovery Protocol) record, need to keep it consistent
  // among client and server.
  private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
  private final boolean isSecure;
  private final Object lock = new Object();
  /** Task to listen to secure RFCOMM channel. */
  @VisibleForTesting AcceptTask acceptTask;

  @VisibleForTesting ConnectTask connectTask;

  private ReadMessageTask readMessageTask;
  private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
  private final Executor taskCallbackExecutor;
  private BluetoothDevice device;

  @GuardedBy("lock")
  @VisibleForTesting
  ConnectionState state;

  @VisibleForTesting BluetoothSocket connectedSocket;
  private final ThreadSafeCallbacks<ConnectionCallback> callbacks = new ThreadSafeCallbacks<>();
  private final ThreadSafeCallbacks<OnMessageReceivedListener> receivedListeners =
      new ThreadSafeCallbacks<>();

  public SppManager(@NonNull boolean isSecure) {
    this(isSecure, Executors.newSingleThreadExecutor());
  }

  @VisibleForTesting
  SppManager(@NonNull boolean isSecure, @NonNull Executor executor) {
    this.isSecure = isSecure;
    taskCallbackExecutor = executor;
  }

  @VisibleForTesting
  enum ConnectionState {
    NONE,
    LISTEN,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
  }

  /**
   * Registers the given callback to be notified of various events within the {@link SppManager}.
   *
   * @param callback The callback to be notified.
   */
  public void registerCallback(@NonNull ConnectionCallback callback, @NonNull Executor executor) {
    callbacks.add(callback, executor);
  }

  /**
   * Unregisters a previously registered callback.
   *
   * @param callback The callback to unregister.
   */
  public void unregisterCallback(@NonNull ConnectionCallback callback) {
    callbacks.remove(callback);
  }

  /**
   * Adds a listener to be notified of a write to characteristics.
   *
   * @param listener The listener to invoke.
   */
  public void addOnMessageReceivedListener(
      @NonNull OnMessageReceivedListener listener, @NonNull Executor executor) {
    receivedListeners.add(listener, executor);
  }

  /**
   * Removes the given listener from being notified of characteristic writes.
   *
   * @param listener The listener to remove.
   */
  public void removeOnMessageReceivedListener(@NonNull OnMessageReceivedListener listener) {
    receivedListeners.remove(listener);
  }

  /**
   * Start listening to connection request from the client.
   *
   * @param serviceUuid The Uuid which the accept task is listening on.
   * @return {@code true} if listening is started successfully
   */
  public boolean startListening(@NonNull UUID serviceUuid) {
    logd(TAG, "Start socket to listening to incoming connection request.");
    if (connectedSocket != null) {
      synchronized (lock) {
        disconnectLocked();
      }
    }
    if (acceptTask != null) {
      acceptTask.cancel();
    }
    // Start the task to listen on a BluetoothServerSocket
    acceptTask =
        new AcceptTask(adapter, isSecure, serviceUuid, acceptTaskListener, taskCallbackExecutor);
    if (!acceptTask.startListening()) {
      // TODO(b/159376003): Handle listening error.
      acceptTask.cancel();
      acceptTask = null;
      return false;
    }
    synchronized (lock) {
      state = ConnectionState.LISTEN;
    }
    taskExecutor.execute(acceptTask);
    return true;
  }

  /** Start connection as client with remote {@code device}. */
  public void connect(@NonNull BluetoothDevice device, @NonNull UUID serviceUuid) {
    // TODO(b/162537040): add retry logic for SPP
    logd(TAG, "Attempt connect to remote device.");
    // Cancel any thread attempting to make a connection
    if (connectTask != null) {
      connectTask.cancel();
    }

    // Start the task to connect with the given device
    connectTask =
        new ConnectTask(device, isSecure, serviceUuid, connectTaskCallback, taskCallbackExecutor);
    taskExecutor.execute(connectTask);
  }
  /**
   * Send data to remote connected bluetooth device.
   *
   * @param data the raw data that wait to be sent
   * @return {@code true} if the message successfully prepared to be sent.
   */
  public boolean write(@NonNull byte[] data, @NonNull PendingSentMessage pendingSentMessage) {
    synchronized (lock) {
      if (state != ConnectionState.CONNECTED) {
        loge(TAG, "Try to send data when device is disconnected");
        return false;
      }
    }

    byte[] dataReadyToSend = wrapWithArrayLength(data);
    if (dataReadyToSend == null) {
      loge(TAG, "Wrapping data with array length failed.");
      return false;
    }
    writeExecutor.execute(
        () -> {
          try {
            connectedSocket.getOutputStream().write(dataReadyToSend);
            pendingSentMessage.notifyMessageSent();
            logd(TAG, "Sent message to remote device with length: " + dataReadyToSend.length);
          } catch (IOException e) {
            loge(TAG, "Exception during write", e);
            synchronized (lock) {
              disconnectLocked();
            }
          }
        });
    return true;
  }

  /**
   * Wrap the raw byte array with array length.
   *
   * <p>Should be called every time when server wants to send a message to client.
   *
   * @param rawData Original data
   * @return The wrapped data or `null` if wrapping failed.
   */
  @Nullable
  @VisibleForTesting
  static byte[] wrapWithArrayLength(@NonNull byte[] rawData) {
    int length = rawData.length;
    byte[] lengthBytes =
        ByteBuffer.allocate(LENGTH_BYTES_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(length)
            .array();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      outputStream.write(lengthBytes);
      outputStream.write(rawData);
    } catch (IOException e) {
      loge(TAG, "Error wrap data with array length");
      return null;
    }
    return outputStream.toByteArray();
  }

  /** Disconnect from remote device and clear state. */
  @GuardedBy("lock")
  private void disconnectLocked() {
    logd(TAG, "cancel current connection: close connected socket.");
    if (connectedSocket == null) {
      logw(TAG, "Try to disconnect when there is no connected socket. Ignored.");
      return;
    }

    if (readMessageTask != null) {
      readMessageTask.cancel();
      readMessageTask = null;
    }

    try {
      connectedSocket.close();
    } catch (IOException e) {
      loge(TAG, "close() of connected socket failed", e);
    }
    connectedSocket = null;
    state = ConnectionState.DISCONNECTED;
    callbacks.invoke(callback -> callback.onRemoteDeviceDisconnected(device));
  }

  /** Cleans up the registered listeners and disconnect remote device. */
  public void cleanup() {
    if (acceptTask != null) {
      acceptTask.cancel();
      acceptTask = null;
    }

    if (connectTask != null) {
      connectTask.cancel();
      connectTask = null;
    }

    receivedListeners.clear();

    synchronized (lock) {
      disconnectLocked();
    }
  }

  /**
   * Start the ConnectedTask to begin and maintain a RFCOMM channel.
   *
   * @param socket The BluetoothSocket on which the connection was made
   * @param device The BluetoothDevice that has been connected
   */
  @GuardedBy("lock")
  private void startConnectionLocked(BluetoothSocket socket, BluetoothDevice device) {
    logd(TAG, "Get connected bluetooth socket, start listening to incoming messages.");

    this.device = device;
    connectedSocket = socket;

    state = ConnectionState.CONNECTED;
    callbacks.invoke(callback -> callback.onRemoteDeviceConnected(device));

    InputStream inputStream;
    try {
      inputStream = connectedSocket.getInputStream();
    } catch (IOException e) {
      loge(TAG, "Error getting input stream from socket, disconnecting");
      disconnectLocked();
      return;
    }
    readMessageTask =
        new ReadMessageTask(inputStream, readMessageTaskCallback, taskCallbackExecutor);
    // Start listening to incoming messages
    taskExecutor.execute(readMessageTask);
  }

  @VisibleForTesting
  final AcceptTask.OnTaskCompletedListener acceptTaskListener =
      new AcceptTask.OnTaskCompletedListener() {
        @Override
        public void onTaskCompleted(BluetoothSocket socket, boolean isSecure) {
          if (socket == null) {
            loge(TAG, "AcceptTask failed getting the socket");
            cleanup();
            return;
          }
          synchronized (lock) {
            switch (state) {
              case LISTEN:
              case CONNECTING:
                logd(TAG, "Starting connection with device " + socket.getRemoteDevice());
                startConnectionLocked(socket, socket.getRemoteDevice());
                break;
              case NONE:
                loge(TAG, "Try to start connection while in NONE state.");
                break;
              case CONNECTED:
                // Already connected. Terminate new socket.
                try {
                  socket.close();
                } catch (IOException e) {
                  loge(TAG, "Could not close unwanted socket", e);
                }
                break;
              case DISCONNECTED:
                loge(TAG, "Start connection while in DISCONNECTED state.");
                break;
            }
          }
        }
      };

  @VisibleForTesting
  final ReadMessageTask.Callback readMessageTaskCallback =
      new ReadMessageTask.Callback() {
        @Override
        public void onMessageReceived(byte[] message) {
          receivedListeners.invoke(listener -> listener.onMessageReceived(device, message));
        }

        @Override
        public void onMessageReadError() {
          loge(TAG, "Error read message from remote device, disconnecting.");
          synchronized (lock) {
            disconnectLocked();
          }
        }
      };

  @VisibleForTesting
  final ConnectTask.Callback connectTaskCallback =
      new ConnectTask.Callback() {
        @Override
        public void onConnectionSuccess(BluetoothSocket socket) {
          // Reset connectTask when the task is completed successfully.
          connectTask = null;
          synchronized (lock) {
            logd(
                TAG,
                "onConnectionSucceeded: Starting connection with device "
                    + socket.getRemoteDevice());
            startConnectionLocked(socket, socket.getRemoteDevice());
          }
        }

        @Override
        public void onConnectionAttemptFailed() {
          loge(TAG, "ConnectTask failed");
          cleanup();
        }
      };

  /** Interface to be notified of various events within the {@link SppManager}. */
  public interface ConnectionCallback {

    /**
     * Triggered when a bluetooth device connected.
     *
     * @param device Remote device that connected on Spp.
     */
    void onRemoteDeviceConnected(@NonNull BluetoothDevice device);

    /**
     * Triggered when a bluetooth device disconnected.
     *
     * @param device Remote device that disconnected on Spp.
     */
    void onRemoteDeviceDisconnected(@NonNull BluetoothDevice device);
  }

  /** An interface for classes that wish to be notified of incoming messages. */
  public interface OnMessageReceivedListener {
    /**
     * Triggered when this SppManager receives a write request from a remote device.
     *
     * @param device The bluetooth device that sending the message.
     * @param value The value that was written.
     */
    void onMessageReceived(@NonNull BluetoothDevice device, @NonNull byte[] value);
  }
}
