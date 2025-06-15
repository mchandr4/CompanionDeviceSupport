/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Event notifications on the discovery process. */
public interface IDiscoveryCallback extends android.os.IInterface
{
  /** Default implementation for IDiscoveryCallback. */
  public static class Default implements com.google.android.connecteddevice.transport.IDiscoveryCallback
  {
    /** Invoked when discovery for a device has started successfully. */
    @Override public void onDiscoveryStartedSuccessfully() throws android.os.RemoteException
    {
    }
    /** Invoked when discovery for a device failed to start. */
    @Override public void onDiscoveryFailedToStart() throws android.os.RemoteException
    {
    }
    /** Invoked when a device connection is established in response to the discovery. */
    @Override public void onDeviceConnected(java.lang.String protocolId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IDiscoveryCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IDiscoveryCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IDiscoveryCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IDiscoveryCallback))) {
        return ((com.google.android.connecteddevice.transport.IDiscoveryCallback)iin);
      }
      return new com.google.android.connecteddevice.transport.IDiscoveryCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onDiscoveryStartedSuccessfully:
        {
          this.onDiscoveryStartedSuccessfully();
          break;
        }
        case TRANSACTION_onDiscoveryFailedToStart:
        {
          this.onDiscoveryFailedToStart();
          break;
        }
        case TRANSACTION_onDeviceConnected:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onDeviceConnected(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.transport.IDiscoveryCallback
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
      /** Invoked when discovery for a device has started successfully. */
      @Override public void onDiscoveryStartedSuccessfully() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDiscoveryStartedSuccessfully, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Invoked when discovery for a device failed to start. */
      @Override public void onDiscoveryFailedToStart() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDiscoveryFailedToStart, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Invoked when a device connection is established in response to the discovery. */
      @Override public void onDeviceConnected(java.lang.String protocolId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceConnected, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDiscoveryStartedSuccessfully = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onDiscoveryFailedToStart = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onDeviceConnected = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IDiscoveryCallback";
  /** Invoked when discovery for a device has started successfully. */
  public void onDiscoveryStartedSuccessfully() throws android.os.RemoteException;
  /** Invoked when discovery for a device failed to start. */
  public void onDiscoveryFailedToStart() throws android.os.RemoteException;
  /** Invoked when a device connection is established in response to the discovery. */
  public void onDeviceConnected(java.lang.String protocolId) throws android.os.RemoteException;
}
