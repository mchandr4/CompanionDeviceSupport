// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: com/google/android/connecteddevice/calendarsync/common/update.proto

package com.google.android.connecteddevice.calendarsync;

/**
 * <pre>
 * Representation of an event participant.
 * Based on
 * https://developer.android.com/reference/android/provider/CalendarContract.Attendees
 * </pre>
 *
 * Protobuf type {@code aae.calendarsync.Attendee}
 */
public  final class Attendee extends
    com.google.protobuf.GeneratedMessageLite<
        Attendee, Attendee.Builder> implements
    // @@protoc_insertion_point(message_implements:aae.calendarsync.Attendee)
    AttendeeOrBuilder {
  private Attendee() {
    name_ = "";
    email_ = "";
  }
  /**
   * <pre>
   * Represents the attendee status for an event.
   * </pre>
   *
   * Protobuf enum {@code aae.calendarsync.Attendee.Status}
   */
  public enum Status
      implements com.google.protobuf.Internal.EnumLite {
    /**
     * <code>UNSPECIFIED_STATUS = 0;</code>
     */
    UNSPECIFIED_STATUS(0),
    /**
     * <code>NONE_STATUS = 1;</code>
     */
    NONE_STATUS(1),
    /**
     * <code>ACCEPTED = 2;</code>
     */
    ACCEPTED(2),
    /**
     * <code>DECLINED = 3;</code>
     */
    DECLINED(3),
    /**
     * <code>INVITED = 4;</code>
     */
    INVITED(4),
    /**
     * <code>TENTATIVE = 5;</code>
     */
    TENTATIVE(5),
    UNRECOGNIZED(-1),
    ;

    /**
     * <code>UNSPECIFIED_STATUS = 0;</code>
     */
    public static final int UNSPECIFIED_STATUS_VALUE = 0;
    /**
     * <code>NONE_STATUS = 1;</code>
     */
    public static final int NONE_STATUS_VALUE = 1;
    /**
     * <code>ACCEPTED = 2;</code>
     */
    public static final int ACCEPTED_VALUE = 2;
    /**
     * <code>DECLINED = 3;</code>
     */
    public static final int DECLINED_VALUE = 3;
    /**
     * <code>INVITED = 4;</code>
     */
    public static final int INVITED_VALUE = 4;
    /**
     * <code>TENTATIVE = 5;</code>
     */
    public static final int TENTATIVE_VALUE = 5;


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
    public static Status valueOf(int value) {
      return forNumber(value);
    }

    public static Status forNumber(int value) {
      switch (value) {
        case 0: return UNSPECIFIED_STATUS;
        case 1: return NONE_STATUS;
        case 2: return ACCEPTED;
        case 3: return DECLINED;
        case 4: return INVITED;
        case 5: return TENTATIVE;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<Status>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        Status> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<Status>() {
            @java.lang.Override
            public Status findValueByNumber(int number) {
              return Status.forNumber(number);
            }
          };

    public static com.google.protobuf.Internal.EnumVerifier 
        internalGetVerifier() {
      return StatusVerifier.INSTANCE;
    }

    private static final class StatusVerifier implements 
         com.google.protobuf.Internal.EnumVerifier { 
            static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new StatusVerifier();
            @java.lang.Override
            public boolean isInRange(int number) {
              return Status.forNumber(number) != null;
            }
          };

    private final int value;

    private Status(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:aae.calendarsync.Attendee.Status)
  }

  /**
   * <pre>
   * Represents the attendee type for an event.
   * </pre>
   *
   * Protobuf enum {@code aae.calendarsync.Attendee.Type}
   */
  public enum Type
      implements com.google.protobuf.Internal.EnumLite {
    /**
     * <code>UNSPECIFIED_TYPE = 0;</code>
     */
    UNSPECIFIED_TYPE(0),
    /**
     * <code>NONE_TYPE = 1;</code>
     */
    NONE_TYPE(1),
    /**
     * <code>OPTIONAL = 2;</code>
     */
    OPTIONAL(2),
    /**
     * <code>REQUIRED = 3;</code>
     */
    REQUIRED(3),
    /**
     * <code>RESOURCE = 4;</code>
     */
    RESOURCE(4),
    UNRECOGNIZED(-1),
    ;

    /**
     * <code>UNSPECIFIED_TYPE = 0;</code>
     */
    public static final int UNSPECIFIED_TYPE_VALUE = 0;
    /**
     * <code>NONE_TYPE = 1;</code>
     */
    public static final int NONE_TYPE_VALUE = 1;
    /**
     * <code>OPTIONAL = 2;</code>
     */
    public static final int OPTIONAL_VALUE = 2;
    /**
     * <code>REQUIRED = 3;</code>
     */
    public static final int REQUIRED_VALUE = 3;
    /**
     * <code>RESOURCE = 4;</code>
     */
    public static final int RESOURCE_VALUE = 4;


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
        case 0: return UNSPECIFIED_TYPE;
        case 1: return NONE_TYPE;
        case 2: return OPTIONAL;
        case 3: return REQUIRED;
        case 4: return RESOURCE;
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

    // @@protoc_insertion_point(enum_scope:aae.calendarsync.Attendee.Type)
  }

  public static final int NAME_FIELD_NUMBER = 1;
  private java.lang.String name_;
  /**
   * <pre>
   * The attendee name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  @java.lang.Override
  public java.lang.String getName() {
    return name_;
  }
  /**
   * <pre>
   * The attendee name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getNameBytes() {
    return com.google.protobuf.ByteString.copyFromUtf8(name_);
  }
  /**
   * <pre>
   * The attendee name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @param value The name to set.
   */
  private void setName(
      java.lang.String value) {
    java.lang.Class<?> valueClass = value.getClass();
  
    name_ = value;
  }
  /**
   * <pre>
   * The attendee name.
   * </pre>
   *
   * <code>string name = 1;</code>
   */
  private void clearName() {
    
    name_ = getDefaultInstance().getName();
  }
  /**
   * <pre>
   * The attendee name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @param value The bytes for name to set.
   */
  private void setNameBytes(
      com.google.protobuf.ByteString value) {
    checkByteStringIsUtf8(value);
    name_ = value.toStringUtf8();
    
  }

  public static final int EMAIL_FIELD_NUMBER = 2;
  private java.lang.String email_;
  /**
   * <pre>
   * The attendee email which must be unique in its event.
   * </pre>
   *
   * <code>string email = 2;</code>
   * @return The email.
   */
  @java.lang.Override
  public java.lang.String getEmail() {
    return email_;
  }
  /**
   * <pre>
   * The attendee email which must be unique in its event.
   * </pre>
   *
   * <code>string email = 2;</code>
   * @return The bytes for email.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getEmailBytes() {
    return com.google.protobuf.ByteString.copyFromUtf8(email_);
  }
  /**
   * <pre>
   * The attendee email which must be unique in its event.
   * </pre>
   *
   * <code>string email = 2;</code>
   * @param value The email to set.
   */
  private void setEmail(
      java.lang.String value) {
    java.lang.Class<?> valueClass = value.getClass();
  
    email_ = value;
  }
  /**
   * <pre>
   * The attendee email which must be unique in its event.
   * </pre>
   *
   * <code>string email = 2;</code>
   */
  private void clearEmail() {
    
    email_ = getDefaultInstance().getEmail();
  }
  /**
   * <pre>
   * The attendee email which must be unique in its event.
   * </pre>
   *
   * <code>string email = 2;</code>
   * @param value The bytes for email to set.
   */
  private void setEmailBytes(
      com.google.protobuf.ByteString value) {
    checkByteStringIsUtf8(value);
    email_ = value.toStringUtf8();
    
  }

  public static final int STATUS_FIELD_NUMBER = 3;
  private int status_;
  /**
   * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
   * @return The enum numeric value on the wire for status.
   */
  @java.lang.Override
  public int getStatusValue() {
    return status_;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
   * @return The status.
   */
  @java.lang.Override
  public com.google.android.connecteddevice.calendarsync.Attendee.Status getStatus() {
    com.google.android.connecteddevice.calendarsync.Attendee.Status result = com.google.android.connecteddevice.calendarsync.Attendee.Status.forNumber(status_);
    return result == null ? com.google.android.connecteddevice.calendarsync.Attendee.Status.UNRECOGNIZED : result;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
   * @param value The enum numeric value on the wire for status to set.
   */
  private void setStatusValue(int value) {
      status_ = value;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
   * @param value The status to set.
   */
  private void setStatus(com.google.android.connecteddevice.calendarsync.Attendee.Status value) {
    status_ = value.getNumber();
    
  }
  /**
   * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
   */
  private void clearStatus() {
    
    status_ = 0;
  }

  public static final int TYPE_FIELD_NUMBER = 4;
  private int type_;
  /**
   * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
   * @return The enum numeric value on the wire for type.
   */
  @java.lang.Override
  public int getTypeValue() {
    return type_;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
   * @return The type.
   */
  @java.lang.Override
  public com.google.android.connecteddevice.calendarsync.Attendee.Type getType() {
    com.google.android.connecteddevice.calendarsync.Attendee.Type result = com.google.android.connecteddevice.calendarsync.Attendee.Type.forNumber(type_);
    return result == null ? com.google.android.connecteddevice.calendarsync.Attendee.Type.UNRECOGNIZED : result;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
   * @param value The enum numeric value on the wire for type to set.
   */
  private void setTypeValue(int value) {
      type_ = value;
  }
  /**
   * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
   * @param value The type to set.
   */
  private void setType(com.google.android.connecteddevice.calendarsync.Attendee.Type value) {
    type_ = value.getNumber();
    
  }
  /**
   * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
   */
  private void clearType() {
    
    type_ = 0;
  }

  public static final int ACTION_FIELD_NUMBER = 5;
  private int action_;
  /**
   * <code>.aae.calendarsync.UpdateAction action = 5;</code>
   * @return The enum numeric value on the wire for action.
   */
  @java.lang.Override
  public int getActionValue() {
    return action_;
  }
  /**
   * <code>.aae.calendarsync.UpdateAction action = 5;</code>
   * @return The action.
   */
  @java.lang.Override
  public com.google.android.connecteddevice.calendarsync.UpdateAction getAction() {
    com.google.android.connecteddevice.calendarsync.UpdateAction result = com.google.android.connecteddevice.calendarsync.UpdateAction.forNumber(action_);
    return result == null ? com.google.android.connecteddevice.calendarsync.UpdateAction.UNRECOGNIZED : result;
  }
  /**
   * <code>.aae.calendarsync.UpdateAction action = 5;</code>
   * @param value The enum numeric value on the wire for action to set.
   */
  private void setActionValue(int value) {
      action_ = value;
  }
  /**
   * <code>.aae.calendarsync.UpdateAction action = 5;</code>
   * @param value The action to set.
   */
  private void setAction(com.google.android.connecteddevice.calendarsync.UpdateAction value) {
    action_ = value.getNumber();
    
  }
  /**
   * <code>.aae.calendarsync.UpdateAction action = 5;</code>
   */
  private void clearAction() {
    
    action_ = 0;
  }

  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static com.google.android.connecteddevice.calendarsync.Attendee parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }

  public static Builder newBuilder() {
    return (Builder) DEFAULT_INSTANCE.createBuilder();
  }
  public static Builder newBuilder(com.google.android.connecteddevice.calendarsync.Attendee prototype) {
    return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
  }

  /**
   * <pre>
   * Representation of an event participant.
   * Based on
   * https://developer.android.com/reference/android/provider/CalendarContract.Attendees
   * </pre>
   *
   * Protobuf type {@code aae.calendarsync.Attendee}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageLite.Builder<
        com.google.android.connecteddevice.calendarsync.Attendee, Builder> implements
      // @@protoc_insertion_point(builder_implements:aae.calendarsync.Attendee)
      com.google.android.connecteddevice.calendarsync.AttendeeOrBuilder {
    // Construct using com.google.android.connecteddevice.calendarsync.Attendee.newBuilder()
    private Builder() {
      super(DEFAULT_INSTANCE);
    }


    /**
     * <pre>
     * The attendee name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * @return The name.
     */
    @java.lang.Override
    public java.lang.String getName() {
      return instance.getName();
    }
    /**
     * <pre>
     * The attendee name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * @return The bytes for name.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getNameBytes() {
      return instance.getNameBytes();
    }
    /**
     * <pre>
     * The attendee name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * @param value The name to set.
     * @return This builder for chaining.
     */
    public Builder setName(
        java.lang.String value) {
      copyOnWrite();
      instance.setName(value);
      return this;
    }
    /**
     * <pre>
     * The attendee name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearName() {
      copyOnWrite();
      instance.clearName();
      return this;
    }
    /**
     * <pre>
     * The attendee name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * @param value The bytes for name to set.
     * @return This builder for chaining.
     */
    public Builder setNameBytes(
        com.google.protobuf.ByteString value) {
      copyOnWrite();
      instance.setNameBytes(value);
      return this;
    }

    /**
     * <pre>
     * The attendee email which must be unique in its event.
     * </pre>
     *
     * <code>string email = 2;</code>
     * @return The email.
     */
    @java.lang.Override
    public java.lang.String getEmail() {
      return instance.getEmail();
    }
    /**
     * <pre>
     * The attendee email which must be unique in its event.
     * </pre>
     *
     * <code>string email = 2;</code>
     * @return The bytes for email.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
        getEmailBytes() {
      return instance.getEmailBytes();
    }
    /**
     * <pre>
     * The attendee email which must be unique in its event.
     * </pre>
     *
     * <code>string email = 2;</code>
     * @param value The email to set.
     * @return This builder for chaining.
     */
    public Builder setEmail(
        java.lang.String value) {
      copyOnWrite();
      instance.setEmail(value);
      return this;
    }
    /**
     * <pre>
     * The attendee email which must be unique in its event.
     * </pre>
     *
     * <code>string email = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearEmail() {
      copyOnWrite();
      instance.clearEmail();
      return this;
    }
    /**
     * <pre>
     * The attendee email which must be unique in its event.
     * </pre>
     *
     * <code>string email = 2;</code>
     * @param value The bytes for email to set.
     * @return This builder for chaining.
     */
    public Builder setEmailBytes(
        com.google.protobuf.ByteString value) {
      copyOnWrite();
      instance.setEmailBytes(value);
      return this;
    }

    /**
     * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
     * @return The enum numeric value on the wire for status.
     */
    @java.lang.Override
    public int getStatusValue() {
      return instance.getStatusValue();
    }
    /**
     * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
     * @param value The status to set.
     * @return This builder for chaining.
     */
    public Builder setStatusValue(int value) {
      copyOnWrite();
      instance.setStatusValue(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
     * @return The status.
     */
    @java.lang.Override
    public com.google.android.connecteddevice.calendarsync.Attendee.Status getStatus() {
      return instance.getStatus();
    }
    /**
     * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
     * @param value The enum numeric value on the wire for status to set.
     * @return This builder for chaining.
     */
    public Builder setStatus(com.google.android.connecteddevice.calendarsync.Attendee.Status value) {
      copyOnWrite();
      instance.setStatus(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.Attendee.Status status = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearStatus() {
      copyOnWrite();
      instance.clearStatus();
      return this;
    }

    /**
     * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
     * @return The enum numeric value on the wire for type.
     */
    @java.lang.Override
    public int getTypeValue() {
      return instance.getTypeValue();
    }
    /**
     * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
     * @param value The type to set.
     * @return This builder for chaining.
     */
    public Builder setTypeValue(int value) {
      copyOnWrite();
      instance.setTypeValue(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
     * @return The type.
     */
    @java.lang.Override
    public com.google.android.connecteddevice.calendarsync.Attendee.Type getType() {
      return instance.getType();
    }
    /**
     * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
     * @param value The enum numeric value on the wire for type to set.
     * @return This builder for chaining.
     */
    public Builder setType(com.google.android.connecteddevice.calendarsync.Attendee.Type value) {
      copyOnWrite();
      instance.setType(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.Attendee.Type type = 4;</code>
     * @return This builder for chaining.
     */
    public Builder clearType() {
      copyOnWrite();
      instance.clearType();
      return this;
    }

    /**
     * <code>.aae.calendarsync.UpdateAction action = 5;</code>
     * @return The enum numeric value on the wire for action.
     */
    @java.lang.Override
    public int getActionValue() {
      return instance.getActionValue();
    }
    /**
     * <code>.aae.calendarsync.UpdateAction action = 5;</code>
     * @param value The action to set.
     * @return This builder for chaining.
     */
    public Builder setActionValue(int value) {
      copyOnWrite();
      instance.setActionValue(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.UpdateAction action = 5;</code>
     * @return The action.
     */
    @java.lang.Override
    public com.google.android.connecteddevice.calendarsync.UpdateAction getAction() {
      return instance.getAction();
    }
    /**
     * <code>.aae.calendarsync.UpdateAction action = 5;</code>
     * @param value The enum numeric value on the wire for action to set.
     * @return This builder for chaining.
     */
    public Builder setAction(com.google.android.connecteddevice.calendarsync.UpdateAction value) {
      copyOnWrite();
      instance.setAction(value);
      return this;
    }
    /**
     * <code>.aae.calendarsync.UpdateAction action = 5;</code>
     * @return This builder for chaining.
     */
    public Builder clearAction() {
      copyOnWrite();
      instance.clearAction();
      return this;
    }

    // @@protoc_insertion_point(builder_scope:aae.calendarsync.Attendee)
  }
  @java.lang.Override
  @java.lang.SuppressWarnings({"unchecked", "fallthrough"})
  protected final java.lang.Object dynamicMethod(
      com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
      java.lang.Object arg0, java.lang.Object arg1) {
    switch (method) {
      case NEW_MUTABLE_INSTANCE: {
        return new com.google.android.connecteddevice.calendarsync.Attendee();
      }
      case NEW_BUILDER: {
        return new Builder();
      }
      case BUILD_MESSAGE_INFO: {
          java.lang.Object[] objects = new java.lang.Object[] {
            "name_",
            "email_",
            "status_",
            "type_",
            "action_",
          };
          java.lang.String info =
              "\u0000\u0005\u0000\u0000\u0001\u0005\u0005\u0000\u0000\u0000\u0001\u0208\u0002\u0208" +
              "\u0003\f\u0004\f\u0005\f";
          return newMessageInfo(DEFAULT_INSTANCE, info, objects);
      }
      // fall through
      case GET_DEFAULT_INSTANCE: {
        return DEFAULT_INSTANCE;
      }
      case GET_PARSER: {
        com.google.protobuf.Parser<com.google.android.connecteddevice.calendarsync.Attendee> parser = PARSER;
        if (parser == null) {
          synchronized (com.google.android.connecteddevice.calendarsync.Attendee.class) {
            parser = PARSER;
            if (parser == null) {
              parser =
                  new DefaultInstanceBasedParser<com.google.android.connecteddevice.calendarsync.Attendee>(
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


  // @@protoc_insertion_point(class_scope:aae.calendarsync.Attendee)
  private static final com.google.android.connecteddevice.calendarsync.Attendee DEFAULT_INSTANCE;
  static {
    Attendee defaultInstance = new Attendee();
    // New instances are implicitly immutable so no need to make
    // immutable.
    DEFAULT_INSTANCE = defaultInstance;
    com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
      Attendee.class, defaultInstance);
  }

  public static com.google.android.connecteddevice.calendarsync.Attendee getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static volatile com.google.protobuf.Parser<Attendee> PARSER;

  public static com.google.protobuf.Parser<Attendee> parser() {
    return DEFAULT_INSTANCE.getParserForType();
  }
}

