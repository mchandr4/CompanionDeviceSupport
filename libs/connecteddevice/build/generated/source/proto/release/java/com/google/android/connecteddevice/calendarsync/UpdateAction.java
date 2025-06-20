// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: com/google/android/connecteddevice/calendarsync/common/update.proto

package com.google.android.connecteddevice.calendarsync;

/**
 * <pre>
 * The type of update to make with the data message.
 * </pre>
 *
 * Protobuf enum {@code aae.calendarsync.UpdateAction}
 */
public enum UpdateAction
    implements com.google.protobuf.Internal.EnumLite {
  /**
   * <pre>
   * The default action when no value is set with protocol version 0.
   * </pre>
   *
   * <code>ACTION_UNSPECIFIED = 0;</code>
   */
  ACTION_UNSPECIFIED(0),
  /**
   * <pre>
   * Send full calendars to replace existing data.
   * </pre>
   *
   * <code>REPLACE = 1;</code>
   */
  REPLACE(1),
  /**
   * <pre>
   * Create a new item and its children.
   * </pre>
   *
   * <code>CREATE = 2;</code>
   */
  CREATE(2),
  /**
   * <pre>
   * Delete the item and children.
   * </pre>
   *
   * <code>DELETE = 3;</code>
   */
  DELETE(3),
  /**
   * <pre>
   * The item is changed and should be updated.
   * </pre>
   *
   * <code>UPDATE = 4;</code>
   */
  UPDATE(4),
  /**
   * <pre>
   * The item is not changed (children might be).
   * </pre>
   *
   * <code>UNCHANGED = 5;</code>
   */
  UNCHANGED(5),
  UNRECOGNIZED(-1),
  ;

  /**
   * <pre>
   * The default action when no value is set with protocol version 0.
   * </pre>
   *
   * <code>ACTION_UNSPECIFIED = 0;</code>
   */
  public static final int ACTION_UNSPECIFIED_VALUE = 0;
  /**
   * <pre>
   * Send full calendars to replace existing data.
   * </pre>
   *
   * <code>REPLACE = 1;</code>
   */
  public static final int REPLACE_VALUE = 1;
  /**
   * <pre>
   * Create a new item and its children.
   * </pre>
   *
   * <code>CREATE = 2;</code>
   */
  public static final int CREATE_VALUE = 2;
  /**
   * <pre>
   * Delete the item and children.
   * </pre>
   *
   * <code>DELETE = 3;</code>
   */
  public static final int DELETE_VALUE = 3;
  /**
   * <pre>
   * The item is changed and should be updated.
   * </pre>
   *
   * <code>UPDATE = 4;</code>
   */
  public static final int UPDATE_VALUE = 4;
  /**
   * <pre>
   * The item is not changed (children might be).
   * </pre>
   *
   * <code>UNCHANGED = 5;</code>
   */
  public static final int UNCHANGED_VALUE = 5;


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
  public static UpdateAction valueOf(int value) {
    return forNumber(value);
  }

  public static UpdateAction forNumber(int value) {
    switch (value) {
      case 0: return ACTION_UNSPECIFIED;
      case 1: return REPLACE;
      case 2: return CREATE;
      case 3: return DELETE;
      case 4: return UPDATE;
      case 5: return UNCHANGED;
      default: return null;
    }
  }

  public static com.google.protobuf.Internal.EnumLiteMap<UpdateAction>
      internalGetValueMap() {
    return internalValueMap;
  }
  private static final com.google.protobuf.Internal.EnumLiteMap<
      UpdateAction> internalValueMap =
        new com.google.protobuf.Internal.EnumLiteMap<UpdateAction>() {
          @java.lang.Override
          public UpdateAction findValueByNumber(int number) {
            return UpdateAction.forNumber(number);
          }
        };

  public static com.google.protobuf.Internal.EnumVerifier 
      internalGetVerifier() {
    return UpdateActionVerifier.INSTANCE;
  }

  private static final class UpdateActionVerifier implements 
       com.google.protobuf.Internal.EnumVerifier { 
          static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new UpdateActionVerifier();
          @java.lang.Override
          public boolean isInRange(int number) {
            return UpdateAction.forNumber(number) != null;
          }
        };

  private final int value;

  private UpdateAction(int value) {
    this.value = value;
  }

  // @@protoc_insertion_point(enum_scope:aae.calendarsync.UpdateAction)
}

