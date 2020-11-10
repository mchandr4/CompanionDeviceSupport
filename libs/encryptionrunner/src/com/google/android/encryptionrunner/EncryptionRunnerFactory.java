package com.google.android.encryptionrunner;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;

/** Factory that creates encryption runner. */
public class EncryptionRunnerFactory {
  private EncryptionRunnerFactory() {}

  /** Types of {@link EncryptionRunner}s. */
  @IntDef({EncryptionRunnerType.UKEY2, EncryptionRunnerType.OOB_UKEY2})
  public @interface EncryptionRunnerType {
    /** Use Ukey2 as underlying key exchange. */
    int UKEY2 = 0;
    /** Use Ukey2 and an out of band channel as underlying key exchange. */
    int OOB_UKEY2 = 1;
  }

  /** Creates a new {@link EncryptionRunner} based on type. */
  public static EncryptionRunner newRunner(@EncryptionRunnerType int type) {
    switch (type) {
      case EncryptionRunnerType.UKEY2:
        return new Ukey2EncryptionRunner();
      case EncryptionRunnerType.OOB_UKEY2:
        return new OobUkey2EncryptionRunner();
      default:
        throw new IllegalArgumentException("Unknown EncryptionRunnerType: " + type);
    }
  }

  /**
   * Creates a new {@link EncryptionRunner} one that doesn't actually do encryption but is useful
   * for testing.
   */
  @VisibleForTesting
  public static EncryptionRunner newFakeRunner() {
    return new FakeEncryptionRunner();
  }

  /**
   * Creates a new {@link EncryptionRunner} that doesn't actually do encryption but is useful for
   * out of band association testing.
   */
  @VisibleForTesting
  public static EncryptionRunner newOobFakeRunner() {
    return new OobFakeEncryptionRunner();
  }
}
