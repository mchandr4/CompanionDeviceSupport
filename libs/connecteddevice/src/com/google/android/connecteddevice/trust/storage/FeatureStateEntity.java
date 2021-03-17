package com.google.android.connecteddevice.trust.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.Objects;

/** Table entity representing a trusted device state that needs to be synced to a remote phone. */
@Entity(tableName = "feature_state")
public class FeatureStateEntity {
  /** ID of trusted device to sync state with. */
  @PrimaryKey @NonNull public final String id;

  /** A state message to be sent as-is to the phone. */
  @NonNull public final byte[] state;

  public FeatureStateEntity(@NonNull String id, @NonNull byte[] state) {
    this.id = id;
    this.state = state;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof FeatureStateEntity)) {
      return false;
    }

    FeatureStateEntity other = (FeatureStateEntity) obj;
    return id.equals(other.id) && Arrays.equals(state, other.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, Arrays.hashCode(state));
  }
}
