/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.panasonic.iapx;
public interface IServiceConnector extends android.os.IInterface
{
  /** Default implementation for IServiceConnector. */
  public static class Default implements com.panasonic.iapx.IServiceConnector
  {
    @Override public void ConnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate, android.os.IBinder deviceConnectionDelegate) throws android.os.RemoteException
    {
    }
    @Override public void DisconnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.panasonic.iapx.IServiceConnector
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.panasonic.iapx.IServiceConnector interface,
     * generating a proxy if needed.
     */
    public static com.panasonic.iapx.IServiceConnector asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.panasonic.iapx.IServiceConnector))) {
        return ((com.panasonic.iapx.IServiceConnector)iin);
      }
      return new com.panasonic.iapx.IServiceConnector.Stub.Proxy(obj);
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
        case TRANSACTION_ConnectClient:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.IBinder _arg1;
          _arg1 = data.readStrongBinder();
          android.os.IBinder _arg2;
          _arg2 = data.readStrongBinder();
          this.ConnectClient(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_DisconnectClient:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.IBinder _arg1;
          _arg1 = data.readStrongBinder();
          this.DisconnectClient(_arg0, _arg1);
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
    private static class Proxy implements com.panasonic.iapx.IServiceConnector
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
      @Override public void ConnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate, android.os.IBinder deviceConnectionDelegate) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(uniqueClientName);
          _data.writeStrongBinder(serviceDelegate);
          _data.writeStrongBinder(deviceConnectionDelegate);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ConnectClient, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void DisconnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(uniqueClientName);
          _data.writeStrongBinder(serviceDelegate);
          boolean _status = mRemote.transact(Stub.TRANSACTION_DisconnectClient, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_ConnectClient = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_DisconnectClient = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.panasonic.iapx.IServiceConnector";
  public static final int kServiceConnectionReady = 1;
  public static final int kServiceConnectionRefused = 2;
  public static final int kServiceConnectionLost = 3;
  public void ConnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate, android.os.IBinder deviceConnectionDelegate) throws android.os.RemoteException;
  public void DisconnectClient(java.lang.String uniqueClientName, android.os.IBinder serviceDelegate) throws android.os.RemoteException;
}
