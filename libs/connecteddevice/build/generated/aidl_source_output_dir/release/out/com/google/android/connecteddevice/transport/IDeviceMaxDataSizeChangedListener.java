/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Listener to be invoked when the protocol has negotiated a new maximum data size. */
public interface IDeviceMaxDataSizeChangedListener extends android.os.IInterface
{
  /** Default implementation for IDeviceMaxDataSizeChangedListener. */
  public static class Default implements com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener
  {
    /** Called when the protocol [protocolId] has negotiated a new maximum data size [maxBytes]. */
    @Override public void onDeviceMaxDataSizeChanged(java.lang.String protocolId, int maxBytes) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener))) {
        return ((com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener)iin);
      }
      return new com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener.Stub.Proxy(obj);
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
        case TRANSACTION_onDeviceMaxDataSizeChanged:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          this.onDeviceMaxDataSizeChanged(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener
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
      /** Called when the protocol [protocolId] has negotiated a new maximum data size [maxBytes]. */
      @Override public void onDeviceMaxDataSizeChanged(java.lang.String protocolId, int maxBytes) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(protocolId);
          _data.writeInt(maxBytes);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceMaxDataSizeChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onDeviceMaxDataSizeChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IDeviceMaxDataSizeChangedListener";
  /** Called when the protocol [protocolId] has negotiated a new maximum data size [maxBytes]. */
  public void onDeviceMaxDataSizeChanged(java.lang.String protocolId, int maxBytes) throws android.os.RemoteException;
}
