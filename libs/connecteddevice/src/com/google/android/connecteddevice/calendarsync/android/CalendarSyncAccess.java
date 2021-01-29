package com.google.android.connecteddevice.calendarsync.android;

import static com.google.common.base.Preconditions.checkState;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import com.google.android.connecteddevice.calendarsync.common.CalendarSync;
import com.google.android.connecteddevice.calendarsync.common.Logger;
import java.util.function.Consumer;

/**
 * Allows access to a {@link CalendarSync} on a background handler thread.
 *
 * <p>Manages starting and stopping the handler thread.
 *
 * <p>This is used from both Android implementations: the phone and the car.
 */
public class CalendarSyncAccess {
  private static final String TAG = "CalendarSyncAccess";

  private final Logger.Factory loggerFactory;
  private final Logger logger;

  @Nullable private HandlerThread backgroundHandlerThread;
  @Nullable private Handler handler;
  @Nullable private CalendarSync sync;

  public CalendarSyncAccess(Logger.Factory loggerFactory) {
    this.loggerFactory = loggerFactory;
    logger = loggerFactory.create(TAG);
  }

  /**
   * Runs a task with the {@link CalendarSync} by posting on the background handler. Must be called
   * after {@link #start()}
   */
  public void access(Consumer<CalendarSync> task) {
    checkState(sync != null, "Must call start() first");
    handler.post(
        () -> {
          try {
            task.accept(sync);
          } catch (RuntimeException e) {
            // Catch and log exceptions without crashing the background thread.
            logger.error("Caught exception on calendar access thread", e);
          }
        });
  }

  /** Returns true when the background thread is started after calling {@link #start()}. */
  public boolean isStarted() {
    return sync != null;
  }

  /**
   * Start the background handler thread and creates the {@link CalendarSync}. Must be called before
   * calling {@link #access(Consumer)}. This calls {@link CalendarSync#start()} on the background
   * handler.
   */
  public void start() {
    if (sync == null) {
      backgroundHandlerThread = new HandlerThread(TAG);
      backgroundHandlerThread.start();
      handler = new Handler(backgroundHandlerThread.getLooper());
      sync = new CalendarSync(loggerFactory);
      handler.post(sync::start);
    } else {
      logger.error("start() was called multiple times without calling stop() first.");
    }
  }

  /**
   * Stops the background handler thread and releases the {@link CalendarSync}. This calls {@link
   * CalendarSync#stop()} on the background handler.
   */
  public void stop() {
    if (sync != null) {
      sync.stop();
      sync = null;
      backgroundHandlerThread.quitSafely();
      backgroundHandlerThread = null;
      handler = null;
    } else {
      logger.error("stop() was called without calling start() first.");
    }
  }
}
