/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.trust.api;
/** Callback for triggered trusted device events. */
public interface ITrustedDeviceCallback extends android.os.IInterface
{
  /** Default implementation for ITrustedDeviceCallback. */
  public static class Default implements com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback
  {
    /** Triggered when a new device has been enrolled. */
    @Override public void onTrustedDeviceAdded(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException
    {
    }
    /** Triggered when a new device has been unenrolled. */
    @Override public void onTrustedDeviceRemoved(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback))) {
        return ((com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback)iin);
      }
      return new com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onTrustedDeviceAdded:
        {
          com.google.android.connecteddevice.trust.api.TrustedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.trust.api.TrustedDevice.CREATOR);
          this.onTrustedDeviceAdded(_arg0);
          break;
        }
        case TRANSACTION_onTrustedDeviceRemoved:
        {
          com.google.android.connecteddevice.trust.api.TrustedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.trust.api.TrustedDevice.CREATOR);
          this.onTrustedDeviceRemoved(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback
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
      /** Triggered when a new device has been enrolled. */
      @Override public void onTrustedDeviceAdded(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, device, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTrustedDeviceAdded, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when a new device has been unenrolled. */
      @Override public void onTrustedDeviceRemoved(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, device, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTrustedDeviceRemoved, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onTrustedDeviceAdded = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onTrustedDeviceRemoved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback";
  /** Triggered when a new device has been enrolled. */
  public void onTrustedDeviceAdded(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException;
  /** Triggered when a new device has been unenrolled. */
  public void onTrustedDeviceRemoved(com.google.android.connecteddevice.trust.api.TrustedDevice device) throws android.os.RemoteException;
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
