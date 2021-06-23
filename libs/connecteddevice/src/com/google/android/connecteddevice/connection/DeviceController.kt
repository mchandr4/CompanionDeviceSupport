package com.google.android.connecteddevice.connection

import com.google.android.connecteddevice.model.DeviceMessage
import java.util.UUID
import java.util.concurrent.Executor

/** Controller in charge of handling device connections. */
interface DeviceController {
  /** Disconnect all devices and reset state. */
  fun reset()

  /** Start to connect to associated devices  */
  fun initiateConnectionToDevice(deviceId: UUID)

  /** Start the association with a new device  */
  fun startAssociation(nameForAssociation: String, callback: AssociationCallback)

  /** Notify that the user has accepted a pairing code. */
  fun notifyVerificationCodeAccepted()

  /**
   * Send the [message] to a connected device with id [deviceId] and returns `true` if the send was
   * successful.
   *
   * If `false` is returned, then it could mean that the given device was not currently connected
   * or is waiting for its secure channel to be set up. [isReadyToSendMessage] should return
   * `true` for the given `deviceId` before calling this method with the same `deviceId`.
   */
  fun sendMessage(deviceId: UUID, message: DeviceMessage): Boolean

  /**
   * Returns `true` if a secure channel has been established for device with id [deviceId]. A value
   * of `true` means that [sendMessage] can be called for the device.
   */
  fun isReadyToSendMessage(deviceId: UUID): Boolean

  /** Disconnect the provided device from this controller.  */
  fun disconnectDevice(deviceId: UUID)

  /** Register a [Callback] to be notified on the [Executor].  */
  fun registerCallback(callback: Callback, executor: Executor)

  /**
   * Unregister a callback.
   *
   * @param callback The [Callback] to unregister.
   */
  fun unregisterCallback(callback: Callback)

  /** Callback for triggered events from [MultiProtocolDeviceController].  */
  interface Callback {
    /**
     * Invoked when the device with the given [deviceId] has connected.
     *
     * @param deviceId The unique identifier of the connected device.
     */
    fun onDeviceConnected(deviceId: String)

    /**
     * Invoked when the device with the given [deviceId] has disconnected.
     *
     * @param deviceId The unique identifier of the device which has been disconnected.
     */
    fun onDeviceDisconnected(deviceId: String)

    /**
     * Triggered when the device with the given [deviceId] has established encryption for secure.
     * communication.
     *
     * @param deviceId Id of device that has established encryption.
     */
    fun onSecureChannelEstablished(deviceId: String)

    /**
     * Triggered when a new message is received from the device with the given [deviceId].
     *
     * @param deviceId Id of the device that sent the message.
     * @param message The message that was received.
     */
    fun onMessageReceived(deviceId: String, message: DeviceMessage)

    /**
     * Triggered if an error occurred when establishing the secure channel.
     *
     * @param deviceId Id of the device that experienced the error.
     */
    fun onSecureChannelError(deviceId: String)
  }
}
