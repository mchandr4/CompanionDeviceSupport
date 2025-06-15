/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Callback for the result of sending data. */
public interface IDataSendCallback extends android.os.IInterface
{
  /** Default implementation for IDataSendCallback. */
  public static class Default implements com.google.android.connecteddevice.transport.IDataSendCallback
  {
    /** Invoked when the data was successfully sent. */
    @Override public void onDataSentSuccessfully() throws android.os.RemoteException
    {
    }
    /** Invoked when the data failed to send. */
    @Override public void onDataFailedToSend() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IDataSendCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IDataSendCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IDataSendCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IDataSendCallback))) {
        return ((com.google.android.connecteddevice.transport.IDataSendCallback)iin);
      }
      return new com.google.android.connecteddevice.transport.IDataSendCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onDataSentSuccessfully:
        {
          this.onDataSentSuccessfully();
          break;
        }
        case TRANSACTION_onDataFailedToSend:
        {
          this.onDataFailedToSend();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.transport.IDataSendCallback
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
      /** Invoked when the data was successfully sent. */
      @Override public void onDataSentSuccessfully() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDataSentSuccessfully, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Invoked when the data failed to send. */
      @Override public void onDataFailedToSend() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDataFailedToSend, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDataSentSuccessfully = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onDataFailedToSend = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IDataSendCallback";
  /** Invoked when the data was successfully sent. */
  public void onDataSentSuccessfully() throws android.os.RemoteException;
  /** Invoked when the data failed to send. */
  public void onDataFailedToSend() throws android.os.RemoteException;
}
