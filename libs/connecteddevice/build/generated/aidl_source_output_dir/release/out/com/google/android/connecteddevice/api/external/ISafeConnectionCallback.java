/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api.external;
/**
 * Callback for triggered connection events.
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
 * Next transaction value: 2
 */
// TODO Link to the internal documentation on rules for modifying
// this class.
public interface ISafeConnectionCallback extends android.os.IInterface
{
  /** Default implementation for ISafeConnectionCallback. */
  public static class Default implements com.google.android.connecteddevice.api.external.ISafeConnectionCallback
  {
    /** Triggered when a new companion device has connected. */
    @Override public void onDeviceConnected(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /** Triggered when a companion device has disconnected. */
    @Override public void onDeviceDisconnected(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.external.ISafeConnectionCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.external.ISafeConnectionCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.external.ISafeConnectionCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.external.ISafeConnectionCallback))) {
        return ((com.google.android.connecteddevice.api.external.ISafeConnectionCallback)iin);
      }
      return new com.google.android.connecteddevice.api.external.ISafeConnectionCallback.Stub.Proxy(obj);
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
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onDeviceConnected(_arg0);
          break;
        }
        case TRANSACTION_onDeviceDisconnected:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
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
    private static class Proxy implements com.google.android.connecteddevice.api.external.ISafeConnectionCallback
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
      /** Triggered when a new companion device has connected. */
      @Override public void onDeviceConnected(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceConnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when a companion device has disconnected. */
      @Override public void onDeviceDisconnected(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
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
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.external.ISafeConnectionCallback";
  /** Triggered when a new companion device has connected. */
  public void onDeviceConnected(java.lang.String deviceId) throws android.os.RemoteException;
  /** Triggered when a companion device has disconnected. */
  public void onDeviceDisconnected(java.lang.String deviceId) throws android.os.RemoteException;
}
