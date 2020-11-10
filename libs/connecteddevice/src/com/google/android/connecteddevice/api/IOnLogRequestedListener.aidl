package com.google.android.connecteddevice.api;

/** Listener for the output log file. */
oneway interface IOnLogRequestedListener {
    /** Triggered when the log records is requested. */
    void onLogRecordsRequested();
}
