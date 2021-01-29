package com.google.android.connecteddevice.calendarsync.common;

import java.util.Set;

/**
 * Synchronizes calendar data from the <a
 * href="https://developer.android.com/guide/topics/providers/calendar-provider">calendar
 * provider</a> on this device with remote devices.
 *
 * <p>Each remote device has a <i>time window</i> for events that will be observed for changes.
 * Changes to events outside of that window will be ignored.
 *
 * <p>If the calendar synchronization is initiated on this device by calling {@link #sync(String,
 * Set)} then it is considered the <i>source</i> calendar. Otherwise the calendar is received from
 * the remote device and it is considered the <i>replica</i>.
 *
 * <p>Early versions of this feature do not support sending calendar changes in both directions but
 * have a <i>sender</i> and a <i>receiver</i>. This version supports sending and receiving
 * incremental updates to calendars.
 *
 * <p>For backwards compatibility this class supports two modes depending on the version of the
 * remote device feature:
 *
 * <ol>
 *   <li><b>Replace mode for legacy remote devices</b>
 *       <p>When a remote device does not support updates this device will send or receive the full
 *       calendar data when there is any change to the synchronized calendars.
 *   <li><b>Update mode</b>
 *       <p>When a remote device does support updates this device will send and receive incremental
 *       updates to synchronized calendars.
 * </ol>
 *
 * The initial message to a remote device always assumes the receiver is a legacy device. Only after
 * receiving a message from the remote device can it know the version. This is fine because the
 * first message will always contain the full source calendar data. Upon receiving the first message
 * the version is updated and subsequent changes may be sent as updates.
 *
 * <p>This class is not thread-safe and after construction should only be accessed by a single
 * non-main thread.
 */
// TODO(b/166134901) Work in progress - complete the features documented above.
@SuppressWarnings("unused")
public class CalendarSync {
  private static final String TAG = "CalendarSync";
  private static final int PROTOCOL_VERSION = 1;
  private static final int UPDATABLE_VERSION = 1;

  private final Logger logger;

  public CalendarSync(Logger.Factory loggerFactory) {
    logger = loggerFactory.create(TAG);
  }

  /**
   * Sets the ids of local calendars to synchronize to the given receiver device.
   *
   * <p>This device is the 'source' of the data and the remote device stores the 'replica'. The
   * remote device will create a new calendar and act as the <a
   * href="https://developer.android.com/guide/topics/providers/calendar-provider#sync-adapter">sync
   * adapter</a>.
   *
   * <p>If the receiver supports updates then changes to the replica are sent back to the source.
   */
  public void sync(String deviceId, Set<String> calendarIds) {
    logger.debug(String.format("Sync calendars %s with device %s", calendarIds, deviceId));
  }

  /** Receives a message from the remote device. */
  public void receive(String deviceId, byte[] message) {
    logger.debug(
        String.format("Receive $s message of size %s from device %s", message.length, deviceId));
  }

  /** Clears all data for the given device. */
  public void clear(String deviceId) {
    logger.debug("Clear device " + deviceId);
  }

  /** Must be called before using the other methods of this class. */
  public void start() {
    logger.debug("Start");
  }

  /** Must be called when the synchronization will be stopped. */
  public void stop() {
    logger.debug("Stop");
  }
}
