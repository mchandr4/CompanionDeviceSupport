/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api.external;
/**
 * Interface to pass Companion binder version to remote processes.
 * 
 * To maintain backward and forward compatibility, this class should not pass
 * customized parcelables. Instead please use supported primitive types
 * mentioned in the Android official doc.
 * See <a href="https://developer.android.com/guide/components/aidl">Android AIDL</a>
 */
// TODO Link to the internal documentation on rules when modify
// this class.
public interface ISafeBinderVersion extends android.os.IInterface
{
  /** Default implementation for ISafeBinderVersion. */
  public static class Default implements com.google.android.connecteddevice.api.external.ISafeBinderVersion
  {
    /** Returns the Companion binder version */
    @Override public int getVersion() throws android.os.RemoteException
    {
      return 0;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.external.ISafeBinderVersion
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.external.ISafeBinderVersion interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.external.ISafeBinderVersion asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.external.ISafeBinderVersion))) {
        return ((com.google.android.connecteddevice.api.external.ISafeBinderVersion)iin);
      }
      return new com.google.android.connecteddevice.api.external.ISafeBinderVersion.Stub.Proxy(obj);
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
        case TRANSACTION_getVersion:
        {
          int _result = this.getVersion();
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
    private static class Proxy implements com.google.android.connecteddevice.api.external.ISafeBinderVersion
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
      /** Returns the Companion binder version */
      @Override public int getVersion() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getVersion, _data, _reply, 0);
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
    static final int TRANSACTION_getVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.external.ISafeBinderVersion";
  /** Returns the Companion binder version */
  public int getVersion() throws android.os.RemoteException;
}
