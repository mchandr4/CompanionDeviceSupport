/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.trust.api;
/** Listnener for trusted device enrollment notification request. */
public interface IOnTrustedDeviceEnrollmentNotificationCallback extends android.os.IInterface
{
  /** Default implementation for IOnTrustedDeviceEnrollmentNotificationCallback. */
  public static class Default implements com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback
  {
    /** Triggered when enrollment notification is needed to continue trusted device enrollment. */
    @Override public void onTrustedDeviceEnrollmentNotificationRequest() throws android.os.RemoteException
    {
    }
    /** Triggered when the pending enrollment is aborted. */
    @Override public void onTrustedDeviceEnrollmentNotificationCancellation() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback))) {
        return ((com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback)iin);
      }
      return new com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onTrustedDeviceEnrollmentNotificationRequest:
        {
          this.onTrustedDeviceEnrollmentNotificationRequest();
          break;
        }
        case TRANSACTION_onTrustedDeviceEnrollmentNotificationCancellation:
        {
          this.onTrustedDeviceEnrollmentNotificationCancellation();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback
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
      /** Triggered when enrollment notification is needed to continue trusted device enrollment. */
      @Override public void onTrustedDeviceEnrollmentNotificationRequest() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTrustedDeviceEnrollmentNotificationRequest, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when the pending enrollment is aborted. */
      @Override public void onTrustedDeviceEnrollmentNotificationCancellation() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTrustedDeviceEnrollmentNotificationCancellation, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onTrustedDeviceEnrollmentNotificationRequest = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onTrustedDeviceEnrollmentNotificationCancellation = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback";
  /** Triggered when enrollment notification is needed to continue trusted device enrollment. */
  public void onTrustedDeviceEnrollmentNotificationRequest() throws android.os.RemoteException;
  /** Triggered when the pending enrollment is aborted. */
  public void onTrustedDeviceEnrollmentNotificationCancellation() throws android.os.RemoteException;
}
