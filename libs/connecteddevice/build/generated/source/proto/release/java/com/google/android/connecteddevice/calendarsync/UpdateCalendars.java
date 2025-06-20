// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: com/google/android/connecteddevice/calendarsync/common/update.proto

package com.google.android.connecteddevice.calendarsync;

/**
 * <pre>
 * Update calendars on a remote device.
 * </pre>
 *
 * Protobuf type {@code aae.calendarsync.UpdateCalendars}
 */
public  final class UpdateCalendars extends
    com.google.protobuf.GeneratedMessageLite<
        UpdateCalendars, UpdateCalendars.Builder> implements
    // @@protoc_insertion_point(message_implements:aae.calendarsync.UpdateCalendars)
    UpdateCalendarsOrBuilder {
  private UpdateCalendars() {
    calendars_ = emptyProtobufList();
  }
  /**
   * Protobuf enum {@code aae.calendarsync.UpdateCalendars.Type}
   */
  public enum Type
      implements com.google.protobuf.Internal.EnumLite {
    /**
     * <pre>
     * The default type when no value is set with protocol version 0.
     * </pre>
     *
     * <code>TYPE_UNSPECIFIED = 0;</code>
     */
    TYPE_UNSPECIFIED(0),
    /**
     * <pre>
     * Receive calendar data.
     * </pre>
     *
     * <code>RECEIVE = 1;</code>
     */
    RECEIVE(1),
    /**
     * <pre>
     * A response with only the protocol version and no calendar data.
     * </pre>
     *
     * <code>ACKNOWLEDGE = 2;</code>
     */
    ACKNOWLEDGE(2),
    /**
     * <pre>
     * Remove all data associated with this device.
     * This is similar to an UPDATE that deletes all stored calendars but safer
     * in the case where the source does not have a correct record of which
     * calendars are stored.
     * </pre>
     *
     * <code>DISABLE = 3;</code>
     */
    DISABLE(3),
    UNRECOGNIZED(-1),
    ;

    /**
     * <pre>
     * The default type when no value is set with protocol version 0.
     * </pre>
     *
     * <code>TYPE_UNSPECIFIED = 0;</code>
     */
    public static final int TYPE_UNSPECIFIED_VALUE = 0;
    /**
     * <pre>
     * Receive calendar data.
     * </pre>
     *
     * <code>RECEIVE = 1;</code>
     */
    public static final int RECEIVE_VALUE = 1;
    /**
     * <pre>
     * A response with only the protocol version and no calendar data.
     * </pre>
     *
     * <code>ACKNOWLEDGE = 2;</code>
     */
    public static final int ACKNOWLEDGE_VALUE = 2;
    /**
     * <pre>
     * Remove all data associated with this device.
     * This is similar to an UPDATE that deletes all stored calendars but safer
     * in the case where the source does not have a correct record of which
     * calendars are stored.
     * </pre>
     *
     * <code>DISABLE = 3;</code>
     */
    public static final int DISABLE_VALUE = 3;


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
    public static Type valueOf(int value) {
      return forNumber(value);
    }

    public static Type forNumber(int value) {
      switch (value) {
        case 0: return TYPE_UNSPECIFIED;
        case 1: return RECEIVE;
        case 2: return ACKNOWLEDGE;
        case 3: return DISABLE;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<Type>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        Type> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<Type>() {
            @java.lang.Override
            public Type findValueByNumber(int number) {
              return Type.forNumber(number);
            }
          };

    public static com.google.protobuf.Internal.EnumVerifier 
        internalGetVerifier() {
      return TypeVerifier.INSTANCE;
    }

    private static final class TypeVerifier implements 
         com.google.protobuf.Internal.EnumVerifier { 
            static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new TypeVerifier();
            @java.lang.Override
            public boolean isInRange(int number) {
              return Type.forNumber(number) != null;
            }
          };

    private final int value;

    private Type(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:aae.calendarsync.UpdateCalendars.Type)
  }

  public static final int CALENDARS_FIELD_NUMBER = 1;
  private com.google.protobuf.Internal.ProtobufList<com.google.android.connecteddevice.calendarsync.Calendar> calendars_;
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  @java.lang.Override
  public java.util.List<com.google.android.connecteddevice.calendarsync.Calendar> getCalendarsList() {
    return calendars_;
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  public java.util.List<? extends com.google.android.connecteddevice.calendarsync.CalendarOrBuilder> 
      getCalendarsOrBuilderList() {
    return calendars_;
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  @java.lang.Override
  public int getCalendarsCount() {
    return calendars_.size();
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  @java.lang.Override
  public com.google.android.connecteddevice.calendarsync.Calendar getCalendars(int index) {
    return calendars_.get(index);
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  public com.google.android.connecteddevice.calendarsync.CalendarOrBuilder getCalendarsOrBuilder(
      int index) {
    return calendars_.get(index);
  }
  private void ensureCalendarsIsMutable() {
    com.google.protobuf.Internal.ProtobufList<com.google.android.connecteddevice.calendarsync.Calendar> tmp = calendars_;
    if (!tmp.isModifiable()) {
      calendars_ =
          com.google.protobuf.GeneratedMessageLite.mutableCopy(tmp);
     }
  }

  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void setCalendars(
      int index, com.google.android.connecteddevice.calendarsync.Calendar value) {
    value.getClass();
  ensureCalendarsIsMutable();
    calendars_.set(index, value);
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void addCalendars(com.google.android.connecteddevice.calendarsync.Calendar value) {
    value.getClass();
  ensureCalendarsIsMutable();
    calendars_.add(value);
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void addCalendars(
      int index, com.google.android.connecteddevice.calendarsync.Calendar value) {
    value.getClass();
  ensureCalendarsIsMutable();
    calendars_.add(index, value);
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void addAllCalendars(
      java.lang.Iterable<? extends com.google.android.connecteddevice.calendarsync.Calendar> values) {
    ensureCalendarsIsMutable();
    com.google.protobuf.AbstractMessageLite.addAll(
        values, calendars_);
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void clearCalendars() {
    calendars_ = emptyProtobufList();
  }
  /**
   * <pre>
   * Calendars to update on the remote device.
   * </pre>
   *
   * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
   */
  private void removeCalendars(int index) {
    ensureCalendarsIsMutable();
    calendars_.remove(index);
  }

  public static final int VERSION_FIELD_NUMBER = 3;
  private int version_;
  /**
   * <pre>
   * The protocol version.
   * When using calendar.proto this will be the default value of 0.
   * Implementations that handle incremental updates will have non-zero values.
   * </pre>
   *
   * <code>int32 version = 3;</code>
   * @return The version.
   */
  @java.lang.Override
  public int getVersion() {
    return version_;
  }
  /**
   * <pre>
   * The protocol version.
   * When using calendar.proto this will be the default value of 0.
   * Implementations that handle incremental updates will have non-zero values.
   * </pre>
   *
   * <code>int32 version = 3;</code>
   * @param value The version to set.
   */
  private void setVersion(int value) {
    
    version_ = value;
  }
  /**
   * <pre>
   * The protocol version.
   * When using calendar.proto this will be the default value of 0.
   * Implementations that handle incremental updates will have non-zero values.
   * </pre>
   *
   * <code>int32 version = 3;</code>
   */
  private void clearVersion() {
    
    version_ = 0;
  }

  public static final int TYPE_FIELD_NUMBER = 4;
  private int type_;
  /**
   * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
   * @return The enum numeric value on the wire for type.
   */
  @java.lang.Override
  public int getTypeValue() {
    return type_;
  }
  /**
   * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
   * @return The type.
   */
  @java.lang.Override
  public com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type getType() {
    com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type result = com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type.forNumber(type_);
    return result == null ? com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type.UNRECOGNIZED : result;
  }
  /**
   * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
   * @param value The enum numeric value on the wire for type to set.
   */
  private void setTypeValue(int value) {
      type_ = value;
  }
  /**
   * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
   * @param value The type to set.
   */
  private void setType(com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type value) {
    type_ = value.getNumber();
    
  }
  /**
   * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
   */
  private void clearType() {
    
    type_ = 0;
  }

  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }

  public static Builder newBuilder() {
    return (Builder) DEFAULT_INSTANCE.createBuilder();
  }
  public static Builder newBuilder(com.google.android.connecteddevice.calendarsync.UpdateCalendars prototype) {
    return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
  }

  /**
   * <pre>
   * Update calendars on a remote device.
   * </pre>
   *
   * Protobuf type {@code aae.calendarsync.UpdateCalendars}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageLite.Builder<
        com.google.android.connecteddevice.calendarsync.UpdateCalendars, Builder> implements
      // @@protoc_insertion_point(builder_implements:aae.calendarsync.UpdateCalendars)
      com.google.android.connecteddevice.calendarsync.UpdateCalendarsOrBuilder {
    // Construct using com.google.android.connecteddevice.calendarsync.UpdateCalendars.newBuilder()
    private Builder() {
      super(DEFAULT_INSTANCE);
    }


    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    @java.lang.Override
    public java.util.List<com.google.android.connecteddevice.calendarsync.Calendar> getCalendarsList() {
      return java.util.Collections.unmodifiableList(
          instance.getCalendarsList());
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    @java.lang.Override
    public int getCalendarsCount() {
      return instance.getCalendarsCount();
    }/**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    @java.lang.Override
    public com.google.android.connecteddevice.calendarsync.Calendar getCalendars(int index) {
      return instance.getCalendars(index);
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder setCalendars(
        int index, com.google.android.connecteddevice.calendarsync.Calendar value) {
      copyOnWrite();
      instance.setCalendars(index, value);
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder setCalendars(
        int index, com.google.android.connecteddevice.calendarsync.Calendar.Builder builderForValue) {
      copyOnWrite();
      instance.setCalendars(index,
          builderForValue.build());
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder addCalendars(com.google.android.connecteddevice.calendarsync.Calendar value) {
      copyOnWrite();
      instance.addCalendars(value);
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder addCalendars(
        int index, com.google.android.connecteddevice.calendarsync.Calendar value) {
      copyOnWrite();
      instance.addCalendars(index, value);
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder addCalendars(
        com.google.android.connecteddevice.calendarsync.Calendar.Builder builderForValue) {
      copyOnWrite();
      instance.addCalendars(builderForValue.build());
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder addCalendars(
        int index, com.google.android.connecteddevice.calendarsync.Calendar.Builder builderForValue) {
      copyOnWrite();
      instance.addCalendars(index,
          builderForValue.build());
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder addAllCalendars(
        java.lang.Iterable<? extends com.google.android.connecteddevice.calendarsync.Calendar> values) {
      copyOnWrite();
      instance.addAllCalendars(values);
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder clearCalendars() {
      copyOnWrite();
      instance.clearCalendars();
      return this;
    }
    /**
     * <pre>
     * Calendars to update on the remote device.
     * </pre>
     *
     * <code>repeated .aae.calendarsync.Calendar calendars = 1;</code>
     */
    public Builder removeCalendars(int index) {
      copyOnWrite();
      instance.removeCalendars(index);
      return this;
    }

    /**
     * <pre>
     * The protocol version.
     * When using calendar.proto this will be the default value of 0.
     * Implementations that handle incremental updates will have non-zero values.
     * </pre>
     *
     * <code>int32 version = 3;</code>
     * @return The version.
     */
    @java.lang.Override
    public int getVersion() {
      return instance.getVersion();
    }
    /**
     * <pre>
     * The protocol version.
     * When using calendar.proto this will be the default value of 0.
     * Implementations that handle incremental updates will have non-zero values.
     * </pre>
     *
     * <code>int32 version = 3;</code>
     * @param value The version to set.
     * @return This builder for chaining.
     */
    public Builder setVersion(int value) {
      copyOnWrite();
      instance.setVersion(value);
      return this;
    }
    /**
     * <pre>
     * The protocol version.
     * When using calendar.proto this will be the default value of 0.
     * Implementations that handle incremental updates will have non-zero values.
     * </pre>
     *
     * <code>int32 version = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearVersion() {
      copyOnWrite();
      instance.clearVersion();
      return this;
    }

    /**
     * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
     * @return The enum numeric value on the wire for type.
     */
    @java.lang.Override
    public int getTypeValue() {
      return instance.getTypeValue();
    }
    /**
     * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
     * @param value The type to set.
     * @return This builder for chaining.
     */
    public Builder setTypeValue(int value) {
      copyOnWrite();
      instance.setTypeValue(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
     * @return The type.
     */
    @java.lang.Override
    public com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type getType() {
      return instance.getType();
    }
    /**
     * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
     * @param value The enum numeric value on the wire for type to set.
     * @return This builder for chaining.
     */
    public Builder setType(com.google.android.connecteddevice.calendarsync.UpdateCalendars.Type value) {
      copyOnWrite();
      instance.setType(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.UpdateCalendars.Type type = 4;</code>
     * @return This builder for chaining.
     */
    public Builder clearType() {
      copyOnWrite();
      instance.clearType();
      return this;
    }

    // @@protoc_insertion_point(builder_scope:aae.calendarsync.UpdateCalendars)
  }
  @java.lang.Override
  @java.lang.SuppressWarnings({"unchecked", "fallthrough"})
  protected final java.lang.Object dynamicMethod(
      com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
      java.lang.Object arg0, java.lang.Object arg1) {
    switch (method) {
      case NEW_MUTABLE_INSTANCE: {
        return new com.google.android.connecteddevice.calendarsync.UpdateCalendars();
      }
      case NEW_BUILDER: {
        return new Builder();
      }
      case BUILD_MESSAGE_INFO: {
          java.lang.Object[] objects = new java.lang.Object[] {
            "calendars_",
            com.google.android.connecteddevice.calendarsync.Calendar.class,
            "version_",
            "type_",
          };
          java.lang.String info =
              "\u0000\u0003\u0000\u0000\u0001\u0004\u0003\u0000\u0001\u0000\u0001\u001b\u0003\u0004" +
              "\u0004\f";
          return newMessageInfo(DEFAULT_INSTANCE, info, objects);
      }
      // fall through
      case GET_DEFAULT_INSTANCE: {
        return DEFAULT_INSTANCE;
      }
      case GET_PARSER: {
        com.google.protobuf.Parser<com.google.android.connecteddevice.calendarsync.UpdateCalendars> parser = PARSER;
        if (parser == null) {
          synchronized (com.google.android.connecteddevice.calendarsync.UpdateCalendars.class) {
            parser = PARSER;
            if (parser == null) {
              parser =
                  new DefaultInstanceBasedParser<com.google.android.connecteddevice.calendarsync.UpdateCalendars>(
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


  // @@protoc_insertion_point(class_scope:aae.calendarsync.UpdateCalendars)
  private static final com.google.android.connecteddevice.calendarsync.UpdateCalendars DEFAULT_INSTANCE;
  static {
    UpdateCalendars defaultInstance = new UpdateCalendars();
    // New instances are implicitly immutable so no need to make
    // immutable.
    DEFAULT_INSTANCE = defaultInstance;
    com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
      UpdateCalendars.class, defaultInstance);
  }

  public static com.google.android.connecteddevice.calendarsync.UpdateCalendars getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static volatile com.google.protobuf.Parser<UpdateCalendars> PARSER;

  public static com.google.protobuf.Parser<UpdateCalendars> parser() {
    return DEFAULT_INSTANCE.getParserForType();
  }
}

