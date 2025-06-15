/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Callback for triggered connection events.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 2
 */
public interface IConnectionCallback extends android.os.IInterface
{
  /** Default implementation for IConnectionCallback. */
  public static class Default implements com.google.android.connecteddevice.api.IConnectionCallback
  {
    /** Triggered when a new connectedDevice has connected. */
    @Override public void onDeviceConnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
    {
    }
    /** Triggered when a connectedDevice has disconnected. */
    @Override public void onDeviceDisconnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IConnectionCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IConnectionCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IConnectionCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IConnectionCallback))) {
        return ((com.google.android.connecteddevice.api.IConnectionCallback)iin);
      }
      return new com.google.android.connecteddevice.api.IConnectionCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onDeviceConnected:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          this.onDeviceConnected(_arg0);
          break;
        }
        case TRANSACTION_onDeviceDisconnected:
        {
          com.google.android.connecteddevice.model.ConnectedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
          this.onDeviceDisconnected(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IConnectionCallback
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
      /** Triggered when a new connectedDevice has connected. */
      @Override public void onDeviceConnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceConnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when a connectedDevice has disconnected. */
      @Override public void onDeviceDisconnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, connectedDevice, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceDisconnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDeviceConnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onDeviceDisconnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IConnectionCallback";
  /** Triggered when a new connectedDevice has connected. */
  public void onDeviceConnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException;
  /** Triggered when a connectedDevice has disconnected. */
  public void onDeviceDisconnected(com.google.android.connecteddevice.model.ConnectedDevice connectedDevice) throws android.os.RemoteException;
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
