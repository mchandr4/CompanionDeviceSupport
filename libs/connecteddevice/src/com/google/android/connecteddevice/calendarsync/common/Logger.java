package com.google.android.connecteddevice.calendarsync.common;

/**
 * An interface that allows platform specific logging implementations to be used in the common code.
 */
public interface Logger {

  /** Logs a DEBUG level message. */
  void debug(String message);

  /** Logs an INFO level message. */
  void info(String message);

  /** Logs a WARN level message. */
  void warn(String message);

  /** Logs an ERROR level message. */
  void error(String message);

  /** Logs an ERROR level message with an exception. */
  void error(String message, Exception e);

  /** Creates a {@link Logger} with the given name. */
  interface Factory {
    Logger create(String name);
  }

  /** A no-op implementation for testing that does not log anything. */
  class NoOpLoggerFactory implements Logger.Factory {
    @Override
    public Logger create(String name) {
      return new Logger() {
        @Override
        public void debug(String message) {}

        @Override
        public void info(String message) {}

        @Override
        public void warn(String message) {}

        @Override
        public void error(String message) {}

        @Override
        public void error(String message, Exception e) {}
      };
    }
  }
  ;
}
