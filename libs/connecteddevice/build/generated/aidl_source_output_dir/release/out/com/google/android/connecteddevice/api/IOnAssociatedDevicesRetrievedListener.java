/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Listener for retrieving devices associated with the active user.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 1
 */
public interface IOnAssociatedDevicesRetrievedListener extends android.os.IInterface
{
  /** Default implementation for IOnAssociatedDevicesRetrievedListener. */
  public static class Default implements com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
  {
    /** Triggered when the devices associated with the active user are retrieved. */
    @Override public void onAssociatedDevicesRetrieved(java.util.List<com.google.android.connecteddevice.model.AssociatedDevice> devices) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener))) {
        return ((com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener)iin);
      }
      return new com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener.Stub.Proxy(obj);
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
        case TRANSACTION_onAssociatedDevicesRetrieved:
        {
          java.util.List<com.google.android.connecteddevice.model.AssociatedDevice> _arg0;
          _arg0 = data.createTypedArrayList(com.google.android.connecteddevice.model.AssociatedDevice.CREATOR);
          this.onAssociatedDevicesRetrieved(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener
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
      /** Triggered when the devices associated with the active user are retrieved. */
      @Override public void onAssociatedDevicesRetrieved(java.util.List<com.google.android.connecteddevice.model.AssociatedDevice> devices) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedList(_data, devices, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociatedDevicesRetrieved, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onAssociatedDevicesRetrieved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IOnAssociatedDevicesRetrievedListener";
  /** Triggered when the devices associated with the active user are retrieved. */
  public void onAssociatedDevicesRetrieved(java.util.List<com.google.android.connecteddevice.model.AssociatedDevice> devices) throws android.os.RemoteException;
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
    static private <T extends android.os.Parcelable> void writeTypedList(
        android.os.Parcel parcel, java.util.List<T> value, int parcelableFlags) {
      if (value == null) {
        parcel.writeInt(-1);
      } else {
        int N = value.size();
        int i = 0;
        parcel.writeInt(N);
        while (i < N) {
    writeTypedObject(parcel, value.get(i), parcelableFlags);
          i++;
        }
      }
    }
  }
}
