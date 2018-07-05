package com.motondon.driveapidemoapp3.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.drive.DriveId
import com.google.android.gms.drive.OpenFileActivityOptions
import com.google.android.gms.drive.events.ListenerToken
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.SearchableField
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.activity.DriveFolderMonitorActivity
import kotlinx.android.synthetic.main.fragment_drive_folder_monitor.*

class DriveFolderMonitorFragment : BaseFragment() {

    // Identifies our change listener so it can be unsubscribed when no longer needed.
    private var mChangeListenerToken: ListenerToken? = null

    private var mMonitoring: Boolean = false
    private var driveClientReady = false
    private var ready = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView()")
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_drive_folder_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated()")

        super.onViewCreated(view, savedInstanceState)

        btnChooseFolder.setOnClickListener({selectDriveFolder()})
        btnStopMonitoring.setOnClickListener({stopMonitoring(false)})

        if (driveClientReady) {
            updateViews(true)
        } else {
            updateViews(false)
        }

        ready = true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()

        // When leaving it, just stop monitoring
        stopMonitoring(true)
    }

    /**
     * This event is received right after user has signed in and the Drive client has been initialized.
     *
     */
    override fun onDriveClientReady() {
        Log.d(TAG, "onDriveClientReady()")
        driveClientReady = true

        if (ready) updateViews(true)
    }

    private fun updateViews(state: Boolean) {
        btnChooseFolder.isEnabled = state
        btnStopMonitoring.isEnabled = state
    }

    /**
     * Request user to select a Drive folder in order to monitor it
     *
     */
    private fun selectDriveFolder() {
        Log.d(TAG, "selectDriveFolder()")

        val openOptions = OpenFileActivityOptions.Builder()
            //.setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.document"))
            .setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, "text/plain"))
            //.setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, DriveFolder.MIME_TYPE))
            .setActivityTitle("Select a file/folder")
            .build()

        getDriveClient()
            .newOpenFileActivityIntentSender(openOptions)
            .continueWith({ task ->
                // Start intent
                startIntentSenderForResult(
                    task.result, REQUEST_CODE_LIST_FILES_IN_FOLDER, null, 0, 0, 0, null)
            })
    }

    /**
     * Handles resolution callbacks.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "in onActivityResult() - triggered on pressing Select. requestCode: $requestCode - resultCode: $resultCode - data(intent): $data")

        if (data == null) {
            return
        }

        when (requestCode) {

            REQUEST_CODE_LIST_FILES_IN_FOLDER -> if (resultCode == Activity.RESULT_OK) {
                val fileId = data.getParcelableExtra(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID) as DriveId
                var fileName = ""

                // Just request metadata so we can get the actual file/folder name which we want to monitor
                getDriveResourceClient().getMetadata(fileId.asDriveResource())
                    .addOnSuccessListener { metadata ->
                        fileName = metadata.title
                        Log.i(TAG, "onActivityResult() - File/Folder: $fileName")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Unable to retrieve metadata", e)
                        showMessage("Unable to retrieve metadata")
                    }

                Log.d(TAG, "Starting to listen file/folder [$fileName] for changes.")

                getDriveResourceClient()
                    .addChangeListener(fileId.asDriveResource(), { changeEvent ->

                        // Once an event is received, log it and add it to the appropriate view.
                        Log.i(TAG, "onActivityResult() - File/Folder change event: $changeEvent")

                        when {
                            changeEvent.hasBeenDeleted() -> {
                                Log.i(TAG, "onActivityResult() - File/Folder has been deleted")
                                tvFileEvents.append("\nFile/Folder has been deleted\n")
                            }
                            changeEvent.hasContentChanged() -> {
                                Log.i(TAG, "onActivityResult() - File/Folder content has changed")
                                tvFileEvents.append("\nFile/Folder content has changed\n")
                            }
                            changeEvent.hasMetadataChanged() -> {
                                Log.i(TAG, "onActivityResult() - File/Folder metadata has changed")
                                tvFileEvents.append("\nFile/Folder metadata has changed\n")
                            }
                        }
                    })
                    .addOnSuccessListener { listenerToken ->
                        Log.i(TAG, "onActivityResult() - Started listen file/folder")

                        mChangeListenerToken = listenerToken

                        // Update mCurrentMonitoredFolder view with the current folder being monitored.
                        tvCurrentMonitoredFolder.text = "Monitoring file/folder: $fileName"
                        mMonitoring = true
                    }
                    .addOnFailureListener { e ->
                        showMessage("onActivityResult() - Could not monitor file/folder: $fileName. Error message: ${e.message}")
                        tvCurrentMonitoredFolder.text = "Monitoring file/folder: --"
                        mMonitoring = false
                    }

                Log.i(TAG, "onActivityResult() - Now listen for changes on file/folder: $fileName")
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun stopMonitoring(isDestroyingFragment: Boolean) {
        Log.d(TAG, "stopMonitoring()")

        if (!mMonitoring) return

        mChangeListenerToken?.let {
            getDriveResourceClient().removeChangeListener(it)
                .addOnSuccessListener { _ ->

                    // If this method is being called in onDestroy, do not touch in any UI element, since addOnSuccessListener may
                    // be called after onDestroy completed. If we try to do it, we may end up in a NPE!
                    if (!isDestroyingFragment) {
                        showMessage("Stop monitoring folder")
                        tvCurrentMonitoredFolder.text = "Monitoring file/folder: --"
                        tvFileEvents.text = ""
                    }
                    mMonitoring = false

                }
                .addOnFailureListener { _ ->
                    if (isDestroyingFragment) showMessage("Failure while trying to stop monitoring folder")
                }

        Log.d(TAG, "Stop monitoring folder")
        }
    }

    companion object {
        val TAG = DriveFolderMonitorActivity::class.java.simpleName
        private const val REQUEST_CODE_LIST_FILES_IN_FOLDER = 107
    }
}
