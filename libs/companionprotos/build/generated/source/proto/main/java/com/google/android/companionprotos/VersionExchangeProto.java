// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: version_exchange.proto

package com.google.android.companionprotos;

public final class VersionExchangeProto {
  private VersionExchangeProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }
  public interface VersionExchangeOrBuilder extends
      // @@protoc_insertion_point(interface_extends:com.google.companionprotos.VersionExchange)
      com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <pre>
     * Minimum supported protobuf version.
     * </pre>
     *
     * <code>int32 min_supported_messaging_version = 1;</code>
     * @return The minSupportedMessagingVersion.
     */
    int getMinSupportedMessagingVersion();

    /**
     * <pre>
     * Maximum supported protobuf version.
     * </pre>
     *
     * <code>int32 max_supported_messaging_version = 2;</code>
     * @return The maxSupportedMessagingVersion.
     */
    int getMaxSupportedMessagingVersion();

    /**
     * <pre>
     * Minimum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 min_supported_security_version = 3;</code>
     * @return The minSupportedSecurityVersion.
     */
    int getMinSupportedSecurityVersion();

    /**
     * <pre>
     * Maximum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 max_supported_security_version = 4;</code>
     * @return The maxSupportedSecurityVersion.
     */
    int getMaxSupportedSecurityVersion();
  }
  /**
   * Protobuf type {@code com.google.companionprotos.VersionExchange}
   */
  public  static final class VersionExchange extends
      com.google.protobuf.GeneratedMessageLite<
          VersionExchange, VersionExchange.Builder> implements
      // @@protoc_insertion_point(message_implements:com.google.companionprotos.VersionExchange)
      VersionExchangeOrBuilder {
    private VersionExchange() {
    }
    public static final int MIN_SUPPORTED_MESSAGING_VERSION_FIELD_NUMBER = 1;
    private int minSupportedMessagingVersion_;
    /**
     * <pre>
     * Minimum supported protobuf version.
     * </pre>
     *
     * <code>int32 min_supported_messaging_version = 1;</code>
     * @return The minSupportedMessagingVersion.
     */
    @java.lang.Override
    public int getMinSupportedMessagingVersion() {
      return minSupportedMessagingVersion_;
    }
    /**
     * <pre>
     * Minimum supported protobuf version.
     * </pre>
     *
     * <code>int32 min_supported_messaging_version = 1;</code>
     * @param value The minSupportedMessagingVersion to set.
     */
    private void setMinSupportedMessagingVersion(int value) {
      
      minSupportedMessagingVersion_ = value;
    }
    /**
     * <pre>
     * Minimum supported protobuf version.
     * </pre>
     *
     * <code>int32 min_supported_messaging_version = 1;</code>
     */
    private void clearMinSupportedMessagingVersion() {
      
      minSupportedMessagingVersion_ = 0;
    }

    public static final int MAX_SUPPORTED_MESSAGING_VERSION_FIELD_NUMBER = 2;
    private int maxSupportedMessagingVersion_;
    /**
     * <pre>
     * Maximum supported protobuf version.
     * </pre>
     *
     * <code>int32 max_supported_messaging_version = 2;</code>
     * @return The maxSupportedMessagingVersion.
     */
    @java.lang.Override
    public int getMaxSupportedMessagingVersion() {
      return maxSupportedMessagingVersion_;
    }
    /**
     * <pre>
     * Maximum supported protobuf version.
     * </pre>
     *
     * <code>int32 max_supported_messaging_version = 2;</code>
     * @param value The maxSupportedMessagingVersion to set.
     */
    private void setMaxSupportedMessagingVersion(int value) {
      
      maxSupportedMessagingVersion_ = value;
    }
    /**
     * <pre>
     * Maximum supported protobuf version.
     * </pre>
     *
     * <code>int32 max_supported_messaging_version = 2;</code>
     */
    private void clearMaxSupportedMessagingVersion() {
      
      maxSupportedMessagingVersion_ = 0;
    }

    public static final int MIN_SUPPORTED_SECURITY_VERSION_FIELD_NUMBER = 3;
    private int minSupportedSecurityVersion_;
    /**
     * <pre>
     * Minimum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 min_supported_security_version = 3;</code>
     * @return The minSupportedSecurityVersion.
     */
    @java.lang.Override
    public int getMinSupportedSecurityVersion() {
      return minSupportedSecurityVersion_;
    }
    /**
     * <pre>
     * Minimum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 min_supported_security_version = 3;</code>
     * @param value The minSupportedSecurityVersion to set.
     */
    private void setMinSupportedSecurityVersion(int value) {
      
      minSupportedSecurityVersion_ = value;
    }
    /**
     * <pre>
     * Minimum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 min_supported_security_version = 3;</code>
     */
    private void clearMinSupportedSecurityVersion() {
      
      minSupportedSecurityVersion_ = 0;
    }

    public static final int MAX_SUPPORTED_SECURITY_VERSION_FIELD_NUMBER = 4;
    private int maxSupportedSecurityVersion_;
    /**
     * <pre>
     * Maximum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 max_supported_security_version = 4;</code>
     * @return The maxSupportedSecurityVersion.
     */
    @java.lang.Override
    public int getMaxSupportedSecurityVersion() {
      return maxSupportedSecurityVersion_;
    }
    /**
     * <pre>
     * Maximum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 max_supported_security_version = 4;</code>
     * @param value The maxSupportedSecurityVersion to set.
     */
    private void setMaxSupportedSecurityVersion(int value) {
      
      maxSupportedSecurityVersion_ = value;
    }
    /**
     * <pre>
     * Maximum supported version of the encryption engine.
     * </pre>
     *
     * <code>int32 max_supported_security_version = 4;</code>
     */
    private void clearMaxSupportedSecurityVersion() {
      
      maxSupportedSecurityVersion_ = 0;
    }

    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
      return (Builder) DEFAULT_INSTANCE.createBuilder();
    }
    public static Builder newBuilder(com.google.android.companionprotos.VersionExchangeProto.VersionExchange prototype) {
      return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
    }

    /**
     * Protobuf type {@code com.google.companionprotos.VersionExchange}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          com.google.android.companionprotos.VersionExchangeProto.VersionExchange, Builder> implements
        // @@protoc_insertion_point(builder_implements:com.google.companionprotos.VersionExchange)
        com.google.android.companionprotos.VersionExchangeProto.VersionExchangeOrBuilder {
      // Construct using com.google.android.companionprotos.VersionExchangeProto.VersionExchange.newBuilder()
      private Builder() {
        super(DEFAULT_INSTANCE);
      }


      /**
       * <pre>
       * Minimum supported protobuf version.
       * </pre>
       *
       * <code>int32 min_supported_messaging_version = 1;</code>
       * @return The minSupportedMessagingVersion.
       */
      @java.lang.Override
      public int getMinSupportedMessagingVersion() {
        return instance.getMinSupportedMessagingVersion();
      }
      /**
       * <pre>
       * Minimum supported protobuf version.
       * </pre>
       *
       * <code>int32 min_supported_messaging_version = 1;</code>
       * @param value The minSupportedMessagingVersion to set.
       * @return This builder for chaining.
       */
      public Builder setMinSupportedMessagingVersion(int value) {
        copyOnWrite();
        instance.setMinSupportedMessagingVersion(value);
        return this;
      }
      /**
       * <pre>
       * Minimum supported protobuf version.
       * </pre>
       *
       * <code>int32 min_supported_messaging_version = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearMinSupportedMessagingVersion() {
        copyOnWrite();
        instance.clearMinSupportedMessagingVersion();
        return this;
      }

      /**
       * <pre>
       * Maximum supported protobuf version.
       * </pre>
       *
       * <code>int32 max_supported_messaging_version = 2;</code>
       * @return The maxSupportedMessagingVersion.
       */
      @java.lang.Override
      public int getMaxSupportedMessagingVersion() {
        return instance.getMaxSupportedMessagingVersion();
      }
      /**
       * <pre>
       * Maximum supported protobuf version.
       * </pre>
       *
       * <code>int32 max_supported_messaging_version = 2;</code>
       * @param value The maxSupportedMessagingVersion to set.
       * @return This builder for chaining.
       */
      public Builder setMaxSupportedMessagingVersion(int value) {
        copyOnWrite();
        instance.setMaxSupportedMessagingVersion(value);
        return this;
      }
      /**
       * <pre>
       * Maximum supported protobuf version.
       * </pre>
       *
       * <code>int32 max_supported_messaging_version = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearMaxSupportedMessagingVersion() {
        copyOnWrite();
        instance.clearMaxSupportedMessagingVersion();
        return this;
      }

      /**
       * <pre>
       * Minimum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 min_supported_security_version = 3;</code>
       * @return The minSupportedSecurityVersion.
       */
      @java.lang.Override
      public int getMinSupportedSecurityVersion() {
        return instance.getMinSupportedSecurityVersion();
      }
      /**
       * <pre>
       * Minimum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 min_supported_security_version = 3;</code>
       * @param value The minSupportedSecurityVersion to set.
       * @return This builder for chaining.
       */
      public Builder setMinSupportedSecurityVersion(int value) {
        copyOnWrite();
        instance.setMinSupportedSecurityVersion(value);
        return this;
      }
      /**
       * <pre>
       * Minimum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 min_supported_security_version = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearMinSupportedSecurityVersion() {
        copyOnWrite();
        instance.clearMinSupportedSecurityVersion();
        return this;
      }

      /**
       * <pre>
       * Maximum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 max_supported_security_version = 4;</code>
       * @return The maxSupportedSecurityVersion.
       */
      @java.lang.Override
      public int getMaxSupportedSecurityVersion() {
        return instance.getMaxSupportedSecurityVersion();
      }
      /**
       * <pre>
       * Maximum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 max_supported_security_version = 4;</code>
       * @param value The maxSupportedSecurityVersion to set.
       * @return This builder for chaining.
       */
      public Builder setMaxSupportedSecurityVersion(int value) {
        copyOnWrite();
        instance.setMaxSupportedSecurityVersion(value);
        return this;
      }
      /**
       * <pre>
       * Maximum supported version of the encryption engine.
       * </pre>
       *
       * <code>int32 max_supported_security_version = 4;</code>
       * @return This builder for chaining.
       */
      public Builder clearMaxSupportedSecurityVersion() {
        copyOnWrite();
        instance.clearMaxSupportedSecurityVersion();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:com.google.companionprotos.VersionExchange)
    }
    @java.lang.Override
    @java.lang.SuppressWarnings({"unchecked", "fallthrough"})
    protected final java.lang.Object dynamicMethod(
        com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
        java.lang.Object arg0, java.lang.Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE: {
          return new com.google.android.companionprotos.VersionExchangeProto.VersionExchange();
        }
        case NEW_BUILDER: {
          return new Builder();
        }
        case BUILD_MESSAGE_INFO: {
            java.lang.Object[] objects = new java.lang.Object[] {
              "minSupportedMessagingVersion_",
              "maxSupportedMessagingVersion_",
              "minSupportedSecurityVersion_",
              "maxSupportedSecurityVersion_",
            };
            java.lang.String info =
                "\u0000\u0004\u0000\u0000\u0001\u0004\u0004\u0000\u0000\u0000\u0001\u0004\u0002\u0004" +
                "\u0003\u0004\u0004\u0004";
            return newMessageInfo(DEFAULT_INSTANCE, info, objects);
        }
        // fall through
        case GET_DEFAULT_INSTANCE: {
          return DEFAULT_INSTANCE;
        }
        case GET_PARSER: {
          com.google.protobuf.Parser<com.google.android.companionprotos.VersionExchangeProto.VersionExchange> parser = PARSER;
          if (parser == null) {
            synchronized (com.google.android.companionprotos.VersionExchangeProto.VersionExchange.class) {
              parser = PARSER;
              if (parser == null) {
                parser =
                    new DefaultInstanceBasedParser<com.google.android.companionprotos.VersionExchangeProto.VersionExchange>(
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


    // @@protoc_insertion_point(class_scope:com.google.companionprotos.VersionExchange)
    private static final com.google.android.companionprotos.VersionExchangeProto.VersionExchange DEFAULT_INSTANCE;
    static {
      VersionExchange defaultInstance = new VersionExchange();
      // New instances are implicitly immutable so no need to make
      // immutable.
      DEFAULT_INSTANCE = defaultInstance;
      com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
        VersionExchange.class, defaultInstance);
    }

    public static com.google.android.companionprotos.VersionExchangeProto.VersionExchange getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static volatile com.google.protobuf.Parser<VersionExchange> PARSER;

    public static com.google.protobuf.Parser<VersionExchange> parser() {
      return DEFAULT_INSTANCE.getParserForType();
    }
  }


  static {
  }

  // @@protoc_insertion_point(outer_class_scope)
}
