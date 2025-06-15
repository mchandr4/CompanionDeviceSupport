/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Listener to be invoked when data has been received from a device. */
public interface IDataReceivedListener extends android.os.IInterface
{
  /** Default implementation for IDataReceivedListener. */
  public static class Default implements com.google.android.connecteddevice.transport.IDataReceivedListener
  {
    /** Called when [data] is received from the remote device on protocol protocolId. */
    @Override public void onDataReceived(java.lang.String protocolId, byte[] data) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IDataReceivedListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IDataReceivedListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IDataReceivedListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IDataReceivedListener))) {
        return ((com.google.android.connecteddevice.transport.IDataReceivedListener)iin);
      }
      return new com.google.android.connecteddevice.transport.IDataReceivedListener.Stub.Proxy(obj);
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
        case TRANSACTION_onDataReceived:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          this.onDataReceived(_arg0, _arg1);
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
    private static class Proxy implements com.google.android.connecteddevice.transport.IDataReceivedListener
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
      /** Called when [data] is received from the remote device on protocol protocolId. */
      @Override public void onDataReceived(java.lang.String protocolId, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDataReceived, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDataReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IDataReceivedListener";
  /** Called when [data] is received from the remote device on protocol protocolId. */
  public void onDataReceived(java.lang.String protocolId, byte[] data) throws android.os.RemoteException;
}
