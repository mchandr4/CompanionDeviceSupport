/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.panasonic.iapx;
public interface IDeviceConnection extends android.os.IInterface
{
  /** Default implementation for IDeviceConnection. */
  public static class Default implements com.panasonic.iapx.IDeviceConnection
  {
    @Override public void DoNotDisturbAnymore() throws android.os.RemoteException
    {
    }
    @Override public void SendEAPData(long eapSessionId, byte[] data) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.panasonic.iapx.IDeviceConnection
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.panasonic.iapx.IDeviceConnection interface,
     * generating a proxy if needed.
     */
    public static com.panasonic.iapx.IDeviceConnection asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.panasonic.iapx.IDeviceConnection))) {
        return ((com.panasonic.iapx.IDeviceConnection)iin);
      }
      return new com.panasonic.iapx.IDeviceConnection.Stub.Proxy(obj);
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
        case TRANSACTION_DoNotDisturbAnymore:
        {
          this.DoNotDisturbAnymore();
          break;
        }
        case TRANSACTION_SendEAPData:
        {
          long _arg0;
          _arg0 = data.readLong();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          this.SendEAPData(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.panasonic.iapx.IDeviceConnection
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
      @Override public void DoNotDisturbAnymore() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_DoNotDisturbAnymore, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void SendEAPData(long eapSessionId, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(eapSessionId);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_SendEAPData, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_DoNotDisturbAnymore = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_SendEAPData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3000);
  }
  public static final java.lang.String DESCRIPTOR = "com.panasonic.iapx.IDeviceConnection";
  public void DoNotDisturbAnymore() throws android.os.RemoteException;
  public void SendEAPData(long eapSessionId, byte[] data) throws android.os.RemoteException;
}
