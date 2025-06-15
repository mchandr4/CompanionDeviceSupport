/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Listener for feature coordinator initialization.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 1
 */
public interface IFeatureCoordinatorListener extends android.os.IInterface
{
  /** Default implementation for IFeatureCoordinatorListener. */
  public static class Default implements com.google.android.connecteddevice.api.IFeatureCoordinatorListener
  {
    /** Callback when feature coordinator is initialized. */
    @Override public void onFeatureCoordinatorInitialized(com.google.android.connecteddevice.api.IFeatureCoordinator featureCoordinator) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IFeatureCoordinatorListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IFeatureCoordinatorListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IFeatureCoordinatorListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IFeatureCoordinatorListener))) {
        return ((com.google.android.connecteddevice.api.IFeatureCoordinatorListener)iin);
      }
      return new com.google.android.connecteddevice.api.IFeatureCoordinatorListener.Stub.Proxy(obj);
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
        case TRANSACTION_onFeatureCoordinatorInitialized:
        {
          com.google.android.connecteddevice.api.IFeatureCoordinator _arg0;
          _arg0 = com.google.android.connecteddevice.api.IFeatureCoordinator.Stub.asInterface(data.readStrongBinder());
          this.onFeatureCoordinatorInitialized(_arg0);
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
    private static class Proxy implements com.google.android.connecteddevice.api.IFeatureCoordinatorListener
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
      /** Callback when feature coordinator is initialized. */
      @Override public void onFeatureCoordinatorInitialized(com.google.android.connecteddevice.api.IFeatureCoordinator featureCoordinator) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(featureCoordinator);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onFeatureCoordinatorInitialized, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onFeatureCoordinatorInitialized = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IFeatureCoordinatorListener";
  /** Callback when feature coordinator is initialized. */
  public void onFeatureCoordinatorInitialized(com.google.android.connecteddevice.api.IFeatureCoordinator featureCoordinator) throws android.os.RemoteException;
}
