/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.panasonic.iapx;
public interface IServiceConnectorDelegate extends android.os.IInterface
{
  /** Default implementation for IServiceConnectorDelegate. */
  public static class Default implements com.panasonic.iapx.IServiceConnectorDelegate
  {
    @Override public void OnServiceConnectionChange(int status) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.panasonic.iapx.IServiceConnectorDelegate
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.panasonic.iapx.IServiceConnectorDelegate interface,
     * generating a proxy if needed.
     */
    public static com.panasonic.iapx.IServiceConnectorDelegate asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.panasonic.iapx.IServiceConnectorDelegate))) {
        return ((com.panasonic.iapx.IServiceConnectorDelegate)iin);
      }
      return new com.panasonic.iapx.IServiceConnectorDelegate.Stub.Proxy(obj);
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
        case TRANSACTION_OnServiceConnectionChange:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.OnServiceConnectionChange(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.panasonic.iapx.IServiceConnectorDelegate
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
      @Override public void OnServiceConnectionChange(int status) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(status);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnServiceConnectionChange, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_OnServiceConnectionChange = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.panasonic.iapx.IServiceConnectorDelegate";
  public void OnServiceConnectionChange(int status) throws android.os.RemoteException;
}
