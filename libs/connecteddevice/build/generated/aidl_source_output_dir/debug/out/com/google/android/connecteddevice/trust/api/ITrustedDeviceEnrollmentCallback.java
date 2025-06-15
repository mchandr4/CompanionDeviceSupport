/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.trust.api;
/** Callback for triggered trusted device enrollment events. */
public interface ITrustedDeviceEnrollmentCallback extends android.os.IInterface
{
  /** Default implementation for ITrustedDeviceEnrollmentCallback. */
  public static class Default implements com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback
  {
    /** Triggered when the HU received escrow token from the mobile side. */
    @Override public void onEscrowTokenReceived() throws android.os.RemoteException
    {
    }
    /** Triggered when credentials validation is needed to authenticate a new escrow token. */
    @Override public void onValidateCredentialsRequest() throws android.os.RemoteException
    {
    }
    /** Triggered when an error happens during trusted device enrollment. */
    @Override public void onTrustedDeviceEnrollmentError(int error) throws android.os.RemoteException
    {
    }
    /** Triggered when a lockscreen credential needs to be set up for trusted device enrollment. */
    @Override public void onSecureDeviceRequest() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback))) {
        return ((com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback)iin);
      }
      return new com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onEscrowTokenReceived:
        {
          this.onEscrowTokenReceived();
          break;
        }
        case TRANSACTION_onValidateCredentialsRequest:
        {
          this.onValidateCredentialsRequest();
          break;
        }
        case TRANSACTION_onTrustedDeviceEnrollmentError:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.onTrustedDeviceEnrollmentError(_arg0);
          break;
        }
        case TRANSACTION_onSecureDeviceRequest:
        {
          this.onSecureDeviceRequest();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback
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
      /** Triggered when the HU received escrow token from the mobile side. */
      @Override public void onEscrowTokenReceived() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onEscrowTokenReceived, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when credentials validation is needed to authenticate a new escrow token. */
      @Override public void onValidateCredentialsRequest() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onValidateCredentialsRequest, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when an error happens during trusted device enrollment. */
      @Override public void onTrustedDeviceEnrollmentError(int error) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(error);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTrustedDeviceEnrollmentError, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when a lockscreen credential needs to be set up for trusted device enrollment. */
      @Override public void onSecureDeviceRequest() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onSecureDeviceRequest, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onEscrowTokenReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onValidateCredentialsRequest = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onTrustedDeviceEnrollmentError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onSecureDeviceRequest = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback";
  /** Triggered when the HU received escrow token from the mobile side. */
  public void onEscrowTokenReceived() throws android.os.RemoteException;
  /** Triggered when credentials validation is needed to authenticate a new escrow token. */
  public void onValidateCredentialsRequest() throws android.os.RemoteException;
  /** Triggered when an error happens during trusted device enrollment. */
  public void onTrustedDeviceEnrollmentError(int error) throws android.os.RemoteException;
  /** Triggered when a lockscreen credential needs to be set up for trusted device enrollment. */
  public void onSecureDeviceRequest() throws android.os.RemoteException;
}
