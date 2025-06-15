/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api;
/**
 * Callback for triggered association events.
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 5
 */
public interface IAssociationCallback extends android.os.IInterface
{
  /** Default implementation for IAssociationCallback. */
  public static class Default implements com.google.android.connecteddevice.api.IAssociationCallback
  {
    /** Triggered when IHU starts advertising for association successfully. */
    @Override public void onAssociationStartSuccess(com.google.android.connecteddevice.model.StartAssociationResponse response) throws android.os.RemoteException
    {
    }
    /** Triggered when IHU failed to start advertising for association. */
    @Override public void onAssociationStartFailure() throws android.os.RemoteException
    {
    }
    /** Triggered when an error has been encountered during association with a new device. */
    @Override public void onAssociationError(int error) throws android.os.RemoteException
    {
    }
    /**  Triggered when a pairing code is available to be present. */
    @Override public void onVerificationCodeAvailable(java.lang.String code) throws android.os.RemoteException
    {
    }
    /** Triggered when the association has completed */
    @Override public void onAssociationCompleted() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.IAssociationCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.IAssociationCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.IAssociationCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.IAssociationCallback))) {
        return ((com.google.android.connecteddevice.api.IAssociationCallback)iin);
      }
      return new com.google.android.connecteddevice.api.IAssociationCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onAssociationStartSuccess:
        {
          com.google.android.connecteddevice.model.StartAssociationResponse _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.model.StartAssociationResponse.CREATOR);
          this.onAssociationStartSuccess(_arg0);
          break;
        }
        case TRANSACTION_onAssociationStartFailure:
        {
          this.onAssociationStartFailure();
          break;
        }
        case TRANSACTION_onAssociationError:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.onAssociationError(_arg0);
          break;
        }
        case TRANSACTION_onVerificationCodeAvailable:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onVerificationCodeAvailable(_arg0);
          break;
        }
        case TRANSACTION_onAssociationCompleted:
        {
          this.onAssociationCompleted();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.IAssociationCallback
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
      /** Triggered when IHU starts advertising for association successfully. */
      @Override public void onAssociationStartSuccess(com.google.android.connecteddevice.model.StartAssociationResponse response) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, response, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociationStartSuccess, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when IHU failed to start advertising for association. */
      @Override public void onAssociationStartFailure() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociationStartFailure, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when an error has been encountered during association with a new device. */
      @Override public void onAssociationError(int error) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(error);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociationError, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /**  Triggered when a pairing code is available to be present. */
      @Override public void onVerificationCodeAvailable(java.lang.String code) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(code);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onVerificationCodeAvailable, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Triggered when the association has completed */
      @Override public void onAssociationCompleted() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onAssociationCompleted, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onAssociationStartSuccess = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onAssociationStartFailure = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onAssociationError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onVerificationCodeAvailable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_onAssociationCompleted = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.IAssociationCallback";
  /** Triggered when IHU starts advertising for association successfully. */
  public void onAssociationStartSuccess(com.google.android.connecteddevice.model.StartAssociationResponse response) throws android.os.RemoteException;
  /** Triggered when IHU failed to start advertising for association. */
  public void onAssociationStartFailure() throws android.os.RemoteException;
  /** Triggered when an error has been encountered during association with a new device. */
  public void onAssociationError(int error) throws android.os.RemoteException;
  /**  Triggered when a pairing code is available to be present. */
  public void onVerificationCodeAvailable(java.lang.String code) throws android.os.RemoteException;
  /** Triggered when the association has completed */
  public void onAssociationCompleted() throws android.os.RemoteException;
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
