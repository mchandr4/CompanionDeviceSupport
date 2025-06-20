// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: verification_code.proto

package com.google.android.companionprotos;

/**
 * <pre>
 * Potential states of the verification code.
 * </pre>
 *
 * Protobuf enum {@code com.google.companionprotos.VerificationCodeState}
 */
public enum VerificationCodeState
    implements com.google.protobuf.Internal.EnumLite {
  /**
   * <code>VERIFICATION_CODE_STATE_UNKNOWN = 0;</code>
   */
  VERIFICATION_CODE_STATE_UNKNOWN(0),
  /**
   * <pre>
   * OOB transfer was successful and verification will be performed on the
   * encrypted value.
   * </pre>
   *
   * <code>OOB_VERIFICATION = 1;</code>
   */
  OOB_VERIFICATION(1),
  /**
   * <pre>
   * OOB transfer did not take place and the code should be visually displayed
   * to the user.
   * </pre>
   *
   * <code>VISUAL_VERIFICATION = 2;</code>
   */
  VISUAL_VERIFICATION(2),
  /**
   * <pre>
   * The visual code has been confirmed by the user.
   * </pre>
   *
   * <code>VISUAL_CONFIRMATION = 3;</code>
   */
  VISUAL_CONFIRMATION(3),
  UNRECOGNIZED(-1),
  ;

  /**
   * <code>VERIFICATION_CODE_STATE_UNKNOWN = 0;</code>
   */
  public static final int VERIFICATION_CODE_STATE_UNKNOWN_VALUE = 0;
  /**
   * <pre>
   * OOB transfer was successful and verification will be performed on the
   * encrypted value.
   * </pre>
   *
   * <code>OOB_VERIFICATION = 1;</code>
   */
  public static final int OOB_VERIFICATION_VALUE = 1;
  /**
   * <pre>
   * OOB transfer did not take place and the code should be visually displayed
   * to the user.
   * </pre>
   *
   * <code>VISUAL_VERIFICATION = 2;</code>
   */
  public static final int VISUAL_VERIFICATION_VALUE = 2;
  /**
   * <pre>
   * The visual code has been confirmed by the user.
   * </pre>
   *
   * <code>VISUAL_CONFIRMATION = 3;</code>
   */
  public static final int VISUAL_CONFIRMATION_VALUE = 3;


  @java.lang.Override
  public final int getNumber() {
    if (this == UNRECOGNIZED) {
      throw new java.lang.IllegalArgumentException(
          "Can't get the number of an unknown enum value.");
    }
    return value;
  }

  /**
   * @param value The number of the enum to look for.
   * @return The enum associated with the given number.
   * @deprecated Use {@link #forNumber(int)} instead.
   */
  @java.lang.Deprecated
  public static VerificationCodeState valueOf(int value) {
    return forNumber(value);
  }

  public static VerificationCodeState forNumber(int value) {
    switch (value) {
      case 0: return VERIFICATION_CODE_STATE_UNKNOWN;
      case 1: return OOB_VERIFICATION;
      case 2: return VISUAL_VERIFICATION;
      case 3: return VISUAL_CONFIRMATION;
      default: return null;
    }
  }

  public static com.google.protobuf.Internal.EnumLiteMap<VerificationCodeState>
      internalGetValueMap() {
    return internalValueMap;
  }
  private static final com.google.protobuf.Internal.EnumLiteMap<
      VerificationCodeState> internalValueMap =
        new com.google.protobuf.Internal.EnumLiteMap<VerificationCodeState>() {
          @java.lang.Override
          public VerificationCodeState findValueByNumber(int number) {
            return VerificationCodeState.forNumber(number);
          }
        };

  public static com.google.protobuf.Internal.EnumVerifier 
      internalGetVerifier() {
    return VerificationCodeStateVerifier.INSTANCE;
  }

  private static final class VerificationCodeStateVerifier implements 
       com.google.protobuf.Internal.EnumVerifier { 
          static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new VerificationCodeStateVerifier();
          @java.lang.Override
          public boolean isInRange(int number) {
            return VerificationCodeState.forNumber(number) != null;
          }
        };

  private final int value;

  private VerificationCodeState(int value) {
    this.value = value;
  }

  // @@protoc_insertion_point(enum_scope:com.google.companionprotos.VerificationCodeState)
}

