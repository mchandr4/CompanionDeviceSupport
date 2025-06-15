/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Triggered companionDevice events for a connected companionDevice.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 3
 */
public interface IDeviceCallback extends android.os.IInterface
{
  /** Default implementation for IDeviceCallback. */
  public static class Default implements com.google.android.connecteddevice.api.IDeviceCallback
  {
    /**
     * Triggered when secure channel has been established on a companionDevice. Encrypted messaging
     * now available.
     */
    @Override public void onSecureChannelEstablished(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
    {
    }
    /** Triggered when a new message is received from a connectedDevice. */
    @Override public void onMessageReceived(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException
    {
    }
    /** Triggered when an error has occurred for a connectedDevice. */
    @Override public void onDeviceError(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, int error) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IDeviceCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IDeviceCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IDeviceCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IDeviceCallback))) {
        return ((com.google.android.connecteddevice.api.IDeviceCallback)iin);
      }
      return new com.google.android.connecteddevice.api.IDeviceCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onSecureChannelEstablished:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          this.onSecureChannelEstablished(_arg0);
          break;
        }
        case TRANSACTION_onMessageReceived:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          com.google.android.connecteddevice.model.DeviceMessage _arg1;
          _arg1 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.DeviceMessage.CREATOR);
          this.onMessageReceived(_arg0, _arg1);
          break;
        }
        case TRANSACTION_onDeviceError:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          int _arg1;
          _arg1 = data.readInt();
          this.onDeviceError(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IDeviceCallback
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
       * Triggered when secure channel has been established on a companionDevice. Encrypted messaging
       * now available.
       */
      @Override public void onSecureChannelEstablished(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onSecureChannelEstablished, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when a new message is received from a connectedDevice. */
      @Override public void onMessageReceived(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          _Parcel.writeTypedObject(_data, message, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onMessageReceived, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when an error has occurred for a connectedDevice. */
      @Override public void onDeviceError(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, int error) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          _data.writeInt(error);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceError, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onSecureChannelEstablished = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onMessageReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onDeviceError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IDeviceCallback";
  /**
   * Triggered when secure channel has been established on a companionDevice. Encrypted messaging
   * now available.
   */
  public void onSecureChannelEstablished(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException;
  /** Triggered when a new message is received from a connectedDevice. */
  public void onMessageReceived(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, com.google.android.connecteddevice.model.DeviceMessage message) throws android.os.RemoteException;
  /** Triggered when an error has occurred for a connectedDevice. */
  public void onDeviceError(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice, int error) throws android.os.RemoteException;
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
