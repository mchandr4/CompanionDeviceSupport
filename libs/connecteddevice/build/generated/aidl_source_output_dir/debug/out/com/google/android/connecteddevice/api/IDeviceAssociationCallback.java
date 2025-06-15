/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Callback for triggered associated device related events.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 3
 */
public interface IDeviceAssociationCallback extends android.os.IInterface
{
  /** Default implementation for IDeviceAssociationCallback. */
  public static class Default implements com.google.android.connecteddevice.api.IDeviceAssociationCallback
  {
    /** Triggered when an associated device has been added */
    @Override public void onAssociatedDeviceAdded(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
    {
    }
    /** Triggered when an associated device has been removed. */
    @Override public void onAssociatedDeviceRemoved(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
    {
    }
    /** Triggered when an associated device has been updated. */
    @Override public void onAssociatedDeviceUpdated(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IDeviceAssociationCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IDeviceAssociationCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IDeviceAssociationCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IDeviceAssociationCallback))) {
        return ((com.google.android.connecteddevice.api.IDeviceAssociationCallback)iin);
      }
      return new com.google.android.connecteddevice.api.IDeviceAssociationCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onAssociatedDeviceAdded:
        {
          com.google.android.connecteddevice.model.AssociatedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.AssociatedDevice.CREATOR);
          this.onAssociatedDeviceAdded(_arg0);
          break;
        }
        case TRANSACTION_onAssociatedDeviceRemoved:
        {
          com.google.android.connecteddevice.model.AssociatedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.AssociatedDevice.CREATOR);
          this.onAssociatedDeviceRemoved(_arg0);
          break;
        }
        case TRANSACTION_onAssociatedDeviceUpdated:
        {
          com.google.android.connecteddevice.model.AssociatedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.AssociatedDevice.CREATOR);
          this.onAssociatedDeviceUpdated(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IDeviceAssociationCallback
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
      /** Triggered when an associated device has been added */
      @Override public void onAssociatedDeviceAdded(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, device, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociatedDeviceAdded, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when an associated device has been removed. */
      @Override public void onAssociatedDeviceRemoved(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, device, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociatedDeviceRemoved, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when an associated device has been updated. */
      @Override public void onAssociatedDeviceUpdated(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, device, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociatedDeviceUpdated, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onAssociatedDeviceAdded = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onAssociatedDeviceRemoved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onAssociatedDeviceUpdated = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IDeviceAssociationCallback";
  /** Triggered when an associated device has been added */
  public void onAssociatedDeviceAdded(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException;
  /** Triggered when an associated device has been removed. */
  public void onAssociatedDeviceRemoved(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException;
  /** Triggered when an associated device has been updated. */
  public void onAssociatedDeviceUpdated(com.google.android.connecteddevice.model.AssociatedDevice device) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
