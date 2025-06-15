/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Coordinator between features and connected devices.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 28
 */
public interface IFeatureCoordinator extends android.os.IInterface
{
  /** Default implementation for IFeatureCoordinator. */
  public static class Default implements com.google.android.connecteddevice.api.IFeatureCoordinator
  {
    /**
     * Returns {@link List<ConnectedDevice>} of devices currently connected that
     * belong to the current driver.
     */
    @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForDriver() throws android.os.RemoteException
    {
      return null;
    }
    /**
     * Returns {@link List<ConnectedDevice>} of devices currently connected that
     * belong to any of the passengers.
     */
    @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForPassengers() throws android.os.RemoteException
    {
      return null;
    }
    /** Returns {@link List<ConnectedDevice>} of all devices currently connected. */
    @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getAllConnectedDevices() throws android.os.RemoteException
    {
      return null;
    }
    /**
     * Registers a callback for connection events for only the driver's devices.
     * 
     * @param callback {@link IConnectionCallback} to register.
     */
    @Override public void registerDriverConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Registers a callback for connection events for only passengers' devices.
     * 
     * @param callback {@link IConnectionCallback} to register.
     */
    @Override public void registerPassengerConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Registers a callback for connection events for all devices.
     * 
     * @param callback {@link IConnectionCallback} to register.
     */
    @Override public void registerAllConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters a connection callback.
     * 
     * @param callback {@link IConnectionCallback} to unregister.
     */
    @Override public void unregisterConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Registers a callback for a specific connectedDevice and recipient.
     * 
     * Duplicate registration with the same [recipientId] will block the
     * recipient and prevent it from receiving callbacks.
     * 
     * @param connectedDevice {@link ConnectedDevice} to register triggers on.
     * @param recipientId {@link ParcelUuid} to register as recipient of.
     * @param callback {@link IDeviceCallback} to register.
     */
    @Override public void registerDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters callback from connectedDevice events.
     * 
     * @param connectedDevice {@link ConnectedDevice} callback was registered on.
     * @param recipientId {@link ParcelUuid} callback was registered under.
     * @param callback {@link IDeviceCallback} to unregister.
     */
    @Override public void unregisterDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Sends a message to a connected device.
     * 
     * @param connectedDevice {@link ConnectedDevice} to send the message to.
     * @param message Message to send.
     */
    @Override public boolean sendMessage(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException
    {
      return false;
    }
    /**
     * Registers a callback for associated device related events.
     * 
     * @param callback {@link IDeviceAssociationCallback} to register.
     */
    @Override public void registerDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters a device association callback.
     * 
     * @param callback {@link IDeviceAssociationCallback} to unregister.
     */
    @Override public void unregisterDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Registers listener for the log request with the given logger identifier.
     * 
     * @param listener {@link ISafeOnLogRequestedListener} to register.
     */
    @Override public void registerOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters listener from the log request.
     * 
     * @param listener {@link ISafeOnLogRequestedListener} to unregister.
     */
    @Override public void unregisterOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException
    {
    }
    /**
     * Processes log records in the logger with given id so it can be combined
     * with log records from other loggers.
     * 
     * @param loggerId of the logger.
     * @param logRecords to process.
     */
    @Override public void processLogRecords(int loggerId, byte[] logRecords) throws android.os.RemoteException
    {
    }
    /** Starts the association with a new device. */
    @Override public void startAssociation(com.google.android.connecteddevice.api.IAssociationCallback callback) throws android.os.RemoteException
    {
    }
    /** Stop the association process if it is still in progress. */
    @Override public void stopAssociation() throws android.os.RemoteException
    {
    }
    /**
     * Retrieves all associated devices for all users.
     * 
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    @Override public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
    {
    }
    /**
     * Retrieves associated devices belonging to the driver (the current user).
     * 
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    @Override public void retrieveAssociatedDevicesForDriver(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
    {
    }
    /**
     * Retrieves associated devices belonging to all of the passengers.
     * 
     * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
     * be notified when the associated devices are retrieved.
     */
    @Override public void retrieveAssociatedDevicesForPassengers(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
    {
    }
    /** Confirms the paring code. */
    @Override public void acceptVerification() throws android.os.RemoteException
    {
    }
    /** Removes the associated device of the given identifier for the active user. */
    @Override public void removeAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /** Enables connection on the associated device with the given identifier. */
    @Override public void enableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /** Disables connection on the associated device with the given identifier. */
    @Override public void disableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /**
     * Starts the association with a new device.
     * 
     * @param callback {@link IAssociationCallback} that will be notified for assocaition events.
     * @param identifier {@link ParcelUuid} to identify the association.
     */
    @Override public void startAssociationWithIdentifier(com.google.android.connecteddevice.api.IAssociationCallback callback, android.os.ParcelUuid identifier) throws android.os.RemoteException
    {
    }
    /** Claim an associated device to belong to the current user. */
    @Override public void claimAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /** Remove the claim on the identified associated device. */
    @Override public void removeAssociatedDeviceClaim(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /**
     * Returns the support status of a feature on the phone side.
     * 
     * Returns:
     * - a positive value if the feature is supported on the phone side;
     * - a negative value if the feature is NOT supported on the phone side;
     * - 0 if the status is unknown.
     */
    @Override public int isFeatureSupportedCached(java.lang.String deviceId, java.lang.String featureId) throws android.os.RemoteException
    {
      return 0;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IFeatureCoordinator
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IFeatureCoordinator interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IFeatureCoordinator asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IFeatureCoordinator))) {
        return ((com.google.android.connecteddevice.api.IFeatureCoordinator)iin);
      }
      return new com.google.android.connecteddevice.api.IFeatureCoordinator.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_getConnectedDevicesForDriver:
        {
          java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result = this.getConnectedDevicesForDriver();
          reply.writeNoException();
          _Parcel.writeTypedList(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getConnectedDevicesForPassengers:
        {
          java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result = this.getConnectedDevicesForPassengers();
          reply.writeNoException();
          _Parcel.writeTypedList(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getAllConnectedDevices:
        {
          java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result = this.getAllConnectedDevices();
          reply.writeNoException();
          _Parcel.writeTypedList(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_registerDriverConnectionCallback:
        {
          com.google.android.connecteddevice.api.IConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.registerDriverConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerPassengerConnectionCallback:
        {
          com.google.android.connecteddevice.api.IConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.registerPassengerConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerAllConnectionCallback:
        {
          com.google.android.connecteddevice.api.IConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.registerAllConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterConnectionCallback:
        {
          com.google.android.connecteddevice.api.IConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerDeviceCallback:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.api.IDeviceCallback _arg2;
          _arg2 = com.google.android.connecteddevice.api.IDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.registerDeviceCallback(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDeviceCallback:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.api.IDeviceCallback _arg2;
          _arg2 = com.google.android.connecteddevice.api.IDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterDeviceCallback(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_sendMessage:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          com.google.android.connecteddevice.model.DeviceMessage _arg1;
          _arg1 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.DeviceMessage.CREATOR);
          boolean _result = this.sendMessage(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_registerDeviceAssociationCallback:
        {
          com.google.android.connecteddevice.api.IDeviceAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IDeviceAssociationCallback.Stub.asInterface(data.readStrongBinder());
          this.registerDeviceAssociationCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDeviceAssociationCallback:
        {
          com.google.android.connecteddevice.api.IDeviceAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IDeviceAssociationCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterDeviceAssociationCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerOnLogRequestedListener:
        {
          int _arg0;
          _arg0 = data.readInt();
          com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener _arg1;
          _arg1 = com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener.Stub.asInterface(data.readStrongBinder());
          this.registerOnLogRequestedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterOnLogRequestedListener:
        {
          int _arg0;
          _arg0 = data.readInt();
          com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener _arg1;
          _arg1 = com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterOnLogRequestedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_processLogRecords:
        {
          int _arg0;
          _arg0 = data.readInt();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          this.processLogRecords(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_startAssociation:
        {
          com.google.android.connecteddevice.api.IAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IAssociationCallback.Stub.asInterface(data.readStrongBinder());
          this.startAssociation(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_stopAssociation:
        {
          this.stopAssociation();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_retrieveAssociatedDevices:
        {
          com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener.Stub.asInterface(data.readStrongBinder());
          this.retrieveAssociatedDevices(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_retrieveAssociatedDevicesForDriver:
        {
          com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener.Stub.asInterface(data.readStrongBinder());
          this.retrieveAssociatedDevicesForDriver(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_retrieveAssociatedDevicesForPassengers:
        {
          com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener.Stub.asInterface(data.readStrongBinder());
          this.retrieveAssociatedDevicesForPassengers(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_acceptVerification:
        {
          this.acceptVerification();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeAssociatedDevice:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.removeAssociatedDevice(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_enableAssociatedDeviceConnection:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.enableAssociatedDeviceConnection(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_disableAssociatedDeviceConnection:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.disableAssociatedDeviceConnection(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_startAssociationWithIdentifier:
        {
          com.google.android.connecteddevice.api.IAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IAssociationCallback.Stub.asInterface(data.readStrongBinder());
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          this.startAssociationWithIdentifier(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_claimAssociatedDevice:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.claimAssociatedDevice(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeAssociatedDeviceClaim:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.removeAssociatedDeviceClaim(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_isFeatureSupportedCached:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          int _result = this.isFeatureSupportedCached(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IFeatureCoordinator
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
       * Returns {@link List<ConnectedDevice>} of devices currently connected that
       * belong to the current driver.
       */
      @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForDriver() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getConnectedDevicesForDriver, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createTypedArrayList(com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Returns {@link List<ConnectedDevice>} of devices currently connected that
       * belong to any of the passengers.
       */
      @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForPassengers() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getConnectedDevicesForPassengers, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createTypedArrayList(com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Returns {@link List<ConnectedDevice>} of all devices currently connected. */
      @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getAllConnectedDevices() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getAllConnectedDevices, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createTypedArrayList(com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Registers a callback for connection events for only the driver's devices.
       * 
       * @param callback {@link IConnectionCallback} to register.
       */
      @Override public void registerDriverConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDriverConnectionCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Registers a callback for connection events for only passengers' devices.
       * 
       * @param callback {@link IConnectionCallback} to register.
       */
      @Override public void registerPassengerConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerPassengerConnectionCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Registers a callback for connection events for all devices.
       * 
       * @param callback {@link IConnectionCallback} to register.
       */
      @Override public void registerAllConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerAllConnectionCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Unregisters a connection callback.
       * 
       * @param callback {@link IConnectionCallback} to unregister.
       */
      @Override public void unregisterConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterConnectionCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Registers a callback for a specific connectedDevice and recipient.
       * 
       * Duplicate registration with the same [recipientId] will block the
       * recipient and prevent it from receiving callbacks.
       * 
       * @param connectedDevice {@link ConnectedDevice} to register triggers on.
       * @param recipientId {@link ParcelUuid} to register as recipient of.
       * @param callback {@link IDeviceCallback} to register.
       */
      @Override public void registerDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          _Parcel.writeTypedObject(_data, recipientId, 0);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Unregisters callback from connectedDevice events.
       * 
       * @param connectedDevice {@link ConnectedDevice} callback was registered on.
       * @param recipientId {@link ParcelUuid} callback was registered under.
       * @param callback {@link IDeviceCallback} to unregister.
       */
      @Override public void unregisterDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          _Parcel.writeTypedObject(_data, recipientId, 0);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Sends a message to a connected device.
       * 
       * @param connectedDevice {@link ConnectedDevice} to send the message to.
       * @param message Message to send.
       */
      @Override public boolean sendMessage(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          _Parcel.writeTypedObject(_data, message, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendMessage, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Registers a callback for associated device related events.
       * 
       * @param callback {@link IDeviceAssociationCallback} to register.
       */
      @Override public void registerDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDeviceAssociationCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Unregisters a device association callback.
       * 
       * @param callback {@link IDeviceAssociationCallback} to unregister.
       */
      @Override public void unregisterDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterDeviceAssociationCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Registers listener for the log request with the given logger identifier.
       * 
       * @param listener {@link ISafeOnLogRequestedListener} to register.
       */
      @Override public void registerOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(loggerId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerOnLogRequestedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Unregisters listener from the log request.
       * 
       * @param listener {@link ISafeOnLogRequestedListener} to unregister.
       */
      @Override public void unregisterOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(loggerId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterOnLogRequestedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Processes log records in the logger with given id so it can be combined
       * with log records from other loggers.
       * 
       * @param loggerId of the logger.
       * @param logRecords to process.
       */
      @Override public void processLogRecords(int loggerId, byte[] logRecords) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(loggerId);
          _data.writeByteArray(logRecords);
          boolean _status = mRemote.transact(Stub.TRANSACTION_processLogRecords, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Starts the association with a new device. */
      @Override public void startAssociation(com.google.android.connecteddevice.api.IAssociationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startAssociation, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Stop the association process if it is still in progress. */
      @Override public void stopAssociation() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopAssociation, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Retrieves all associated devices for all users.
       * 
       * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
       * be notified when the associated devices are retrieved.
       */
      @Override public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_retrieveAssociatedDevices, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Retrieves associated devices belonging to the driver (the current user).
       * 
       * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
       * be notified when the associated devices are retrieved.
       */
      @Override public void retrieveAssociatedDevicesForDriver(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_retrieveAssociatedDevicesForDriver, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Retrieves associated devices belonging to all of the passengers.
       * 
       * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
       * be notified when the associated devices are retrieved.
       */
      @Override public void retrieveAssociatedDevicesForPassengers(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_retrieveAssociatedDevicesForPassengers, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Confirms the paring code. */
      @Override public void acceptVerification() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_acceptVerification, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Removes the associated device of the given identifier for the active user. */
      @Override public void removeAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeAssociatedDevice, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Enables connection on the associated device with the given identifier. */
      @Override public void enableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_enableAssociatedDeviceConnection, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Disables connection on the associated device with the given identifier. */
      @Override public void disableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_disableAssociatedDeviceConnection, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Starts the association with a new device.
       * 
       * @param callback {@link IAssociationCallback} that will be notified for assocaition events.
       * @param identifier {@link ParcelUuid} to identify the association.
       */
      @Override public void startAssociationWithIdentifier(com.google.android.connecteddevice.api.IAssociationCallback callback, android.os.ParcelUuid identifier) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          _Parcel.writeTypedObject(_data, identifier, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startAssociationWithIdentifier, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Claim an associated device to belong to the current user. */
      @Override public void claimAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_claimAssociatedDevice, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove the claim on the identified associated device. */
      @Override public void removeAssociatedDeviceClaim(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeAssociatedDeviceClaim, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Returns the support status of a feature on the phone side.
       * 
       * Returns:
       * - a positive value if the feature is supported on the phone side;
       * - a negative value if the feature is NOT supported on the phone side;
       * - 0 if the status is unknown.
       */
      @Override public int isFeatureSupportedCached(java.lang.String deviceId, java.lang.String featureId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          _data.writeString(featureId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isFeatureSupportedCached, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_getConnectedDevicesForDriver = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_getConnectedDevicesForPassengers = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getAllConnectedDevices = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_registerDriverConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_registerPassengerConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_registerAllConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_unregisterConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_registerDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_unregisterDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_sendMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_registerDeviceAssociationCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_unregisterDeviceAssociationCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_registerOnLogRequestedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_unregisterOnLogRequestedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_processLogRecords = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_startAssociation = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_stopAssociation = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_retrieveAssociatedDevices = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_retrieveAssociatedDevicesForDriver = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_retrieveAssociatedDevicesForPassengers = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_acceptVerification = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
    static final int TRANSACTION_removeAssociatedDevice = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
    static final int TRANSACTION_enableAssociatedDeviceConnection = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
    static final int TRANSACTION_disableAssociatedDeviceConnection = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
    static final int TRANSACTION_startAssociationWithIdentifier = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
    static final int TRANSACTION_claimAssociatedDevice = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
    static final int TRANSACTION_removeAssociatedDeviceClaim = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
    static final int TRANSACTION_isFeatureSupportedCached = (android.os.IBinder.FIRST_CALL_TRANSACTION + 27);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IFeatureCoordinator";
  /**
   * Returns {@link List<ConnectedDevice>} of devices currently connected that
   * belong to the current driver.
   */
  public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForDriver() throws android.os.RemoteException;
  /**
   * Returns {@link List<ConnectedDevice>} of devices currently connected that
   * belong to any of the passengers.
   */
  public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getConnectedDevicesForPassengers() throws android.os.RemoteException;
  /** Returns {@link List<ConnectedDevice>} of all devices currently connected. */
  public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getAllConnectedDevices() throws android.os.RemoteException;
  /**
   * Registers a callback for connection events for only the driver's devices.
   * 
   * @param callback {@link IConnectionCallback} to register.
   */
  public void registerDriverConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Registers a callback for connection events for only passengers' devices.
   * 
   * @param callback {@link IConnectionCallback} to register.
   */
  public void registerPassengerConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Registers a callback for connection events for all devices.
   * 
   * @param callback {@link IConnectionCallback} to register.
   */
  public void registerAllConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Unregisters a connection callback.
   * 
   * @param callback {@link IConnectionCallback} to unregister.
   */
  public void unregisterConnectionCallback(com.google.android.connecteddevice.api.IConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Registers a callback for a specific connectedDevice and recipient.
   * 
   * Duplicate registration with the same [recipientId] will block the
   * recipient and prevent it from receiving callbacks.
   * 
   * @param connectedDevice {@link ConnectedDevice} to register triggers on.
   * @param recipientId {@link ParcelUuid} to register as recipient of.
   * @param callback {@link IDeviceCallback} to register.
   */
  public void registerDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException;
  /**
   * Unregisters callback from connectedDevice events.
   * 
   * @param connectedDevice {@link ConnectedDevice} callback was registered on.
   * @param recipientId {@link ParcelUuid} callback was registered under.
   * @param callback {@link IDeviceCallback} to unregister.
   */
  public void unregisterDeviceCallback(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.IDeviceCallback callback) throws android.os.RemoteException;
  /**
   * Sends a message to a connected device.
   * 
   * @param connectedDevice {@link ConnectedDevice} to send the message to.
   * @param message Message to send.
   */
  public boolean sendMessage(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException;
  /**
   * Registers a callback for associated device related events.
   * 
   * @param callback {@link IDeviceAssociationCallback} to register.
   */
  public void registerDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException;
  /**
   * Unregisters a device association callback.
   * 
   * @param callback {@link IDeviceAssociationCallback} to unregister.
   */
  public void unregisterDeviceAssociationCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException;
  /**
   * Registers listener for the log request with the given logger identifier.
   * 
   * @param listener {@link ISafeOnLogRequestedListener} to register.
   */
  public void registerOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException;
  /**
   * Unregisters listener from the log request.
   * 
   * @param listener {@link ISafeOnLogRequestedListener} to unregister.
   */
  public void unregisterOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException;
  /**
   * Processes log records in the logger with given id so it can be combined
   * with log records from other loggers.
   * 
   * @param loggerId of the logger.
   * @param logRecords to process.
   */
  public void processLogRecords(int loggerId, byte[] logRecords) throws android.os.RemoteException;
  /** Starts the association with a new device. */
  public void startAssociation(com.google.android.connecteddevice.api.IAssociationCallback callback) throws android.os.RemoteException;
  /** Stop the association process if it is still in progress. */
  public void stopAssociation() throws android.os.RemoteException;
  /**
   * Retrieves all associated devices for all users.
   * 
   * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
   * be notified when the associated devices are retrieved.
   */
  public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException;
  /**
   * Retrieves associated devices belonging to the driver (the current user).
   * 
   * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
   * be notified when the associated devices are retrieved.
   */
  public void retrieveAssociatedDevicesForDriver(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException;
  /**
   * Retrieves associated devices belonging to all of the passengers.
   * 
   * @param listener {@link IOnAssociatedDevicesRetrievedListener} that will
   * be notified when the associated devices are retrieved.
   */
  public void retrieveAssociatedDevicesForPassengers(com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException;
  /** Confirms the paring code. */
  public void acceptVerification() throws android.os.RemoteException;
  /** Removes the associated device of the given identifier for the active user. */
  public void removeAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException;
  /** Enables connection on the associated device with the given identifier. */
  public void enableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException;
  /** Disables connection on the associated device with the given identifier. */
  public void disableAssociatedDeviceConnection(java.lang.String deviceId) throws android.os.RemoteException;
  /**
   * Starts the association with a new device.
   * 
   * @param callback {@link IAssociationCallback} that will be notified for assocaition events.
   * @param identifier {@link ParcelUuid} to identify the association.
   */
  public void startAssociationWithIdentifier(com.google.android.connecteddevice.api.IAssociationCallback callback, android.os.ParcelUuid identifier) throws android.os.RemoteException;
  /** Claim an associated device to belong to the current user. */
  public void claimAssociatedDevice(java.lang.String deviceId) throws android.os.RemoteException;
  /** Remove the claim on the identified associated device. */
  public void removeAssociatedDeviceClaim(java.lang.String deviceId) throws android.os.RemoteException;
  /**
   * Returns the support status of a feature on the phone side.
   * 
   * Returns:
   * - a positive value if the feature is supported on the phone side;
   * - a negative value if the feature is NOT supported on the phone side;
   * - 0 if the status is unknown.
   */
  public int isFeatureSupportedCached(java.lang.String deviceId, java.lang.String featureId) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedList(
        android.os.Parcel parcel, java.util.List<T> value, int parcelableFlags) {
      if (value == null) {
        parcel.writeInt(-1);
      } else {
        int N = value.size();
        int i = 0;
        parcel.writeInt(N);
        while (i < N) {
    writeTypedObject(parcel, value.get(i), parcelableFlags);
          i++;
        }
      }
    }
  }
}
