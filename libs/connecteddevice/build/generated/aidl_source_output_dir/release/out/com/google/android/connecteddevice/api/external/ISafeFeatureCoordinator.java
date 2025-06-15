/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api.external;
/**
 * Coordinator between external features and connected devices.
 * 
 * To maintain backward and forward compatibility, this class should not pass
 * customized parcelables. Instead please use supported primitive types
 * mentioned in the Android official doc.
 * See <a href="https://developer.android.com/guide/components/aidl">Android AIDL</a>
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 10
 */
// TODO Link to the internal documentation on rules when modify
// this class.
public interface ISafeFeatureCoordinator extends android.os.IInterface
{
  /** Default implementation for ISafeFeatureCoordinator. */
  public static class Default implements com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
  {
    /**
     * Returns list of ids of currently connected devices that belong to the
     * current user.
     */
    @Override public java.util.List<java.lang.String> getConnectedDevices() throws android.os.RemoteException
    {
      return null;
    }
    /**
     * Registers a callback for connection events for only the driver's devices.
     * 
     * @param callback {@link ISafeConnectionCallback} to register.
     */
    @Override public void registerConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters a connection callback.
     * 
     * @param callback {@link ISafeConnectionCallback} to unregister.
     */
    @Override public void unregisterConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Registers a callback for a specific connectedDevice and recipient.
     * 
     * Duplicate registration with the same [recipientId] will block the
     * recipient and prevent it from receiving callbacks.
     * 
     * @param deviceId {@link String} to register triggers on.
     * @param recipientId {@link ParcelUuid} to register as recipient of.
     * @param callback {@link ISafeDeviceCallback} to register.
     */
    @Override public void registerDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Unregisters callback from connectedDevice events. The request will be
     * ignored if there is no matching {@link ISafeDeviceCallback} registered
     * with the Companion platform
     * 
     * @param deviceId {@link String} that callback was registered on.
     * @param recipientId {@link ParcelUuid} callback was registered under.
     * @param callback {@link ISafeDeviceCallback} to unregister.
     */
    @Override public void unregisterDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /**
     * Sends a message to a connected device.
     * 
     * @param deviceId {@link String} to send the message to.
     * @param message byte array message proto to send.
     */
    @Override public boolean sendMessage(java.lang.String deviceId, byte[] message) throws android.os.RemoteException
    {
      return false;
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
     * Unregisters listener from the log request. The request will be
     * ignored if there is no matching {@link ISafeOnLogRequestedListener}
     * registered with the Companion platform
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
    /**
     * Retrieves all associated devices's ids for current user.
     * 
     * @param listener {@link ISafeOnAssociatedDevicesRetrievedListener} that
     * will be notified when the associated devices are retrieved.
     */
    @Override public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator))) {
        return ((com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator)iin);
      }
      return new com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator.Stub.Proxy(obj);
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
        case TRANSACTION_getConnectedDevices:
        {
          java.util.List<java.lang.String> _result = this.getConnectedDevices();
          reply.writeNoException();
          reply.writeStringList(_result);
          break;
        }
        case TRANSACTION_registerConnectionCallback:
        {
          com.google.android.connecteddevice.api.external.ISafeConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.external.ISafeConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.registerConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterConnectionCallback:
        {
          com.google.android.connecteddevice.api.external.ISafeConnectionCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.external.ISafeConnectionCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterConnectionCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerDeviceCallback:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.api.external.ISafeDeviceCallback _arg2;
          _arg2 = com.google.android.connecteddevice.api.external.ISafeDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.registerDeviceCallback(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDeviceCallback:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.api.external.ISafeDeviceCallback _arg2;
          _arg2 = com.google.android.connecteddevice.api.external.ISafeDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterDeviceCallback(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_sendMessage:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          boolean _result = this.sendMessage(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
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
        case TRANSACTION_retrieveAssociatedDevices:
        {
          com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener.Stub.asInterface(data.readStrongBinder());
          this.retrieveAssociatedDevices(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator
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
       * Returns list of ids of currently connected devices that belong to the
       * current user.
       */
      @Override public java.util.List<java.lang.String> getConnectedDevices() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getConnectedDevices, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createStringArrayList();
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
       * @param callback {@link ISafeConnectionCallback} to register.
       */
      @Override public void registerConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerConnectionCallback, _data, _reply, 0);
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
       * @param callback {@link ISafeConnectionCallback} to unregister.
       */
      @Override public void unregisterConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException
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
       * @param deviceId {@link String} to register triggers on.
       * @param recipientId {@link ParcelUuid} to register as recipient of.
       * @param callback {@link ISafeDeviceCallback} to register.
       */
      @Override public void registerDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
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
       * Unregisters callback from connectedDevice events. The request will be
       * ignored if there is no matching {@link ISafeDeviceCallback} registered
       * with the Companion platform
       * 
       * @param deviceId {@link String} that callback was registered on.
       * @param recipientId {@link ParcelUuid} callback was registered under.
       * @param callback {@link ISafeDeviceCallback} to unregister.
       */
      @Override public void unregisterDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
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
       * @param deviceId {@link String} to send the message to.
       * @param message byte array message proto to send.
       */
      @Override public boolean sendMessage(java.lang.String deviceId, byte[] message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          _data.writeByteArray(message);
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
       * Unregisters listener from the log request. The request will be
       * ignored if there is no matching {@link ISafeOnLogRequestedListener}
       * registered with the Companion platform
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
      /**
       * Retrieves all associated devices's ids for current user.
       * 
       * @param listener {@link ISafeOnAssociatedDevicesRetrievedListener} that
       * will be notified when the associated devices are retrieved.
       */
      @Override public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException
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
    }
    static final int TRANSACTION_getConnectedDevices = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_registerConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_unregisterConnectionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_registerDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_unregisterDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_sendMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_registerOnLogRequestedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_unregisterOnLogRequestedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_processLogRecords = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_retrieveAssociatedDevices = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.external.ISafeFeatureCoordinator";
  /**
   * Returns list of ids of currently connected devices that belong to the
   * current user.
   */
  public java.util.List<java.lang.String> getConnectedDevices() throws android.os.RemoteException;
  /**
   * Registers a callback for connection events for only the driver's devices.
   * 
   * @param callback {@link ISafeConnectionCallback} to register.
   */
  public void registerConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Unregisters a connection callback.
   * 
   * @param callback {@link ISafeConnectionCallback} to unregister.
   */
  public void unregisterConnectionCallback(com.google.android.connecteddevice.api.external.ISafeConnectionCallback callback) throws android.os.RemoteException;
  /**
   * Registers a callback for a specific connectedDevice and recipient.
   * 
   * Duplicate registration with the same [recipientId] will block the
   * recipient and prevent it from receiving callbacks.
   * 
   * @param deviceId {@link String} to register triggers on.
   * @param recipientId {@link ParcelUuid} to register as recipient of.
   * @param callback {@link ISafeDeviceCallback} to register.
   */
  public void registerDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException;
  /**
   * Unregisters callback from connectedDevice events. The request will be
   * ignored if there is no matching {@link ISafeDeviceCallback} registered
   * with the Companion platform
   * 
   * @param deviceId {@link String} that callback was registered on.
   * @param recipientId {@link ParcelUuid} callback was registered under.
   * @param callback {@link ISafeDeviceCallback} to unregister.
   */
  public void unregisterDeviceCallback(java.lang.String deviceId, android.os.ParcelUuid recipientId, com.google.android.connecteddevice.api.external.ISafeDeviceCallback callback) throws android.os.RemoteException;
  /**
   * Sends a message to a connected device.
   * 
   * @param deviceId {@link String} to send the message to.
   * @param message byte array message proto to send.
   */
  public boolean sendMessage(java.lang.String deviceId, byte[] message) throws android.os.RemoteException;
  /**
   * Registers listener for the log request with the given logger identifier.
   * 
   * @param listener {@link ISafeOnLogRequestedListener} to register.
   */
  public void registerOnLogRequestedListener(int loggerId, com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener listener) throws android.os.RemoteException;
  /**
   * Unregisters listener from the log request. The request will be
   * ignored if there is no matching {@link ISafeOnLogRequestedListener}
   * registered with the Companion platform
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
  /**
   * Retrieves all associated devices's ids for current user.
   * 
   * @param listener {@link ISafeOnAssociatedDevicesRetrievedListener} that
   * will be notified when the associated devices are retrieved.
   */
  public void retrieveAssociatedDevices(com.google.android.connecteddevice.api.external.ISafeOnAssociatedDevicesRetrievedListener listener) throws android.os.RemoteException;
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
  }
}
