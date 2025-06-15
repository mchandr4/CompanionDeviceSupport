/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.api.external;
/**
 * Triggered events for a connected companion device.
 * 
 * To maintain backward and forward compatibility, this class should not pass
 * customized parcelables. Instead please use supported primitive types
 * mentioned in the Android official doc.
 * See <a href="https://developer.android.com/guide/components/aidl">Android AIDL</a>
 * 
 * Only make additive changes to maintain backward compatibility.
 * The added function needs to be assigned the transaction value noted below,
 * and the value needs to be appropriately incremented.
 * 
 * Next transaction value: 3
 */
// TODO Link to the internal documentation on rules for modifying
// this class.
public interface ISafeDeviceCallback extends android.os.IInterface
{
  /** Default implementation for ISafeDeviceCallback. */
  public static class Default implements com.google.android.connecteddevice.api.external.ISafeDeviceCallback
  {
    /**
     * Triggered when secure channel has been established on a device.
     * Encrypted messaging now available.
     * 
     * @param deviceId that established the secure channel.
     */
    @Override public void onSecureChannelEstablished(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /**
     * Triggered when a new message is received from a connected device.
     * 
     * @param deviceId from which the message is received.
     * @param message byte array of the message received.
     */
    @Override public void onMessageReceived(java.lang.String deviceId, byte[] message) throws android.os.RemoteException
    {
    }
    /**
     * Triggered when an error has occurred for a connected device.
     * 
     * @param deviceId of the device which the error occured for.
     * @param error code {@link Errors} of the error happened.
     */
    @Override public void onDeviceError(java.lang.String deviceId, int error) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.api.external.ISafeDeviceCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.api.external.ISafeDeviceCallback interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.api.external.ISafeDeviceCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.api.external.ISafeDeviceCallback))) {
        return ((com.google.android.connecteddevice.api.external.ISafeDeviceCallback)iin);
      }
      return new com.google.android.connecteddevice.api.external.ISafeDeviceCallback.Stub.Proxy(obj);
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
        case TRANSACTION_onSecureChannelEstablished:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.onSecureChannelEstablished(_arg0);
          break;
        }
        case TRANSACTION_onMessageReceived:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          byte[] _arg1;
          _arg1 = data.createByteArray();
          this.onMessageReceived(_arg0, _arg1);
          break;
        }
        case TRANSACTION_onDeviceError:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          this.onDeviceError(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.google.android.connecteddevice.api.external.ISafeDeviceCallback
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
       * Triggered when secure channel has been established on a device.
       * Encrypted messaging now available.
       * 
       * @param deviceId that established the secure channel.
       */
      @Override public void onSecureChannelEstablished(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onSecureChannelEstablished, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /**
       * Triggered when a new message is received from a connected device.
       * 
       * @param deviceId from which the message is received.
       * @param message byte array of the message received.
       */
      @Override public void onMessageReceived(java.lang.String deviceId, byte[] message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          _data.writeByteArray(message);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onMessageReceived, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /**
       * Triggered when an error has occurred for a connected device.
       * 
       * @param deviceId of the device which the error occured for.
       * @param error code {@link Errors} of the error happened.
       */
      @Override public void onDeviceError(java.lang.String deviceId, int error) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          _data.writeInt(error);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDeviceError, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onSecureChannelEstablished = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onMessageReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onDeviceError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.api.external.ISafeDeviceCallback";
  /**
   * Triggered when secure channel has been established on a device.
   * Encrypted messaging now available.
   * 
   * @param deviceId that established the secure channel.
   */
  public void onSecureChannelEstablished(java.lang.String deviceId) throws android.os.RemoteException;
  /**
   * Triggered when a new message is received from a connected device.
   * 
   * @param deviceId from which the message is received.
   * @param message byte array of the message received.
   */
  public void onMessageReceived(java.lang.String deviceId, byte[] message) throws android.os.RemoteException;
  /**
   * Triggered when an error has occurred for a connected device.
   * 
   * @param deviceId of the device which the error occured for.
   * @param error code {@link Errors} of the error happened.
   */
  public void onDeviceError(java.lang.String deviceId, int error) throws android.os.RemoteException;
}
