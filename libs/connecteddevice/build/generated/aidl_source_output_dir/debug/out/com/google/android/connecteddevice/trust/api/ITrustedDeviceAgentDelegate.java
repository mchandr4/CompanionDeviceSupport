/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.trust.api;
/** Delegate for TrustAgent operations. */
public interface ITrustedDeviceAgentDelegate extends android.os.IInterface
{
  /** Default implementation for ITrustedDeviceAgentDelegate. */
  public static class Default implements com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate
  {
    /** Add escrow token for user. */
    @Override public void addEscrowToken(byte[] token, int userId) throws android.os.RemoteException
    {
    }
    /** Unlock user with token and handle. */
    @Override public void unlockUserWithToken(byte[] token, long handle, int userId) throws android.os.RemoteException
    {
    }
    /** Remove the escrow token associated with handle for user. */
    @Override public void removeEscrowToken(long handle, int userId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate))) {
        return ((com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate)iin);
      }
      return new com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate.Stub.Proxy(obj);
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
        case TRANSACTION_addEscrowToken:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          int _arg1;
          _arg1 = data.readInt();
          this.addEscrowToken(_arg0, _arg1);
          break;
        }
        case TRANSACTION_unlockUserWithToken:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          long _arg1;
          _arg1 = data.readLong();
          int _arg2;
          _arg2 = data.readInt();
          this.unlockUserWithToken(_arg0, _arg1, _arg2);
          break;
        }
        case TRANSACTION_removeEscrowToken:
        {
          long _arg0;
          _arg0 = data.readLong();
          int _arg1;
          _arg1 = data.readInt();
          this.removeEscrowToken(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate
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
      /** Add escrow token for user. */
      @Override public void addEscrowToken(byte[] token, int userId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(token);
          _data.writeInt(userId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addEscrowToken, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Unlock user with token and handle. */
      @Override public void unlockUserWithToken(byte[] token, long handle, int userId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(token);
          _data.writeLong(handle);
          _data.writeInt(userId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unlockUserWithToken, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Remove the escrow token associated with handle for user. */
      @Override public void removeEscrowToken(long handle, int userId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(handle);
          _data.writeInt(userId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeEscrowToken, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_addEscrowToken = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_unlockUserWithToken = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_removeEscrowToken = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate";
  /** Add escrow token for user. */
  public void addEscrowToken(byte[] token, int userId) throws android.os.RemoteException;
  /** Unlock user with token and handle. */
  public void unlockUserWithToken(byte[] token, long handle, int userId) throws android.os.RemoteException;
  /** Remove the escrow token associated with handle for user. */
  public void removeEscrowToken(long handle, int userId) throws android.os.RemoteException;
}
