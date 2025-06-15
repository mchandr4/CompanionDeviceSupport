/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Creates listeners for feature coordinator initialization.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 2
 */
public interface IFeatureCoordinatorStatusNotifier extends android.os.IInterface
{
  /** Default implementation for IFeatureCoordinatorStatusNotifier. */
  public static class Default implements com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier
  {
    /**
     * Registers listeners to be notified when feature coordinator is
     * initialized. Eeach listener will receive a feature coordinator upon
     * notification.
     */
    @Override public void registerFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listener) throws android.os.RemoteException
    {
    }
    /** Unregisters listener. */
    @Override public void unregisterFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listeners) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier))) {
        return ((com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier)iin);
      }
      return new com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier.Stub.Proxy(obj);
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
        case TRANSACTION_registerFeatureCoordinatorListener:
        {
          com.google.android.connecteddevice.api.IFeatureCoordinatorListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.IFeatureCoordinatorListener.Stub.asInterface(data.readStrongBinder());
          this.registerFeatureCoordinatorListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterFeatureCoordinatorListener:
        {
          com.google.android.connecteddevice.api.IFeatureCoordinatorListener _arg0;
          _arg0 = com.google.android.connecteddevice.api.IFeatureCoordinatorListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterFeatureCoordinatorListener(_arg0);
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
    private static class Proxy implements com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier
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
      /**
       * Registers listeners to be notified when feature coordinator is
       * initialized. Eeach listener will receive a feature coordinator upon
       * notification.
       */
      @Override public void registerFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerFeatureCoordinatorListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Unregisters listener. */
      @Override public void unregisterFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listeners) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listeners);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterFeatureCoordinatorListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_registerFeatureCoordinatorListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_unregisterFeatureCoordinatorListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IFeatureCoordinatorStatusNotifier";
  /**
   * Registers listeners to be notified when feature coordinator is
   * initialized. Eeach listener will receive a feature coordinator upon
   * notification.
   */
  public void registerFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listener) throws android.os.RemoteException;
  /** Unregisters listener. */
  public void unregisterFeatureCoordinatorListener(com.google.android.connecteddevice.api.IFeatureCoordinatorListener listeners) throws android.os.RemoteException;
}
