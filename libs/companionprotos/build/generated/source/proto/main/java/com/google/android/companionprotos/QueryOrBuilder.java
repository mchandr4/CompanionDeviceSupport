// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: query.proto

package com.google.android.companionprotos;

public interface QueryOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.google.companionprotos.Query)
    com.google.protobuf.MessageLiteOrBuilder {

  /**
   * <pre>
   * Id of the query to be paired with a response.
   * </pre>
   *
   * <code>int32 id = 1;</code>
   * @return The id.
   */
  int getId();

  /**
   * <pre>
   * Identifier of the sender for a response.
   * </pre>
   *
   * <code>bytes sender = 2;</code>
   * @return The sender.
   */
  com.google.protobuf.ByteString getSender();

  /**
   * <pre>
   * Identifier of request type. Defined by recipient so each side understands
   * the intent.
   * </pre>
   *
   * <code>bytes request = 3;</code>
   * @return The request.
   */
  com.google.protobuf.ByteString getRequest();

  /**
   * <pre>
   * Any parameters needed to fulfill the query. Structure is defined by
   * the recipient.
   * </pre>
   *
   * <code>bytes parameters = 4;</code>
   * @return The parameters.
   */
  com.google.protobuf.ByteString getParameters();
}
