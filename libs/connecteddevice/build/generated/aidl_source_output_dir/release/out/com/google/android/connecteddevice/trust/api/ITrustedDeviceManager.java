/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.google.android.connecteddevice.trust.api;
/**
 * Manager of trusted devices with the car to be used by any service/activity that needs to interact
 * with trusted devices.
 */
public interface ITrustedDeviceManager extends android.os.IInterface
{
  /** Default implementation for ITrustedDeviceManager. */
  public static class Default implements com.google.android.connecteddevice.trust.api.ITrustedDeviceManager
  {
    /** Send unlock request to the mobile device. */
    @Override public void sendUnlockRequest() throws android.os.RemoteException
    {
    }
    /** Indicate the escrow token has been added for a user and corresponding handle. */
    @Override public void onEscrowTokenAdded(int userId, long handle) throws android.os.RemoteException
    {
    }
    /** Indicate the escrow token has been activated for a user and corresponding handle. */
    @Override public void onEscrowTokenActivated(int userId, long handle) throws android.os.RemoteException
    {
    }
    /** Indicate when the user has successfully confirmed their credential. */
    @Override public void onCredentialVerified() throws android.os.RemoteException
    {
    }
    /** Indicate the device has been unlocked for current user. */
    @Override public void onUserUnlocked() throws android.os.RemoteException
    {
    }
    /** Register a new callback for trusted device events. */
    @Override public void registerTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /** Remove a previously registered callback. */
    @Override public void unregisterTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException
    {
    }
    /** Register a new callback for enrollment triggered events. */
    @Override public void registerTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException
    {
    }
    /** Remove a previously registered callback. */
    @Override public void unregisterTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException
    {
    }
    /** Register a new callback for trusted device enrollment notification request. */
    @Override public void registerTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException
    {
    }
    /** Remove a new callback for trusted device enrollment notification request. */
    @Override public void unregisterTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException
    {
    }
    /** Set a delegate for TrustAgent operation calls. */
    @Override public void setTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate) throws android.os.RemoteException
    {
    }
    /** Remove a prevoiusly set delegate with device secure status. */
    @Override public void clearTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate, boolean isDeviceSecure) throws android.os.RemoteException
    {
    }
    /** Retrieves trusted devices for the active user. */
    @Override public void retrieveTrustedDevicesForActiveUser(com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener listener) throws android.os.RemoteException
    {
    }
    /** Removes a trusted device and invalidate any credentials associated with it. */
    @Override public void removeTrustedDevice(com.google.android.connecteddevice.trust.api.TrustedDevice trustedDevice) throws android.os.RemoteException
    {
    }
    /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
    @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getActiveUserConnectedDevices() throws android.os.RemoteException
    {
      return null;
    }
    /** Registers a new callback for associated device events. */
    @Override public void registerAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
    {
    }
    /** Removes a previously registered callback. */
    @Override public void unregisterAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
    {
    }
    /** Attempts to initiate trusted device enrollment on the phone with the given device id. */
    @Override public void initiateEnrollment(java.lang.String deviceId) throws android.os.RemoteException
    {
    }
    /**
     * Processes trusted device enrollment.
     * 
     * @param isDeviceSecure {@code true} if the car is secured with a lockscreen.
     */
    @Override public void processEnrollment(boolean isDeviceSecure) throws android.os.RemoteException
    {
    }
    /** Aborts an ongoing enrollment. */
    @Override public void abortEnrollment() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.google.android.connecteddevice.trust.api.ITrustedDeviceManager
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.google.android.connecteddevice.trust.api.ITrustedDeviceManager interface,
     * generating a proxy if needed.
     */
    public static com.google.android.connecteddevice.trust.api.ITrustedDeviceManager asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.google.android.connecteddevice.trust.api.ITrustedDeviceManager))) {
        return ((com.google.android.connecteddevice.trust.api.ITrustedDeviceManager)iin);
      }
      return new com.google.android.connecteddevice.trust.api.ITrustedDeviceManager.Stub.Proxy(obj);
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
        case TRANSACTION_sendUnlockRequest:
        {
          this.sendUnlockRequest();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onEscrowTokenAdded:
        {
          int _arg0;
          _arg0 = data.readInt();
          long _arg1;
          _arg1 = data.readLong();
          this.onEscrowTokenAdded(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onEscrowTokenActivated:
        {
          int _arg0;
          _arg0 = data.readInt();
          long _arg1;
          _arg1 = data.readLong();
          this.onEscrowTokenActivated(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onCredentialVerified:
        {
          this.onCredentialVerified();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onUserUnlocked:
        {
          this.onUserUnlocked();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerTrustedDeviceCallback:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.registerTrustedDeviceCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterTrustedDeviceCallback:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterTrustedDeviceCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerTrustedDeviceEnrollmentCallback:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback.Stub.asInterface(data.readStrongBinder());
          this.registerTrustedDeviceEnrollmentCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterTrustedDeviceEnrollmentCallback:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterTrustedDeviceEnrollmentCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerTrustedDeviceEnrollmentNotificationCallback:
        {
          com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback.Stub.asInterface(data.readStrongBinder());
          this.registerTrustedDeviceEnrollmentNotificationCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterTrustedDeviceEnrollmentNotificationCallback:
        {
          com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterTrustedDeviceEnrollmentNotificationCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setTrustedDeviceAgentDelegate:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate.Stub.asInterface(data.readStrongBinder());
          this.setTrustedDeviceAgentDelegate(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_clearTrustedDeviceAgentDelegate:
        {
          com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate.Stub.asInterface(data.readStrongBinder());
          boolean _arg1;
          _arg1 = (0!=data.readInt());
          this.clearTrustedDeviceAgentDelegate(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_retrieveTrustedDevicesForActiveUser:
        {
          com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener _arg0;
          _arg0 = com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener.Stub.asInterface(data.readStrongBinder());
          this.retrieveTrustedDevicesForActiveUser(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeTrustedDevice:
        {
          com.google.android.connecteddevice.trust.api.TrustedDevice _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.google.android.connecteddevice.trust.api.TrustedDevice.CREATOR);
          this.removeTrustedDevice(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getActiveUserConnectedDevices:
        {
          java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result = this.getActiveUserConnectedDevices();
          reply.writeNoException();
          _Parcel.writeTypedList(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_registerAssociatedDeviceCallback:
        {
          com.google.android.connecteddevice.api.IDeviceAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IDeviceAssociationCallback.Stub.asInterface(data.readStrongBinder());
          this.registerAssociatedDeviceCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterAssociatedDeviceCallback:
        {
          com.google.android.connecteddevice.api.IDeviceAssociationCallback _arg0;
          _arg0 = com.google.android.connecteddevice.api.IDeviceAssociationCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterAssociatedDeviceCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_initiateEnrollment:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.initiateEnrollment(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_processEnrollment:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          this.processEnrollment(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_abortEnrollment:
        {
          this.abortEnrollment();
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
    private static class Proxy implements com.google.android.connecteddevice.trust.api.ITrustedDeviceManager
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
      /** Send unlock request to the mobile device. */
      @Override public void sendUnlockRequest() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendUnlockRequest, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Indicate the escrow token has been added for a user and corresponding handle. */
      @Override public void onEscrowTokenAdded(int userId, long handle) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(userId);
          _data.writeLong(handle);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onEscrowTokenAdded, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Indicate the escrow token has been activated for a user and corresponding handle. */
      @Override public void onEscrowTokenActivated(int userId, long handle) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(userId);
          _data.writeLong(handle);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onEscrowTokenActivated, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Indicate when the user has successfully confirmed their credential. */
      @Override public void onCredentialVerified() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onCredentialVerified, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Indicate the device has been unlocked for current user. */
      @Override public void onUserUnlocked() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onUserUnlocked, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Register a new callback for trusted device events. */
      @Override public void registerTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerTrustedDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove a previously registered callback. */
      @Override public void unregisterTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterTrustedDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Register a new callback for enrollment triggered events. */
      @Override public void registerTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerTrustedDeviceEnrollmentCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove a previously registered callback. */
      @Override public void unregisterTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterTrustedDeviceEnrollmentCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Register a new callback for trusted device enrollment notification request. */
      @Override public void registerTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerTrustedDeviceEnrollmentNotificationCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove a new callback for trusted device enrollment notification request. */
      @Override public void unregisterTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterTrustedDeviceEnrollmentNotificationCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Set a delegate for TrustAgent operation calls. */
      @Override public void setTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(trustAgentDelegate);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setTrustedDeviceAgentDelegate, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Remove a prevoiusly set delegate with device secure status. */
      @Override public void clearTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate, boolean isDeviceSecure) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(trustAgentDelegate);
          _data.writeInt(((isDeviceSecure)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_clearTrustedDeviceAgentDelegate, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Retrieves trusted devices for the active user. */
      @Override public void retrieveTrustedDevicesForActiveUser(com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_retrieveTrustedDevicesForActiveUser, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Removes a trusted device and invalidate any credentials associated with it. */
      @Override public void removeTrustedDevice(com.google.android.connecteddevice.trust.api.TrustedDevice trustedDevice) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, trustedDevice, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeTrustedDevice, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
      @Override public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getActiveUserConnectedDevices() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getActiveUserConnectedDevices, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createTypedArrayList(com.google.android.connecteddevice.model.ConnectedDevice.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Registers a new callback for associated device events. */
      @Override public void registerAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerAssociatedDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Removes a previously registered callback. */
      @Override public void unregisterAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterAssociatedDeviceCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Attempts to initiate trusted device enrollment on the phone with the given device id. */
      @Override public void initiateEnrollment(java.lang.String deviceId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(deviceId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_initiateEnrollment, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * Processes trusted device enrollment.
       * 
       * @param isDeviceSecure {@code true} if the car is secured with a lockscreen.
       */
      @Override public void processEnrollment(boolean isDeviceSecure) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((isDeviceSecure)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_processEnrollment, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Aborts an ongoing enrollment. */
      @Override public void abortEnrollment() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_abortEnrollment, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_sendUnlockRequest = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onEscrowTokenAdded = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onEscrowTokenActivated = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onCredentialVerified = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_onUserUnlocked = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_registerTrustedDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_unregisterTrustedDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_registerTrustedDeviceEnrollmentCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_unregisterTrustedDeviceEnrollmentCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_registerTrustedDeviceEnrollmentNotificationCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_unregisterTrustedDeviceEnrollmentNotificationCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_setTrustedDeviceAgentDelegate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_clearTrustedDeviceAgentDelegate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_retrieveTrustedDevicesForActiveUser = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_removeTrustedDevice = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_getActiveUserConnectedDevices = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_registerAssociatedDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_unregisterAssociatedDeviceCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_initiateEnrollment = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_processEnrollment = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_abortEnrollment = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
  }
  public static final java.lang.String DESCRIPTOR = "com.google.android.connecteddevice.trust.api.ITrustedDeviceManager";
  /** Send unlock request to the mobile device. */
  public void sendUnlockRequest() throws android.os.RemoteException;
  /** Indicate the escrow token has been added for a user and corresponding handle. */
  public void onEscrowTokenAdded(int userId, long handle) throws android.os.RemoteException;
  /** Indicate the escrow token has been activated for a user and corresponding handle. */
  public void onEscrowTokenActivated(int userId, long handle) throws android.os.RemoteException;
  /** Indicate when the user has successfully confirmed their credential. */
  public void onCredentialVerified() throws android.os.RemoteException;
  /** Indicate the device has been unlocked for current user. */
  public void onUserUnlocked() throws android.os.RemoteException;
  /** Register a new callback for trusted device events. */
  public void registerTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException;
  /** Remove a previously registered callback. */
  public void unregisterTrustedDeviceCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceCallback callback) throws android.os.RemoteException;
  /** Register a new callback for enrollment triggered events. */
  public void registerTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException;
  /** Remove a previously registered callback. */
  public void unregisterTrustedDeviceEnrollmentCallback(com.google.android.connecteddevice.trust.api.ITrustedDeviceEnrollmentCallback callback) throws android.os.RemoteException;
  /** Register a new callback for trusted device enrollment notification request. */
  public void registerTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException;
  /** Remove a new callback for trusted device enrollment notification request. */
  public void unregisterTrustedDeviceEnrollmentNotificationCallback(com.google.android.connecteddevice.trust.api.IOnTrustedDeviceEnrollmentNotificationCallback callback) throws android.os.RemoteException;
  /** Set a delegate for TrustAgent operation calls. */
  public void setTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate) throws android.os.RemoteException;
  /** Remove a prevoiusly set delegate with device secure status. */
  public void clearTrustedDeviceAgentDelegate(com.google.android.connecteddevice.trust.api.ITrustedDeviceAgentDelegate trustAgentDelegate, boolean isDeviceSecure) throws android.os.RemoteException;
  /** Retrieves trusted devices for the active user. */
  public void retrieveTrustedDevicesForActiveUser(com.google.android.connecteddevice.trust.api.IOnTrustedDevicesRetrievedListener listener) throws android.os.RemoteException;
  /** Removes a trusted device and invalidate any credentials associated with it. */
  public void removeTrustedDevice(com.google.android.connecteddevice.trust.api.TrustedDevice trustedDevice) throws android.os.RemoteException;
  /** Returns {@link List<ConnectedDevice>} of devices currently connected. */
  public java.util.List<com.google.android.connecteddevice.model.ConnectedDevice> getActiveUserConnectedDevices() throws android.os.RemoteException;
  /** Registers a new callback for associated device events. */
  public void registerAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException;
  /** Removes a previously registered callback. */
  public void unregisterAssociatedDeviceCallback(com.google.android.connecteddevice.api.IDeviceAssociationCallback callback) throws android.os.RemoteException;
  /** Attempts to initiate trusted device enrollment on the phone with the given device id. */
  public void initiateEnrollment(java.lang.String deviceId) throws android.os.RemoteException;
  /**
   * Processes trusted device enrollment.
   * 
   * @param isDeviceSecure {@code true} if the car is secured with a lockscreen.
   */
  public void processEnrollment(boolean isDeviceSecure) throws android.os.RemoteException;
  /** Aborts an ongoing enrollment. */
  public void abortEnrollment() throws android.os.RemoteException;
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
