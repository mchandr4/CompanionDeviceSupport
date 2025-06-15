/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.panasonic.iapx;
public interface IDeviceConnectionDelegate extends android.os.IInterface
{
  /** Default implementation for IDeviceConnectionDelegate. */
  public static class Default implements com.panasonic.iapx.IDeviceConnectionDelegate
  {
    @Override public void OnConnectionReady(com.panasonic.iapx.IDeviceConnection connection, int transportType) throws android.os.RemoteException
    {
    }
    @Override public void OnConnectionClosed(com.panasonic.iapx.IDeviceConnection connection) throws android.os.RemoteException
    {
    }
    @Override public void OnDeviceNameUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String name) throws android.os.RemoteException
    {
    }
    @Override public void OnDeviceTransientUUIDUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String uuid) throws android.os.RemoteException
    {
    }
    @Override public void OnEAPSessionStart(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, java.lang.String eapProtocolName) throws android.os.RemoteException
    {
    }
    @Override public void OnEAPSessionStop(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId) throws android.os.RemoteException
    {
    }
    @Override public void OnEAPData(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, byte[] data) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.panasonic.iapx.IDeviceConnectionDelegate
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.panasonic.iapx.IDeviceConnectionDelegate interface,
     * generating a proxy if needed.
     */
    public static com.panasonic.iapx.IDeviceConnectionDelegate asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.panasonic.iapx.IDeviceConnectionDelegate))) {
        return ((com.panasonic.iapx.IDeviceConnectionDelegate)iin);
      }
      return new com.panasonic.iapx.IDeviceConnectionDelegate.Stub.Proxy(obj);
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
        case TRANSACTION_OnConnectionReady:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          int _arg1;
          _arg1 = data.readInt();
          this.OnConnectionReady(_arg0, _arg1);
          break;
        }
        case TRANSACTION_OnConnectionClosed:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          this.OnConnectionClosed(_arg0);
          break;
        }
        case TRANSACTION_OnDeviceNameUpdate:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.OnDeviceNameUpdate(_arg0, _arg1);
          break;
        }
        case TRANSACTION_OnDeviceTransientUUIDUpdate:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.OnDeviceTransientUUIDUpdate(_arg0, _arg1);
          break;
        }
        case TRANSACTION_OnEAPSessionStart:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          long _arg1;
          _arg1 = data.readLong();
          java.lang.String _arg2;
          _arg2 = data.readString();
          this.OnEAPSessionStart(_arg0, _arg1, _arg2);
          break;
        }
        case TRANSACTION_OnEAPSessionStop:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          long _arg1;
          _arg1 = data.readLong();
          this.OnEAPSessionStop(_arg0, _arg1);
          break;
        }
        case TRANSACTION_OnEAPData:
        {
          com.panasonic.iapx.IDeviceConnection _arg0;
          _arg0 = com.panasonic.iapx.IDeviceConnection.Stub.asInterface(data.readStrongBinder());
          long _arg1;
          _arg1 = data.readLong();
          byte[] _arg2;
          _arg2 = data.createByteArray();
          this.OnEAPData(_arg0, _arg1, _arg2);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.panasonic.iapx.IDeviceConnectionDelegate
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
      @Override public void OnConnectionReady(com.panasonic.iapx.IDeviceConnection connection, int transportType) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeInt(transportType);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnConnectionReady, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnConnectionClosed(com.panasonic.iapx.IDeviceConnection connection) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnConnectionClosed, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnDeviceNameUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnDeviceNameUpdate, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnDeviceTransientUUIDUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String uuid) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeString(uuid);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnDeviceTransientUUIDUpdate, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnEAPSessionStart(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, java.lang.String eapProtocolName) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeLong(eapSessionId);
          _data.writeString(eapProtocolName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnEAPSessionStart, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnEAPSessionStop(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeLong(eapSessionId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnEAPSessionStop, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void OnEAPData(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, byte[] data) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(connection);
          _data.writeLong(eapSessionId);
          _data.writeByteArray(data);
          boolean _status = mRemote.transact(Stub.TRANSACTION_OnEAPData, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_OnConnectionReady = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_OnConnectionClosed = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_OnDeviceNameUpdate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 100);
    static final int TRANSACTION_OnDeviceTransientUUIDUpdate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 101);
    static final int TRANSACTION_OnEAPSessionStart = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3000);
    static final int TRANSACTION_OnEAPSessionStop = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3001);
    static final int TRANSACTION_OnEAPData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3010);
  }
  public static final java.lang.String DESCRIPTOR = "com.panasonic.iapx.IDeviceConnectionDelegate";
  public void OnConnectionReady(com.panasonic.iapx.IDeviceConnection connection, int transportType) throws android.os.RemoteException;
  public void OnConnectionClosed(com.panasonic.iapx.IDeviceConnection connection) throws android.os.RemoteException;
  public void OnDeviceNameUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String name) throws android.os.RemoteException;
  public void OnDeviceTransientUUIDUpdate(com.panasonic.iapx.IDeviceConnection connection, java.lang.String uuid) throws android.os.RemoteException;
  public void OnEAPSessionStart(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, java.lang.String eapProtocolName) throws android.os.RemoteException;
  public void OnEAPSessionStop(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId) throws android.os.RemoteException;
  public void OnEAPData(com.panasonic.iapx.IDeviceConnection connection, long eapSessionId, byte[] data) throws android.os.RemoteException;
}
