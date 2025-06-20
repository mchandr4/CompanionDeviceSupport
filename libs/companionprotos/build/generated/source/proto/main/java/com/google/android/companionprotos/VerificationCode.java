// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: verification_code.proto

package com.google.android.companionprotos;

/**
 * <pre>
 * Message used to verify the security handshake.
 * </pre>
 *
 * Protobuf type {@code com.google.companionprotos.VerificationCode}
 */
public  final class VerificationCode extends
    com.google.protobuf.GeneratedMessageLite<
        VerificationCode, VerificationCode.Builder> implements
    // @@protoc_insertion_point(message_implements:com.google.companionprotos.VerificationCode)
    VerificationCodeOrBuilder {
  private VerificationCode() {
    payload_ = com.google.protobuf.ByteString.EMPTY;
  }
  public static final int STATE_FIELD_NUMBER = 1;
  private int state_;
  /**
   * <pre>
   * State of verification code on the phone.
   * </pre>
   *
   * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
   * @return The enum numeric value on the wire for state.
   */
  @java.lang.Override
  public int getStateValue() {
    return state_;
  }
  /**
   * <pre>
   * State of verification code on the phone.
   * </pre>
   *
   * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
   * @return The state.
   */
  @java.lang.Override
  public com.google.android.companionprotos.VerificationCodeState getState() {
    com.google.android.companionprotos.VerificationCodeState result = com.google.android.companionprotos.VerificationCodeState.forNumber(state_);
    return result == null ? com.google.android.companionprotos.VerificationCodeState.UNRECOGNIZED : result;
  }
  /**
   * <pre>
   * State of verification code on the phone.
   * </pre>
   *
   * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
   * @param value The enum numeric value on the wire for state to set.
   */
  private void setStateValue(int value) {
      state_ = value;
  }
  /**
   * <pre>
   * State of verification code on the phone.
   * </pre>
   *
   * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
   * @param value The state to set.
   */
  private void setState(com.google.android.companionprotos.VerificationCodeState value) {
    state_ = value.getNumber();
    
  }
  /**
   * <pre>
   * State of verification code on the phone.
   * </pre>
   *
   * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
   */
  private void clearState() {
    
    state_ = 0;
  }

  public static final int PAYLOAD_FIELD_NUMBER = 2;
  private com.google.protobuf.ByteString payload_;
  /**
   * <pre>
   * Payload containing encrypted verification code if state is
   * OOB_VERIFICATION. Otherwise empty.
   * </pre>
   *
   * <code>bytes payload = 2;</code>
   * @return The payload.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString getPayload() {
    return payload_;
  }
  /**
   * <pre>
   * Payload containing encrypted verification code if state is
   * OOB_VERIFICATION. Otherwise empty.
   * </pre>
   *
   * <code>bytes payload = 2;</code>
   * @param value The payload to set.
   */
  private void setPayload(com.google.protobuf.ByteString value) {
    java.lang.Class<?> valueClass = value.getClass();
  
    payload_ = value;
  }
  /**
   * <pre>
   * Payload containing encrypted verification code if state is
   * OOB_VERIFICATION. Otherwise empty.
   * </pre>
   *
   * <code>bytes payload = 2;</code>
   */
  private void clearPayload() {
    
    payload_ = getDefaultInstance().getPayload();
  }

  public static com.google.android.companionprotos.VerificationCode parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.companionprotos.VerificationCode parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.VerificationCode parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.VerificationCode parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }

  public static Builder newBuilder() {
    return (Builder) DEFAULT_INSTANCE.createBuilder();
  }
  public static Builder newBuilder(com.google.android.companionprotos.VerificationCode prototype) {
    return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
  }

  /**
   * <pre>
   * Message used to verify the security handshake.
   * </pre>
   *
   * Protobuf type {@code com.google.companionprotos.VerificationCode}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageLite.Builder<
        com.google.android.companionprotos.VerificationCode, Builder> implements
      // @@protoc_insertion_point(builder_implements:com.google.companionprotos.VerificationCode)
      com.google.android.companionprotos.VerificationCodeOrBuilder {
    // Construct using com.google.android.companionprotos.VerificationCode.newBuilder()
    private Builder() {
      super(DEFAULT_INSTANCE);
    }


    /**
     * <pre>
     * State of verification code on the phone.
     * </pre>
     *
     * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
     * @return The enum numeric value on the wire for state.
     */
    @java.lang.Override
    public int getStateValue() {
      return instance.getStateValue();
    }
    /**
     * <pre>
     * State of verification code on the phone.
     * </pre>
     *
     * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
     * @param value The state to set.
     * @return This builder for chaining.
     */
    public Builder setStateValue(int value) {
      copyOnWrite();
      instance.setStateValue(value);
      return this;
    }
    /**
     * <pre>
     * State of verification code on the phone.
     * </pre>
     *
     * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
     * @return The state.
     */
    @java.lang.Override
    public com.google.android.companionprotos.VerificationCodeState getState() {
      return instance.getState();
    }
    /**
     * <pre>
     * State of verification code on the phone.
     * </pre>
     *
     * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
     * @param value The enum numeric value on the wire for state to set.
     * @return This builder for chaining.
     */
    public Builder setState(com.google.android.companionprotos.VerificationCodeState value) {
      copyOnWrite();
      instance.setState(value);
      return this;
    }
    /**
     * <pre>
     * State of verification code on the phone.
     * </pre>
     *
     * <code>.com.google.companionprotos.VerificationCodeState state = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearState() {
      copyOnWrite();
      instance.clearState();
      return this;
    }

    /**
     * <pre>
     * Payload containing encrypted verification code if state is
     * OOB_VERIFICATION. Otherwise empty.
     * </pre>
     *
     * <code>bytes payload = 2;</code>
     * @return The payload.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString getPayload() {
      return instance.getPayload();
    }
    /**
     * <pre>
     * Payload containing encrypted verification code if state is
     * OOB_VERIFICATION. Otherwise empty.
     * </pre>
     *
     * <code>bytes payload = 2;</code>
     * @param value The payload to set.
     * @return This builder for chaining.
     */
    public Builder setPayload(com.google.protobuf.ByteString value) {
      copyOnWrite();
      instance.setPayload(value);
      return this;
    }
    /**
     * <pre>
     * Payload containing encrypted verification code if state is
     * OOB_VERIFICATION. Otherwise empty.
     * </pre>
     *
     * <code>bytes payload = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearPayload() {
      copyOnWrite();
      instance.clearPayload();
      return this;
    }

    // @@protoc_insertion_point(builder_scope:com.google.companionprotos.VerificationCode)
  }
  @java.lang.Override
  @java.lang.SuppressWarnings({"unchecked", "fallthrough"})
  protected final java.lang.Object dynamicMethod(
      com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
      java.lang.Object arg0, java.lang.Object arg1) {
    switch (method) {
      case NEW_MUTABLE_INSTANCE: {
        return new com.google.android.companionprotos.VerificationCode();
      }
      case NEW_BUILDER: {
        return new Builder();
      }
      case BUILD_MESSAGE_INFO: {
          java.lang.Object[] objects = new java.lang.Object[] {
            "state_",
            "payload_",
          };
          java.lang.String info =
              "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000\u0001\f\u0002\n";
          return newMessageInfo(DEFAULT_INSTANCE, info, objects);
      }
      // fall through
      case GET_DEFAULT_INSTANCE: {
        return DEFAULT_INSTANCE;
      }
      case GET_PARSER: {
        com.google.protobuf.Parser<com.google.android.companionprotos.VerificationCode> parser = PARSER;
        if (parser == null) {
          synchronized (com.google.android.companionprotos.VerificationCode.class) {
            parser = PARSER;
            if (parser == null) {
              parser =
                  new DefaultInstanceBasedParser<com.google.android.companionprotos.VerificationCode>(
                      DEFAULT_INSTANCE);
              PARSER = parser;
            }
          }
        }
        return parser;
    }
    case GET_MEMOIZED_IS_INITIALIZED: {
      return (byte) 1;
    }
    case SET_MEMOIZED_IS_INITIALIZED: {
      return null;
    }
    }
    throw new UnsupportedOperationException();
  }


  // @@protoc_insertion_point(class_scope:com.google.companionprotos.VerificationCode)
  private static final com.google.android.companionprotos.VerificationCode DEFAULT_INSTANCE;
  static {
    VerificationCode defaultInstance = new VerificationCode();
    // New instances are implicitly immutable so no need to make
    // immutable.
    DEFAULT_INSTANCE = defaultInstance;
    com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
      VerificationCode.class, defaultInstance);
  }

  public static com.google.android.companionprotos.VerificationCode getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static volatile com.google.protobuf.Parser<VerificationCode> PARSER;

  public static com.google.protobuf.Parser<VerificationCode> parser() {
    return DEFAULT_INSTANCE.getParserForType();
  }
}

