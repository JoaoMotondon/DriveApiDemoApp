package com.motondon.driveapidemoapp3.controller

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.motondon.driveapidemoapp3.common.Constants
import com.motondon.driveapidemoapp3.conflict.TaskConflictUtil
import android.support.v4.content.LocalBroadcastManager
import com.motondon.driveapidemoapp3.fragments.ConflictResolutionFragment

import java.io.OutputStreamWriter

/**
 * This class is intended to hold some method that manipulates Drive [mTaskListDriveFileName] file
 *
 */
class TaskListDriveController(
        private val mContext: Context,
        private val mFragment: ConflictResolutionFragment,
        private val mDriveClient: DriveClient,
        private val mDriveResourceClient: DriveResourceClient) {

    private val mTaskListDriveFileName = "driveApiDemoAppTaskList"

    // Instance variables used for DriveFile and DriveContents to help initiate file conflicts.
    private lateinit var taskListDriveFile: DriveFile
    private var taskListContents: DriveContents? = null

    /**
     * Called when the ConflictResolutionFragment is opened. It will request a TaskModel list from the Drive. Once it receives it, it
     * reads its content and send a broadcast in order to update the UI.
     */
    fun requestData() {
        Log.d(TAG, "requestData() - calling DriveApi::requestData() method...")

        mDriveClient
            .requestSync()
            .addOnFailureListener({ e ->
                Log.e(TAG, "requestData::addOnFailureListener() - Unexpected error", e)
                val error = e.message?.let { it } ?: ""

                if (error.contains("limit exceeded")) { // Sync request rate limit exceeded.
                    Log.w(TAG, "requestData() - Received: 'Sync request rate limit exceeded' message. You should wait a few seconds before calling DriveClient::requestSync() method again...")
                    showMessage("Received: 'Sync request rate limit exceeded' message. You should wait a few seconds before opening this activity again")
                } else {
                    Log.e(TAG, "requestData() - Unable to sync error: $error.")
                    showMessage("Unable to sync. Check logs for details")
                }
                return@addOnFailureListener
            })
            .continueWithTask({ _ ->
                // syncRequest returned successfully. Performing a query now will return fresh results, so, let's do it.
                fetchTaskListFileFromDrive()
            })
    }

    /**
     * Retrieves the TaskModel list from Drive if it exists. If not, create a new list.
     */
    private fun fetchTaskListFileFromDrive(): Task<Void> {
        Log.i(TAG, "fetchTaskListFileFromDrive() - Querying task list file from Drive: $mTaskListDriveFileName")

        // Create a query in order to fetch a fresh TaskModel list from the Drive.
        val query = Query.Builder()
            .addFilter(Filters.eq(SearchableField.TITLE, mTaskListDriveFileName))
            .build()

        return mDriveResourceClient
            .query(query)
            .continueWithTask<DriveFile>({ task ->
                val metadataBuffer = task.result
                try {
                    // If metadataBuffer is empty, it means Drive did not find a [mTaskListDriveFileName] file in it. So, request it to create a new one.
                    if (metadataBuffer.count == 0) {
                        Log.d(TAG, "fetchTaskListFileFromDrive() - File [$mTaskListDriveFileName] not found in the Drive. Trying to create a new one...")
                        return@continueWithTask createNewTaskListFile()

                    } else {

                        // This block means Drive found a [mTaskListDriveFileName] file in it. Now, first check whether it was move to trash, and
                        // if so, request Drive to create a new one.
                        val metadata = metadataBuffer.get(0)
                        if (metadataBuffer != null && metadata.isTrashed) {
                            Log.w(TAG, "fetchTaskListFileFromDrive() - Detected [$mTaskListDriveFileName] file was move to trash. Just return...")
                            showMessage("Problem while retrieving [$mTaskListDriveFileName] metadata.")
                            return@continueWithTask createNewTaskListFile()
                        }

                        // If the file exists then use it.
                        Log.d(TAG, "fetchTaskListFileFromDrive() - Detected [$mTaskListDriveFileName] file contains any data. Opening it....")
                        val id = metadataBuffer.get(0).driveId
                        return@continueWithTask Tasks.forResult<DriveFile>(id.asDriveFile())
                    }
                } finally {
                    metadataBuffer.release()
                }
            })
            .continueWithTask{ driveId ->
                loadTaskListFile(driveId.result)
            }
    }

    /**
     * Request Drive to create a new [mTaskListDriveFileName] file
     *
     */
    private fun createNewTaskListFile(): Task<DriveFile> {
        Log.d(TAG, "createNewTaskListFile() - Trying to create task list file: $mTaskListDriveFileName on the Drive...")

        // If the file does not exist then create one.
        val rootFolderTask = mDriveResourceClient.rootFolder
        val createContentsTask = mDriveResourceClient.createContents()

        return Tasks.whenAll(rootFolderTask, createContentsTask)
            .continueWithTask<DriveFile> { _ ->
                val parent = rootFolderTask.result
                val contents = createContentsTask.result

                val changeSet = MetadataChangeSet.Builder()
                    .setTitle(mTaskListDriveFileName)
                    .setMimeType("text/plain")
                    .build()

                // Add Completion notification so we can be notified when this file is created on the server. When it happens, DriveCompletionEventService will fire an
                // event. See link below for details:
                // - https://stackoverflow.com/questions/22874657/unpredictable-result-of-driveid-getresourceid-in-google-drive-android-api/31553269#31553269
                // Note we are assuming our file is always created accordingly. In a real application we should rely only when receiving a completion event
                val executionOptions = ExecutionOptions.Builder()
                    .setNotifyOnCompletion(true)
                    .build()

                // And finally create a file in Drive root folder
                return@continueWithTask mDriveResourceClient.createFile(parent, changeSet, contents, executionOptions)
                    .addOnSuccessListener { _ ->
                        Log.d(TAG, "createNewTaskListFile() - Task list file: $mTaskListDriveFileName successfully created on Drive...")
                    }
            }
    }

    /**
     * Load the content of [mTaskListDriveFileName] file and deserialize it in a list of TaskModel
     *
     */
    private fun loadTaskListFile(driveFile: DriveFile) : Task<Void> {
        Log.d(TAG, "loadTaskListFile() - Loading content file: $mTaskListDriveFileName...")

        taskListDriveFile = driveFile

        val loadTask = mDriveResourceClient.openFile(taskListDriveFile, DriveFile.MODE_READ_ONLY) as Task<DriveContents>

        return loadTask.continueWith ({ task ->
            Log.d(TAG, "loadTaskListFile - Remote task list file $mTaskListDriveFileName  was successfully opened. Loading its content...")

            taskListContents = task.result

            val inputStream = taskListContents?.inputStream
            val taskModelList = TaskConflictUtil.getTaskListFromInputStream(inputStream)

            // Notify the UI that the list should be updated
            Log.d(TAG, "loadTaskListFile - File content loaded. Adding TaskModel items to the UI..")
            Intent(Constants.TASK_LIST_LOADED).apply {
                putExtra(Constants.TASK_LIST, taskModelList)
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(this)
            }
            null
        })
    }

    /**
     * Called when user requests a sync process. It will send the local TaskModel list to the Drive. If a conflict is detected, Drive
     * will send later a CompletionEvent.STATUS_CONFLICT event allowing us to resolve locally and then send back the resolution
     *
     */
    fun requestSync() : Task<Void> {
        Log.d(TAG, "requestSync()")

        val reopenTask = mDriveResourceClient.reopenContentsForWrite(taskListContents!!)

        return reopenTask
            .continueWithTask({ task ->

                val driveContents = task.result
                val outputStream = driveContents.outputStream
                OutputStreamWriter(outputStream).use { writer -> writer.write(TaskConflictUtil.formatStringFromTaskList(mFragment.getTasks())) }

                // ExecutionOptions define the conflict strategy to be used.
                // From the official docs: "A conflict resolution strategy that keeps the remote version of the file instead of overwriting it
                // with the locally committed changes when a conflict is detected." ... "...in case of conflict the remote state of the file will
                // be preserved but through the CompletionEvent, the client will be able to handle the conflict, access the locally committed
                // changes that conflicted (and couldn't be persisted on the server), and perform the desired conflict resolution with all the data."
                //
                // So, basically we are telling the Drive to keep remote version, and in case of a conflict, we will receive the conflict event through
                // the class MyDriveEventsService which will pass the control to the ConflictResolver class. There we can handle the conflict according
                // to our needs
                val executionOptions = ExecutionOptions.Builder()
                    .setNotifyOnCompletion(true)
                    .setConflictStrategy(
                            ExecutionOptions.CONFLICT_STRATEGY_KEEP_REMOTE)
                    .build()
                return@continueWithTask mDriveResourceClient.commitContents(
                        driveContents, null, executionOptions)

            })
            .continueWithTask({ _ ->
                showMessage("File saved on Drive. In case of conflict, it will be resolved soon")
                Log.d(TAG, "requestSync() - Task list file saved successfully on the Drive.")
                return@continueWithTask loadTaskListFile(taskListDriveFile)
            })
            .addOnCompleteListener({
                Log.d(TAG, "requestSync() - Finished requestSync() process successfully")
            })
            .addOnFailureListener { e ->
                Log.e(TAG, "Unexpected error", e)
                showMessage("Unexpected error")
            }
    }

    private fun showMessage(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val TAG = TaskListDriveController::class.java.simpleName
    }
}
