// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: system_query.proto

package com.google.android.companionprotos;

/**
 * <pre>
 * The support status of a feature.
 * </pre>
 *
 * Protobuf type {@code com.google.companionprotos.FeatureSupportStatus}
 */
public  final class FeatureSupportStatus extends
    com.google.protobuf.GeneratedMessageLite<
        FeatureSupportStatus, FeatureSupportStatus.Builder> implements
    // @@protoc_insertion_point(message_implements:com.google.companionprotos.FeatureSupportStatus)
    FeatureSupportStatusOrBuilder {
  private FeatureSupportStatus() {
    featureId_ = "";
  }
  public static final int FEATURE_ID_FIELD_NUMBER = 1;
  private java.lang.String featureId_;
  /**
   * <pre>
   * The feature that is being queried.
   * </pre>
   *
   * <code>string feature_id = 1;</code>
   * @return The featureId.
   */
  @java.lang.Override
  public java.lang.String getFeatureId() {
    return featureId_;
  }
  /**
   * <pre>
   * The feature that is being queried.
   * </pre>
   *
   * <code>string feature_id = 1;</code>
   * @return The bytes for featureId.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getFeatureIdBytes() {
    return com.google.protobuf.ByteString.copyFromUtf8(featureId_);
  }
  /**
   * <pre>
   * The feature that is being queried.
   * </pre>
   *
   * <code>string feature_id = 1;</code>
   * @param value The featureId to set.
   */
  private void setFeatureId(
      java.lang.String value) {
    java.lang.Class<?> valueClass = value.getClass();
  
    featureId_ = value;
  }
  /**
   * <pre>
   * The feature that is being queried.
   * </pre>
   *
   * <code>string feature_id = 1;</code>
   */
  private void clearFeatureId() {
    
    featureId_ = getDefaultInstance().getFeatureId();
  }
  /**
   * <pre>
   * The feature that is being queried.
   * </pre>
   *
   * <code>string feature_id = 1;</code>
   * @param value The bytes for featureId to set.
   */
  private void setFeatureIdBytes(
      com.google.protobuf.ByteString value) {
    checkByteStringIsUtf8(value);
    featureId_ = value.toStringUtf8();
    
  }

  public static final int IS_SUPPORTED_FIELD_NUMBER = 2;
  private boolean isSupported_;
  /**
   * <pre>
   * Whether the feature is supported.
   * </pre>
   *
   * <code>bool is_supported = 2;</code>
   * @return The isSupported.
   */
  @java.lang.Override
  public boolean getIsSupported() {
    return isSupported_;
  }
  /**
   * <pre>
   * Whether the feature is supported.
   * </pre>
   *
   * <code>bool is_supported = 2;</code>
   * @param value The isSupported to set.
   */
  private void setIsSupported(boolean value) {
    
    isSupported_ = value;
  }
  /**
   * <pre>
   * Whether the feature is supported.
   * </pre>
   *
   * <code>bool is_supported = 2;</code>
   */
  private void clearIsSupported() {
    
    isSupported_ = false;
  }

  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.companionprotos.FeatureSupportStatus parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }

  public static Builder newBuilder() {
    return (Builder) DEFAULT_INSTANCE.createBuilder();
  }
  public static Builder newBuilder(com.google.android.companionprotos.FeatureSupportStatus prototype) {
    return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
  }

  /**
   * <pre>
   * The support status of a feature.
   * </pre>
   *
   * Protobuf type {@code com.google.companionprotos.FeatureSupportStatus}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageLite.Builder<
        com.google.android.companionprotos.FeatureSupportStatus, Builder> implements
      // @@protoc_insertion_point(builder_implements:com.google.companionprotos.FeatureSupportStatus)
      com.google.android.companionprotos.FeatureSupportStatusOrBuilder {
    // Construct using com.google.android.companionprotos.FeatureSupportStatus.newBuilder()
    private Builder() {
      super(DEFAULT_INSTANCE);
    }


    /**
     * <pre>
     * The feature that is being queried.
     * </pre>
     *
     * <code>string feature_id = 1;</code>
     * @return The featureId.
     */
    @java.lang.Override
    public java.lang.String getFeatureId() {
      return instance.getFeatureId();
    }
    /**
     * <pre>
     * The feature that is being queried.
     * </pre>
     *
     * <code>string feature_id = 1;</code>
     * @return The bytes for featureId.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getFeatureIdBytes() {
      return instance.getFeatureIdBytes();
    }
    /**
     * <pre>
     * The feature that is being queried.
     * </pre>
     *
     * <code>string feature_id = 1;</code>
     * @param value The featureId to set.
     * @return This builder for chaining.
     */
    public Builder setFeatureId(
        java.lang.String value) {
      copyOnWrite();
      instance.setFeatureId(value);
      return this;
    }
    /**
     * <pre>
     * The feature that is being queried.
     * </pre>
     *
     * <code>string feature_id = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearFeatureId() {
      copyOnWrite();
      instance.clearFeatureId();
      return this;
    }
    /**
     * <pre>
     * The feature that is being queried.
     * </pre>
     *
     * <code>string feature_id = 1;</code>
     * @param value The bytes for featureId to set.
     * @return This builder for chaining.
     */
    public Builder setFeatureIdBytes(
        com.google.protobuf.ByteString value) {
      copyOnWrite();
      instance.setFeatureIdBytes(value);
      return this;
    }

    /**
     * <pre>
     * Whether the feature is supported.
     * </pre>
     *
     * <code>bool is_supported = 2;</code>
     * @return The isSupported.
     */
    @java.lang.Override
    public boolean getIsSupported() {
      return instance.getIsSupported();
    }
    /**
     * <pre>
     * Whether the feature is supported.
     * </pre>
     *
     * <code>bool is_supported = 2;</code>
     * @param value The isSupported to set.
     * @return This builder for chaining.
     */
    public Builder setIsSupported(boolean value) {
      copyOnWrite();
      instance.setIsSupported(value);
      return this;
    }
    /**
     * <pre>
     * Whether the feature is supported.
     * </pre>
     *
     * <code>bool is_supported = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearIsSupported() {
      copyOnWrite();
      instance.clearIsSupported();
      return this;
    }

    // @@protoc_insertion_point(builder_scope:com.google.companionprotos.FeatureSupportStatus)
  }
  @java.lang.Override
  @java.lang.SuppressWarnings({"unchecked", "fallthrough"})
  protected final java.lang.Object dynamicMethod(
      com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
      java.lang.Object arg0, java.lang.Object arg1) {
    switch (method) {
      case NEW_MUTABLE_INSTANCE: {
        return new com.google.android.companionprotos.FeatureSupportStatus();
      }
      case NEW_BUILDER: {
        return new Builder();
      }
      case BUILD_MESSAGE_INFO: {
          java.lang.Object[] objects = new java.lang.Object[] {
            "featureId_",
            "isSupported_",
          };
          java.lang.String info =
              "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000\u0001\u0208\u0002\u0007" +
              "";
          return newMessageInfo(DEFAULT_INSTANCE, info, objects);
      }
      // fall through
      case GET_DEFAULT_INSTANCE: {
        return DEFAULT_INSTANCE;
      }
      case GET_PARSER: {
        com.google.protobuf.Parser<com.google.android.companionprotos.FeatureSupportStatus> parser = PARSER;
        if (parser == null) {
          synchronized (com.google.android.companionprotos.FeatureSupportStatus.class) {
            parser = PARSER;
            if (parser == null) {
              parser =
                  new DefaultInstanceBasedParser<com.google.android.companionprotos.FeatureSupportStatus>(
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


  // @@protoc_insertion_point(class_scope:com.google.companionprotos.FeatureSupportStatus)
  private static final com.google.android.companionprotos.FeatureSupportStatus DEFAULT_INSTANCE;
  static {
    FeatureSupportStatus defaultInstance = new FeatureSupportStatus();
    // New instances are implicitly immutable so no need to make
    // immutable.
    DEFAULT_INSTANCE = defaultInstance;
    com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
      FeatureSupportStatus.class, defaultInstance);
  }

  public static com.google.android.companionprotos.FeatureSupportStatus getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static volatile com.google.protobuf.Parser<FeatureSupportStatus> PARSER;

  public static com.google.protobuf.Parser<FeatureSupportStatus> parser() {
    return DEFAULT_INSTANCE.getParserForType();
  }
}

