/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.transport;
/** Delegate for registering protocols to the platform. */
public interface IProtocolDelegate extends android.os.IInterface
{
  /** Default implementation for IProtocolDelegate. */
  public static class Default implements com.google.android.connecteddevice.transport.IProtocolDelegate
  {
    /** Add a protocol to the collection of supported protocols. */
    @Override public void addProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
    {
    }
    /** Add an OOB protocol to the collection of supported OOB protocols. */
    @Override public void addOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
    {
    }
    /** Remove a protocol from the collection of supported protocols. */
    @Override public void removeProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
    {
    }
    /** Remove an OOB protocol to the collection of supported OOB protocols. */
    @Override public void removeOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.transport.IProtocolDelegate
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.transport.IProtocolDelegate interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.transport.IProtocolDelegate asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.transport.IProtocolDelegate))) {
        return ((com.google.android.connecteddevice.transport.IProtocolDelegate)iin);
      }
      return new com.google.android.connecteddevice.transport.IProtocolDelegate.Stub.Proxy(obj);
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
        case TRANSACTION_addProtocol:
        {
          com.google.android.connecteddevice.transport.IConnectionProtocol _arg0;
          _arg0 = com.google.android.connecteddevice.transport.IConnectionProtocol.Stub.asInterface(data.readStrongBinder());
          this.addProtocol(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_addOobProtocol:
        {
          com.google.android.connecteddevice.transport.IConnectionProtocol _arg0;
          _arg0 = com.google.android.connecteddevice.transport.IConnectionProtocol.Stub.asInterface(data.readStrongBinder());
          this.addOobProtocol(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeProtocol:
        {
          com.google.android.connecteddevice.transport.IConnectionProtocol _arg0;
          _arg0 = com.google.android.connecteddevice.transport.IConnectionProtocol.Stub.asInterface(data.readStrongBinder());
          this.removeProtocol(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeOobProtocol:
        {
          com.google.android.connecteddevice.transport.IConnectionProtocol _arg0;
          _arg0 = com.google.android.connecteddevice.transport.IConnectionProtocol.Stub.asInterface(data.readStrongBinder());
          this.removeOobProtocol(_arg0);
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
    private static class Proxy implements com.google.android.connecteddevice.transport.IProtocolDelegate
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
      /** Add a protocol to the collection of supported protocols. */
      @Override public void addProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(protocol);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addProtocol, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Add an OOB protocol to the collection of supported OOB protocols. */
      @Override public void addOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(protocol);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addOobProtocol, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove a protocol from the collection of supported protocols. */
      @Override public void removeProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(protocol);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeProtocol, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove an OOB protocol to the collection of supported OOB protocols. */
      @Override public void removeOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(protocol);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeOobProtocol, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_addProtocol = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_addOobProtocol = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_removeProtocol = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_removeOobProtocol = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.transport.IProtocolDelegate";
  /** Add a protocol to the collection of supported protocols. */
  public void addProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException;
  /** Add an OOB protocol to the collection of supported OOB protocols. */
  public void addOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException;
  /** Remove a protocol from the collection of supported protocols. */
  public void removeProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException;
  /** Remove an OOB protocol to the collection of supported OOB protocols. */
  public void removeOobProtocol(com.google.android.connecteddevice.transport.IConnectionProtocol protocol) throws android.os.RemoteException;
}
