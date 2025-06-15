/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Listener to be invoked when a device has disconnected. */
public interface IDeviceDisconnectedListener extends android.os.IInterface
{
  /** Default implementation for IDeviceDisconnectedListener. */
  public static class Default implements com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
  {
    /** Called when the device has disconnected on protocol protocolId. */
    @Override public void onDeviceDisconnected(java.lang.String protocolId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IDeviceDisconnectedListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IDeviceDisconnectedListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IDeviceDisconnectedListener))) {
        return ((com.google.android.connecteddevice.transport.IDeviceDisconnectedListener)iin);
      }
      return new com.google.android.connecteddevice.transport.IDeviceDisconnectedListener.Stub.Proxy(obj);
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
    private static class Proxy implements com.google.android.connecteddevice.transport.IDeviceDisconnectedListener
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
      /** Called when the device has disconnected on protocol protocolId. */
      @Override public void onDeviceDisconnected(java.lang.String protocolId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceDisconnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDeviceDisconnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IDeviceDisconnectedListener";
  /** Called when the device has disconnected on protocol protocolId. */
  public void onDeviceDisconnected(java.lang.String protocolId) throws android.os.RemoteException;
}
