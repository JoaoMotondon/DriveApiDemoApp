package com.motondon.driveapidemoapp3.conflict

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

import com.google.android.gms.drive.*
import com.google.android.gms.drive.events.CompletionEvent
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.motondon.driveapidemoapp3.model.TaskModel

import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService
import com.google.android.gms.drive.ExecutionOptions
import com.google.android.gms.drive.DriveContents
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.Drive

/**
 * ConflictResolver handles a CompletionEvent with a conflict status.
 *
 * It will be called by the MyDriveEventsService::onCompletion() callback when Drive detects a conflict during a sync process.
 *
 */
class ConflictResolver(private val mConflictedCompletionEvent: CompletionEvent, private val mContext: Context, private val mExecutorService: ExecutorService) {

    private var mDriveResourceClient: DriveResourceClient? = null
    private var mDriveContents: DriveContents? = null

    /**
     * Execute the resolution process.
     *
     * It first signsIn to the Google Drive and extracts from the mConflictedCompletionEvent both local and modified TaskModel lists.
     *
     * Then it still uses mConflictedCompletionEvent object to open and get server TaskModel list, there is the Drive version of this list.
     *
     * With all these three lists, it calls TaskConflictUtil.resolveConflict(...) that handles all the conflicts and returns another list with
     * the resolution (resolvedContent list).
     *
     * Last, it calls DriveResourceClient::commitContents() in order to commit resolvedContent list in the Drive.
     *
     */
    fun resolve() {
        Log.d(TAG, "resolve() - Begin")

        var localBaseTaskList: ArrayList<TaskModel> = arrayListOf()
        var localModifiedTaskList: ArrayList<TaskModel> = arrayListOf()
        var serverTaskList: ArrayList<TaskModel> = arrayListOf()
        var resolvedContent: ArrayList<TaskModel> = arrayListOf()

        val signInOptionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Drive.SCOPE_FILE)
            .requestScopes(Drive.SCOPE_APPFOLDER)
        if (mConflictedCompletionEvent.accountName != null) {
            signInOptionsBuilder.setAccountName(mConflictedCompletionEvent.accountName)
            Log.d(TAG, "resolve() - SetAccountName")
        }

        val signInClient = GoogleSignIn.getClient(mContext, signInOptionsBuilder.build())
        signInClient.silentSignIn()
            .continueWith(mExecutorService,
                Continuation<GoogleSignInAccount, Void> { signInTask ->
                    Log.d(TAG, "resolve() - Getting base and modified task list from ConflictedCompletion event...")

                    mDriveResourceClient = Drive.getDriveResourceClient(
                        mContext, signInTask.result)
                    localBaseTaskList = TaskConflictUtil.getTaskListFromInputStream(
                        mConflictedCompletionEvent.baseContentsInputStream)
                    localModifiedTaskList = TaskConflictUtil.getTaskListFromInputStream(
                        mConflictedCompletionEvent.modifiedContentsInputStream)
                    null
                })
            .continueWithTask(mExecutorService,
                Continuation<Void, Task<DriveContents>> {
                    Log.d(TAG, "resolve() - Opening Drive file...")

                    val driveId = mConflictedCompletionEvent.driveId
                    mDriveResourceClient?.openFile(
                        driveId.asDriveFile(), DriveFile.MODE_READ_ONLY)

                })
            .continueWithTask(mExecutorService,
                Continuation<DriveContents, Task<DriveContents>> { task ->
                    Log.d(TAG, "resolve() - Reopening Drive content file for write...")

                    mDriveContents = task.result
                    val serverInputStream = task.result.inputStream
                    serverTaskList = TaskConflictUtil.getTaskListFromInputStream(serverInputStream)
                    return@Continuation mDriveResourceClient?.reopenContentsForWrite(mDriveContents!!)
                })
            .continueWithTask(mExecutorService,
                Continuation<DriveContents, Task<Void>> { task ->
                    val contentsForWrite = task.result

                    Log.d(TAG, "resolve() - Local base task list contains ${localBaseTaskList.size} task(s).")
                    Log.d(TAG, "resolve() - Local modified task list contains ${localModifiedTaskList.size} task(s).")
                    Log.d(TAG, "resolve() - Server task list contains ${serverTaskList.size} task(s).")

                    Log.d(TAG, "resolve() - Calling TaskConflictUtil.resolveConflict() in order to resolve any conflict...")

                    resolvedContent = TaskConflictUtil.resolveConflict(
                        localBaseTaskList, localModifiedTaskList, serverTaskList)

                    Log.d(TAG, "resolve() - TaskConflictUtil.resolveConflict() returned. Preparing to write the resolved list to the Drive...")

                    val outputStream = contentsForWrite.outputStream
                    OutputStreamWriter(outputStream).use { writer -> writer.write(TaskConflictUtil.formatStringFromTaskList(resolvedContent)) }

                    // It is not likely that resolving a conflict will result in another
                    // conflict, but it can happen if the file changed again while this
                    // conflict was resolved. Since we already implemented conflict
                    // resolution and we never want to miss user data, we commit here
                    // with execution options in conflict-aware mode (otherwise we would
                    // overwrite server content).
                    val executionOptions = ExecutionOptions.Builder()
                        .setNotifyOnCompletion(true)
                        .setConflictStrategy(
                                ExecutionOptions
                                        .CONFLICT_STRATEGY_KEEP_REMOTE)
                        .build()

                    // Commit resolved contents. Note commitContents returns success when it writes the list content locally. Later, when
                    // the data is in fact stored in the Drive, a Completion event is fired (which is handled in the MyDriveEventsService)
                    Log.d(TAG, "resolve() - Committing resolved contents...")
                    val modifiedMetadataChangeSet = mConflictedCompletionEvent.modifiedMetadataChangeSet
                    return@Continuation mDriveResourceClient?.commitContents(contentsForWrite,
                            modifiedMetadataChangeSet, executionOptions)
                })
            .addOnSuccessListener({ _: Void? ->
                Log.d(TAG, "resolve() - Sync process finished successfully")
                mConflictedCompletionEvent.dismiss()
                // Notify the UI that the list should be updated
                sendResult(resolvedContent)
            })
            .addOnFailureListener({ e: Exception ->
                // The contents cannot be reopened at this point, probably due to
                // connectivity, so by snoozing the event we will get it again later.
                Log.d(TAG, "resolve() - Unable to write resolved content, snoozing completion event.", e)
                mConflictedCompletionEvent.snooze()

                mDriveContents?.let {
                    mDriveResourceClient?.discardContents(it)
                }
            })
    }

    /**
     * Notify the UI that the list should be updated.
     *
     * @param resolution Resolved TaskModel list.
     */
    private fun sendResult(resolution: ArrayList<TaskModel>) {
        Log.d(TAG, "sendResult()")

        Intent(CONFLICT_RESOLVED).apply {
            putExtra(CONFLICT_RESOLVED_DATA, resolution)
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(this)
        }
    }

    companion object {
        private val TAG = ConflictResolver::class.java.simpleName
        const val CONFLICT_RESOLVED = "CONFLICT_RESOLVED"
        const val CONFLICT_RESOLVED_DATA = "CONFLICT_RESOLVED_DATA"
    }
}
