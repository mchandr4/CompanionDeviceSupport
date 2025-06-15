/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
public interface IConnectionProtocol extends android.os.IInterface
{
  /** Default implementation for IConnectionProtocol. */
  public static class Default implements com.google.android.connecteddevice.transport.IConnectionProtocol
  {
    /**
     * Returns true if challenge exchange is required to verify the remote device for establishing a
     * secure channel over this protocol.
     */
    @Override public boolean isDeviceVerificationRequired() throws android.os.RemoteException
    {
      return false;
    }
    /** Begin the discovery process with the name and identifier for a new device to associate with. */
    @Override public void startAssociationDiscovery(java.lang.String name, android.os.ParcelUuid identifier, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException
    {
    }
    /** Begin the discovery process for a device that will respond to the supplied id and challenge. */
    @Override public void startConnectionDiscovery(android.os.ParcelUuid id, com.google.android.connecteddevice.transport.ConnectChallenge challenge, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException
    {
    }
    /** Stop an ongoing association discovery. */
    @Override public void stopAssociationDiscovery() throws android.os.RemoteException
    {
    }
    /** Stop an ongoing connection discovery for the provided device. */
    @Override public void stopConnectionDiscovery(android.os.ParcelUuid id) throws android.os.RemoteException
    {
    }
    /** Send data to a device. */
    @Override public void sendData(java.lang.String protocolId, byte[] data, com.google.android.connecteddevice.transport.IDataSendCallback callback) throws android.os.RemoteException
    {
    }
    /** Disconnect a specific device. */
    @Override public void disconnectDevice(java.lang.String protocolId) throws android.os.RemoteException
    {
    }
    /**
     * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
     * neutral state.
     */
    @Override public void reset() throws android.os.RemoteException
    {
    }
    /**
     * Returns the maximum number of bytes that can be written in a single message for the device
     * matching the protocolId.
     */
    @Override public int getMaxWriteSize(java.lang.String protocolId) throws android.os.RemoteException
    {
      return 0;
    }
    /** Register a listener to be notified when data has been received on the specified  device. */
    @Override public void registerDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException
    {
    }
    /** Register a listener to be notified when device has disconnected. */
    @Override public void registerDeviceDisconnectedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException
    {
    }
    /**
     * Register a listener to be notified when the specified device has negotiated a new maximum
     * data size.
     */
    @Override public void registerDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException
    {
    }
    /** Unregister a previously registered listener. */
    @Override public void unregisterDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException
    {
    }
    /** Unregister a previously registered listener. */
    @Override public void unregisterDeviceDisconnectListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException
    {
    }
    /** Unregister a previously registered listener. */
    @Override public void unregisterDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException
    {
    }
    /** Removes registered listeners for the specified device. */
    @Override public void removeListeners(java.lang.String protocolId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IConnectionProtocol
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IConnectionProtocol interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IConnectionProtocol asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IConnectionProtocol))) {
        return ((com.google.android.connecteddevice.transport.IConnectionProtocol)iin);
      }
      return new com.google.android.connecteddevice.transport.IConnectionProtocol.Stub.Proxy(obj);
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
        case TRANSACTION_isDeviceVerificationRequired:
        {
          boolean _result = this.isDeviceVerificationRequired();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_startAssociationDiscovery:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.ParcelUuid _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.transport.IDiscoveryCallback _arg2;
          _arg2 = com.google.android.connecteddevice.transport.IDiscoveryCallback.Stub.asInterface(data.readStrongBinder());
          this.startAssociationDiscovery(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_startConnectionDiscovery:
        {
          android.os.ParcelUuid _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          com.google.android.connecteddevice.transport.ConnectChallenge _arg1;
          _arg1 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.transport.ConnectChallenge.CREATOR);
          com.google.android.connecteddevice.transport.IDiscoveryCallback _arg2;
          _arg2 = com.google.android.connecteddevice.transport.IDiscoveryCallback.Stub.asInterface(data.readStrongBinder());
          this.startConnectionDiscovery(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_stopAssociationDiscovery:
        {
          this.stopAssociationDiscovery();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_stopConnectionDiscovery:
        {
          android.os.ParcelUuid _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.os.ParcelUuid.CREATOR);
          this.stopConnectionDiscovery(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_sendData:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          com.google.android.connecteddevice.transport.IDataSendCallback _arg2;
          _arg2 = com.google.android.connecteddevice.transport.IDataSendCallback.Stub.asInterface(data.readStrongBinder());
          this.sendData(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_disconnectDevice:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.disconnectDevice(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_reset:
        {
          this.reset();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getMaxWriteSize:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _result = this.getMaxWriteSize(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_registerDataReceivedListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDataReceivedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDataReceivedListener.Stub.asInterface(data.readStrongBinder());
          this.registerDataReceivedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerDeviceDisconnectedListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDeviceDisconnectedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDeviceDisconnectedListener.Stub.asInterface(data.readStrongBinder());
          this.registerDeviceDisconnectedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerDeviceMaxDataSizeChangedListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener.Stub.asInterface(data.readStrongBinder());
          this.registerDeviceMaxDataSizeChangedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDataReceivedListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDataReceivedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDataReceivedListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterDataReceivedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDeviceDisconnectListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDeviceDisconnectedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDeviceDisconnectedListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterDeviceDisconnectListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterDeviceMaxDataSizeChangedListener:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener _arg1;
          _arg1 = com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterDeviceMaxDataSizeChangedListener(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeListeners:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.removeListeners(_arg0);
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
    private static class Proxy implements com.google.android.connecteddevice.transport.IConnectionProtocol
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
       * Returns true if challenge exchange is required to verify the remote device for establishing a
       * secure channel over this protocol.
       */
      @Override public boolean isDeviceVerificationRequired() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isDeviceVerificationRequired, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Begin the discovery process with the name and identifier for a new device to associate with. */
      @Override public void startAssociationDiscovery(java.lang.String name, android.os.ParcelUuid identifier, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _Parcel.writeTypedObject(_data, identifier, 0);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startAssociationDiscovery, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Begin the discovery process for a device that will respond to the supplied id and challenge. */
      @Override public void startConnectionDiscovery(android.os.ParcelUuid id, com.google.android.connecteddevice.transport.ConnectChallenge challenge, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, id, 0);
          _Parcel.writeTypedObject(_data, challenge, 0);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startConnectionDiscovery, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Stop an ongoing association discovery. */
      @Override public void stopAssociationDiscovery() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopAssociationDiscovery, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Stop an ongoing connection discovery for the provided device. */
      @Override public void stopConnectionDiscovery(android.os.ParcelUuid id) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, id, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopConnectionDiscovery, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Send data to a device. */
      @Override public void sendData(java.lang.String protocolId, byte[] data, com.google.android.connecteddevice.transport.IDataSendCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeByteArray(data);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendData, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Disconnect a specific device. */
      @Override public void disconnectDevice(java.lang.String protocolId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_disconnectDevice, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
       * neutral state.
       */
      @Override public void reset() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_reset, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Returns the maximum number of bytes that can be written in a single message for the device
       * matching the protocolId.
       */
      @Override public int getMaxWriteSize(java.lang.String protocolId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getMaxWriteSize, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Register a listener to be notified when data has been received on the specified  device. */
      @Override public void registerDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDataReceivedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Register a listener to be notified when device has disconnected. */
      @Override public void registerDeviceDisconnectedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDeviceDisconnectedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Register a listener to be notified when the specified device has negotiated a new maximum
       * data size.
       */
      @Override public void registerDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerDeviceMaxDataSizeChangedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Unregister a previously registered listener. */
      @Override public void unregisterDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterDataReceivedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Unregister a previously registered listener. */
      @Override public void unregisterDeviceDisconnectListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterDeviceDisconnectListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Unregister a previously registered listener. */
      @Override public void unregisterDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterDeviceMaxDataSizeChangedListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Removes registered listeners for the specified device. */
      @Override public void removeListeners(java.lang.String protocolId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeListeners, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_isDeviceVerificationRequired = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_startAssociationDiscovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_startConnectionDiscovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_stopAssociationDiscovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_stopConnectionDiscovery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_sendData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_disconnectDevice = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_reset = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_getMaxWriteSize = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_registerDataReceivedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_registerDeviceDisconnectedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_registerDeviceMaxDataSizeChangedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_unregisterDataReceivedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_unregisterDeviceDisconnectListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_unregisterDeviceMaxDataSizeChangedListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_removeListeners = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IConnectionProtocol";
  /**
   * Returns true if challenge exchange is required to verify the remote device for establishing a
   * secure channel over this protocol.
   */
  public boolean isDeviceVerificationRequired() throws android.os.RemoteException;
  /** Begin the discovery process with the name and identifier for a new device to associate with. */
  public void startAssociationDiscovery(java.lang.String name, android.os.ParcelUuid identifier, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException;
  /** Begin the discovery process for a device that will respond to the supplied id and challenge. */
  public void startConnectionDiscovery(android.os.ParcelUuid id, com.google.android.connecteddevice.transport.ConnectChallenge challenge, com.google.android.connecteddevice.transport.IDiscoveryCallback callback) throws android.os.RemoteException;
  /** Stop an ongoing association discovery. */
  public void stopAssociationDiscovery() throws android.os.RemoteException;
  /** Stop an ongoing connection discovery for the provided device. */
  public void stopConnectionDiscovery(android.os.ParcelUuid id) throws android.os.RemoteException;
  /** Send data to a device. */
  public void sendData(java.lang.String protocolId, byte[] data, com.google.android.connecteddevice.transport.IDataSendCallback callback) throws android.os.RemoteException;
  /** Disconnect a specific device. */
  public void disconnectDevice(java.lang.String protocolId) throws android.os.RemoteException;
  /**
   * Disconnect all active connections, cancel any discoveries in progress, and clean up to a
   * neutral state.
   */
  public void reset() throws android.os.RemoteException;
  /**
   * Returns the maximum number of bytes that can be written in a single message for the device
   * matching the protocolId.
   */
  public int getMaxWriteSize(java.lang.String protocolId) throws android.os.RemoteException;
  /** Register a listener to be notified when data has been received on the specified  device. */
  public void registerDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException;
  /** Register a listener to be notified when device has disconnected. */
  public void registerDeviceDisconnectedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException;
  /**
   * Register a listener to be notified when the specified device has negotiated a new maximum
   * data size.
   */
  public void registerDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException;
  /** Unregister a previously registered listener. */
  public void unregisterDataReceivedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDataReceivedListener listener) throws android.os.RemoteException;
  /** Unregister a previously registered listener. */
  public void unregisterDeviceDisconnectListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceDisconnectedListener listener) throws android.os.RemoteException;
  /** Unregister a previously registered listener. */
  public void unregisterDeviceMaxDataSizeChangedListener(java.lang.String protocolId, com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener listener) throws android.os.RemoteException;
  /** Removes registered listeners for the specified device. */
  public void removeListeners(java.lang.String protocolId) throws android.os.RemoteException;
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
