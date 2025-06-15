/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api.external;
/**
 * Listener for the output log file.
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
 * Next transaction value: 1
 */
// TODO Link to the internal documentation on rules for modifying
// this class.
public interface ISafeOnLogRequestedListener extends android.os.IInterface
{
  /** Default implementation for ISafeOnLogRequestedListener. */
  public static class Default implements com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
  {
    /** Triggered when the log records is requested. */
    @Override public void onLogRecordsRequested() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener))) {
        return ((com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener)iin);
      }
      return new com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener.Stub.Proxy(obj);
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
        case TRANSACTION_onLogRecordsRequested:
        {
          this.onLogRecordsRequested();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener
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
      /** Triggered when the log records is requested. */
      @Override public void onLogRecordsRequested() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onLogRecordsRequested, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onLogRecordsRequested = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.external.ISafeOnLogRequestedListener";
  /** Triggered when the log records is requested. */
  public void onLogRecordsRequested() throws android.os.RemoteException;
}
