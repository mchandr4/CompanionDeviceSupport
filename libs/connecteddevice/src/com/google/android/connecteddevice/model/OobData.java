package com.google.android.connecteddevice.model;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Arrays;

/** Data class holding required data during out-of-band data exchange. */
public class OobData implements Parcelable {

  private final byte[] encryptionKey;

  private final byte[] ihuIv;

  private final byte[] mobileIv;

  /**
   * Create a new OobData.
   *
   * @param encryptionKey The key shared between IHU and mobile.
   * @param ihuIv The car side Iv which is used to encrypt and decrypt car's message.
   * @param mobileIv The mobile side Iv which is used to encrypt and decrypt car's message.
   */
  public OobData(@NonNull byte[] encryptionKey, @NonNull byte[] ihuIv, @NonNull byte[] mobileIv) {
    this.encryptionKey = encryptionKey;
    this.ihuIv = ihuIv;
    this.mobileIv = mobileIv;
  }

  private OobData(Parcel in) {
    this(in.createByteArray(), in.createByteArray(), in.createByteArray());
  }

  /** Returns the encryption key of the OOB data. */
  @NonNull
  public byte[] getEncryptionKey() {
    return encryptionKey;
  }

  /** Returns the car IV of the OOB data. */
  @NonNull
  public byte[] getIhuIv() {
    return ihuIv;
  }

  /** Returns the mobile IV of the OOB data. */
  @NonNull
  public byte[] getMobileIv() {
    return mobileIv;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof OobData)) {
      return false;
    }
    OobData response = (OobData) obj;
    return Arrays.equals(encryptionKey, response.encryptionKey)
        && Arrays.equals(ihuIv, response.getIhuIv())
        && Arrays.equals(mobileIv, response.getMobileIv());
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(encryptionKey);
    result = 31 * result + Arrays.hashCode(ihuIv);
    result = 31 * result + Arrays.hashCode(mobileIv);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByteArray(encryptionKey);
    dest.writeByteArray(ihuIv);
    dest.writeByteArray(mobileIv);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<OobData> CREATOR =
      new Creator<OobData>() {
        @Override
        public OobData createFromParcel(Parcel in) {
          return new OobData(in);
        }

        @Override
        public OobData[] newArray(int size) {
          return new OobData[size];
        }
      };
}
