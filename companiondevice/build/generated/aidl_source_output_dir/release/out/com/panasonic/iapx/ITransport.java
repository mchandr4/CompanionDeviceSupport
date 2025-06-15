/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.panasonic.iapx;
public interface ITransport extends android.os.IInterface
{
  /** Default implementation for ITransport. */
  public static class Default implements com.panasonic.iapx.ITransport
  {
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.panasonic.iapx.ITransport
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.panasonic.iapx.ITransport interface,
     * generating a proxy if needed.
     */
    public static com.panasonic.iapx.ITransport asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.panasonic.iapx.ITransport))) {
        return ((com.panasonic.iapx.ITransport)iin);
      }
      return new com.panasonic.iapx.ITransport.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
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
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements com.panasonic.iapx.ITransport
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
    }
  }
  public static final java.lang.String DESCRIPTOR = "com.panasonic.iapx.ITransport";
  public static final int kTypeSerial = 0;
  public static final int kTypeUSBHostMode = 1;
  public static final int kTypeUSBDeviceMode = 2;
  public static final int kTypeBluetooth = 3;
  public static final int kTypeCarPlay = 4;
}
