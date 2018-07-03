package com.motondon.driveapidemoapp3.service

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

import com.google.android.gms.drive.events.CompletionEvent
import com.google.android.gms.drive.events.DriveEventService
import com.motondon.driveapidemoapp3.common.Constants
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DriveCompletionEventService : DriveEventService() {

    private lateinit var mExecutorService: ExecutorService

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() - Starting ExecutorService...")
        mExecutorService = Executors.newSingleThreadExecutor()
    }

    @Synchronized
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() - Shutting down ExecutorService...")
        mExecutorService.shutdown()
    }

    override fun onCompletion(event: CompletionEvent) {
        Log.d(TAG, "onCompletion() - received event: ${event.toString()}")

        when {
            event.status == CompletionEvent.STATUS_FAILURE -> {
                Log.d(TAG, "onCompletion() - Detected STATUS_FAILURE.")
                // Handle completion failure.

                // CompletionEvent is only dismissed here, in a real world application failure should
                // be handled before the event is dismissed.
                event.dismiss()
            }
            event.status == CompletionEvent.STATUS_SUCCESS -> {
                Log.d(TAG, "onCompletion() - Detected STATUS_SUCCESS.")

                // Broadcast when completion is success. It is mostly like to happen when we create a file or update a file to the drive from this app.
                this.also {
                    Intent(Constants.FILE_CREATED_SUCCESSFULLY).apply {
                        putExtra(Constants.EVENT_DRIVE_ID, event.driveId)
                        LocalBroadcastManager.getInstance(it).sendBroadcast(this)
                    }
                }
                // Finally dismiss completed event. We no longer need it.
                event.dismiss()
            }
            event.status == CompletionEvent.STATUS_CANCELED -> {
                Log.d(TAG, "onCompletion() - Detected STATUS_CANCELED.")

                // Finally dismiss completed event. We no longer need it.
                event.dismiss()
            }
        }
    }

    companion object {
        private val TAG = DriveCompletionEventService::class.java.simpleName
    }
}